/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.easy4j.codex.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * End-to-end tests running the real JDK WebSocket client against an
 * in-process fake Codex app-server, covering the full integration contract:
 * bearer handshake, thread/start &rarr; turn/start sequence, notification
 * consumption, session-key resume mapping and unknown-notification tolerance.
 *
 * @since 3.0.0
 */
class CodexAppServerClientE2ETest {

    private final JsonMapper mapper = new JsonMapper();

    private CodexAppServerConfig configFor(FakeCodexAppServer server) {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setBaseUrl(server.baseUrl());
        config.setToken("test-token");
        config.setConnectTimeoutMillis(2_000);
        config.setReadTimeoutMillis(10_000);
        return config;
    }

    @Test
    void shouldRunFullTurnWithDeltasAndHandshakeAuth() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {
            List<String> deltas = new ArrayList<>();

            AppServerTurnResult result = client.runTurn(AppServerTurnRequest.builder()
                    .prompt("第一轮")
                    .sessionKey("chat-42")
                    .onDelta(deltas::add)
                    .build());

            assertEquals("th_e2e", result.getThreadId());
            assertEquals("你好世界", result.getContent());
            assertEquals("stop", result.getFinishReason());
            assertEquals(List.of("你好", "世界"), deltas);
            assertTrue(server.authorizationSeen(), "WebSocket handshake must carry the bearer token");
            // 真实协议（codex ≥0.14x）：turn 前必须先 initialize 握手
            assertEquals("initialize", methodOf(server.receivedFrames().get(0)));
            assertTrue(methodsOf(server).contains("thread/start"),
                    "turn must issue thread/start after the initialize handshake");
        }
    }


    @Test
    void shouldSerializeConcurrentTurnsForSameSessionKey() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {
            server.holdTurnCompletions();

            java.util.concurrent.CompletableFuture<AppServerTurnResult> first =
                    client.runTurnAsync(AppServerTurnRequest.builder()
                            .prompt("first")
                            .sessionKey("chat-serial")
                            .build());
            assertTrue(server.awaitTurnStarts(1, 2_000), "first turn must reach server");

            java.util.concurrent.CompletableFuture<AppServerTurnResult> second =
                    client.runTurnAsync(AppServerTurnRequest.builder()
                            .prompt("second")
                            .sessionKey("chat-serial")
                            .build());

            assertFalse(server.awaitTurnStarts(2, 750),
                    "second same-session turn must not start while first is active");

            server.releaseTurnCompletions();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(2, server.turnStartCount());
        }
    }

    @Test
    void shouldAllowConcurrentTurnsForDifferentSessionKeys() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {
            server.holdTurnCompletions();

            java.util.concurrent.CompletableFuture<AppServerTurnResult> first =
                    client.runTurnAsync(AppServerTurnRequest.builder()
                            .prompt("first")
                            .sessionKey("chat-a")
                            .build());
            java.util.concurrent.CompletableFuture<AppServerTurnResult> second =
                    client.runTurnAsync(AppServerTurnRequest.builder()
                            .prompt("second")
                            .sessionKey("chat-b")
                            .build());

            assertTrue(server.awaitTurnStarts(2, 2_000),
                    "different sessions must be able to run concurrently");

            server.releaseTurnCompletions();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldResumeSameThreadForRepeatedSessionKey() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {

            client.runTurn(AppServerTurnRequest.builder().prompt("第一轮").sessionKey("chat-42").build());
            AppServerTurnResult resumed = client.runTurn(
                    AppServerTurnRequest.builder().prompt("第二轮").sessionKey("chat-42").build());

            assertEquals("th_e2e", resumed.getThreadId());
            assertEquals("你好世界", resumed.getContent());
            List<String> methods = server.receivedFrames().stream()
                    .map(this::methodOf)
                    .toList();
            assertTrue(methods.contains("thread/resume"), "second turn must resume the mapped thread");
            String resumeFrame = server.receivedFrames().stream()
                    .filter(frame -> "thread/resume".equals(methodOf(frame)))
                    .findFirst()
                    .orElseThrow();
            assertEquals("th_e2e", mapper.readTree(resumeFrame).path("params").path("threadId").asText());
        }
    }

    @Test
    void shouldStartFreshThreadWhenSessionKeyDiffers() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {

            client.runTurn(AppServerTurnRequest.builder().prompt("第一轮").sessionKey("chat-42").build());
            client.runTurn(AppServerTurnRequest.builder().prompt("复用同会话").sessionKey("chat-42").build());
            client.runTurn(AppServerTurnRequest.builder().prompt("另一会话").sessionKey("chat-99").build());

            List<String> methods = server.receivedFrames().stream()
                    .map(this::methodOf)
                    .toList();
            assertTrue(methods.contains("thread/resume"));
            assertEquals(1, methods.stream().filter("thread/resume"::equals).count(),
                    "only the repeated session key resumes; a different key starts a fresh thread");
        }
    }

    @Test
    void shouldExposeTurnIdAndOnTurnStarted() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {
            List<String> startedTurns = new ArrayList<>();

            AppServerTurnResult result = client.runTurn(AppServerTurnRequest.builder()
                    .prompt("带转向回调")
                    .onTurnStarted(startedTurns::add)
                    .build());

            assertEquals("turn_1", result.getTurnId());
            assertEquals(List.of("turn_1"), startedTurns);
        }
    }

    @Test
    void shouldSupportThreadLifecycleAndTurnControl() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {

            List<AppServerThread> threads = client.listThreads(2);
            assertEquals(2, threads.size());
            assertEquals("th_a", threads.get(0).getId());
            assertEquals("/tmp/a", threads.get(0).getCwd());
            assertFalse(threads.get(0).isArchived());
            assertTrue(threads.get(1).isArchived());

            AppServerThread read = client.readThread("th_e2e");
            assertEquals("th_e2e", read.getId());
            assertEquals("E2E", read.getName());

            AppServerThread forked = client.forkThread("th_e2e");
            assertEquals("th_fork", forked.getId());

            client.archiveThread("th_fork");
            client.unarchiveThread("th_fork");
            client.deleteThread("th_fork");
            client.interruptTurn("th_e2e", "turn_1");
            client.steerTurn("th_e2e", "turn_1", "补充指令");

            String raw = client.execRpc("thread/read",
                    java.util.Map.of("threadId", "th_e2e"));
            assertTrue(raw.contains("th_e2e"));
        }
    }

    @Test
    void shouldSendInitializeHandshake() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {

            client.listThreads(2);

            List<String> methods = server.receivedFrames().stream()
                    .map(this::methodOf)
                    .toList();
            assertTrue(methods.size() >= 3, "initialize handshake and business request must all be present");
            assertEquals("initialize", methods.get(0));
            assertEquals("notifications/initialized", methods.get(1),
                    "initialize response must be acknowledged with the official notification name");
            assertEquals("thread/list", methods.get(2),
                    "business RPC must follow the initialized acknowledgement");
        }
    }

    @Test
    void shouldCompleteTurnWithoutSessionKey() throws Exception {
        try (FakeCodexAppServer server = new FakeCodexAppServer();
                CodexAppServerClient client = new CodexAppServerClient(configFor(server))) {

            AppServerTurnResult result = client.runTurn(
                    AppServerTurnRequest.builder().prompt("一次性任务").build());

            assertEquals("你好世界", result.getContent());
            assertEquals(0, methodsOf(server).stream().filter("thread/resume"::equals).count());
        }
    }

    private String methodOf(String frame) {
        try {
            JsonNode node = mapper.readTree(frame);
            return node.path("method").asText("");
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid JSON-RPC frame: " + frame, ex);
        }
    }

    private List<String> methodsOf(FakeCodexAppServer server) {
        return server.receivedFrames().stream().map(this::methodOf).toList();
    }
}
