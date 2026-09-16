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
            assertEquals("thread/start", methodOf(server.receivedFrames().get(0)));
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
