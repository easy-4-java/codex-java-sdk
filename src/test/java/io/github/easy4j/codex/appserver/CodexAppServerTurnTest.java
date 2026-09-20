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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Contract tests for {@link CodexAppServerTurn} driven without a socket:
 * outgoing RPC payloads are inspected through {@code sentMessages} and
 * inbound frames are fed straight into {@code handleFrame}, mirroring the
 * integration contract of the app-server protocol.
 *
 * <p>Real wire shape (verified against codex 0.154.0): {@code initialize} is
 * mandatory before any thread RPC ({@code -32600 Not initialized} otherwise),
 * and the thread id is nested at {@code result.thread.id}.</p>
 *
 * @since 3.0.0
 */
class CodexAppServerTurnTest {

    private final ObjectMapper mapper = new JsonMapper();

    private CodexAppServerTurn newTurn(AppServerTurnRequest request, ThreadMappingCache cache) {
        return new CodexAppServerTurn(request, new CodexAppServerConfig(), mapper, cache, null);
    }

    private JsonNode lastFrame(List<String> sent) {
        try {
            return mapper.readTree(sent.get(sent.size() - 1));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid captured frame", ex);
        }
    }

    private JsonNode frameAt(List<String> sent, int index) {
        try {
            return mapper.readTree(sent.get(index));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid captured frame", ex);
        }
    }

    private String methodOf(String frame) {
        try {
            return mapper.readTree(frame).path("method").asText("");
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid frame", ex);
        }
    }

    private WebSocket recordingSocket(List<String> writes,
                                      Queue<CompletableFuture<WebSocket>> completions) {
        return (WebSocket) Proxy.newProxyInstance(
                WebSocket.class.getClassLoader(),
                new Class<?>[]{WebSocket.class},
                (proxy, method, args) -> {
                    if ("sendText".equals(method.getName())) {
                        writes.add((String) args[0]);
                        CompletableFuture<WebSocket> completion = completions.poll();
                        return completion == null
                                ? CompletableFuture.completedFuture((WebSocket) proxy)
                                : completion;
                    }
                    if ("sendClose".equals(method.getName())
                            || "sendPing".equals(method.getName())
                            || "sendPong".equals(method.getName())
                            || "sendBinary".equals(method.getName())) {
                        return CompletableFuture.completedFuture((WebSocket) proxy);
                    }
                    if ("getSubprotocol".equals(method.getName())) {
                        return "";
                    }
                    if ("isInputClosed".equals(method.getName())
                            || "isOutputClosed".equals(method.getName())) {
                        return false;
                    }
                    return null;
                });
    }

    /**
     * Drives {@code begin()} through the mandatory initialize handshake:
     * initialize (id=1) → notifications/initialized → thread/start|resume (id=2).
     */
    private void handshake(CodexAppServerTurn turn) {
        turn.begin();
        turn.handleFrame(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"serverInfo\":{\"name\":\"codex\"}}}");
    }

    @Test
    void shouldWaitForInitializedSendBeforeThreadStart() {
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        List<String> socketWrites = new ArrayList<>();
        Queue<CompletableFuture<WebSocket>> completions = new ArrayDeque<>();
        CompletableFuture<WebSocket> initializeWrite = new CompletableFuture<>();
        CompletableFuture<WebSocket> initializedWrite = new CompletableFuture<>();
        CompletableFuture<WebSocket> threadStartWrite = new CompletableFuture<>();
        completions.add(initializeWrite);
        completions.add(initializedWrite);
        completions.add(threadStartWrite);
        WebSocket socket = recordingSocket(socketWrites, completions);
        initializeWrite.complete(socket);

        turn.onOpen(socket);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");

        assertEquals(2, socketWrites.size(),
                "thread/start must wait until notifications/initialized finishes sending");
        assertEquals(CodexAppServerProtocol.INITIALIZED, methodOf(socketWrites.get(1)));

        initializedWrite.complete(socket);
        assertEquals(3, socketWrites.size());
        assertEquals(CodexAppServerProtocol.THREAD_START, methodOf(socketWrites.get(2)));
        threadStartWrite.complete(socket);
    }

    @Test
    void shouldFailImmediatelyWhenWebSocketSendFails() {
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        List<String> socketWrites = new ArrayList<>();
        Queue<CompletableFuture<WebSocket>> completions = new ArrayDeque<>();
        CompletableFuture<WebSocket> initializeWrite = new CompletableFuture<>();
        completions.add(initializeWrite);
        WebSocket socket = recordingSocket(socketWrites, completions);

        turn.onOpen(socket);
        initializeWrite.completeExceptionally(new RuntimeException("write failed"));

        assertTrue(turn.future().isCompletedExceptionally(),
                "write failure must fail the turn immediately instead of waiting for read timeout");
    }

    @Test
    void shouldMapWebSocketUrls() {
        assertEquals("ws://host:8081", CodexAppServerTurn.toWebSocketUrl("ws://host:8081"));
        assertEquals("wss://host:8081", CodexAppServerTurn.toWebSocketUrl("wss://host:8081"));
        assertEquals("ws://host:8081", CodexAppServerTurn.toWebSocketUrl("http://host:8081"));
        assertEquals("wss://host:8081", CodexAppServerTurn.toWebSocketUrl("https://host:8081"));
        assertEquals("ws://host:8081", CodexAppServerTurn.toWebSocketUrl("http://host:8081/"));
        assertEquals("ws://host:8081", CodexAppServerTurn.toWebSocketUrl("  http://host:8081/// "));
        assertThrows(IllegalArgumentException.class, () -> CodexAppServerTurn.toWebSocketUrl(null));
        assertThrows(IllegalArgumentException.class, () -> CodexAppServerTurn.toWebSocketUrl("  "));
        assertThrows(IllegalArgumentException.class, () -> CodexAppServerTurn.toWebSocketUrl("ftp://host:8081"));
    }

    @Test
    void shouldInitializeBeforeThreadStartWhenNoSessionKey() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);

        // 倒数第二帧应为 initialize，最后一帧为 thread/start（id=2）
        JsonNode initializeFrame = frameAt(turn.sentMessages(), 0);
        assertEquals("initialize", initializeFrame.path("method").asText());
        assertEquals("easy4j-codex-java-sdk",
                initializeFrame.path("params").path("clientInfo").path("name").asText());
        JsonNode initializedFrame = frameAt(turn.sentMessages(), 1);
        assertEquals("notifications/initialized", initializedFrame.path("method").asText());
        JsonNode frame = lastFrame(turn.sentMessages());
        assertEquals(2L, frame.path("id").asLong());
        assertEquals("thread/start", frame.path("method").asText());
        assertTrue(frame.path("params").isEmpty());
    }

    @Test
    void shouldResumeThreadWhenSessionKeyMapped() {
        ThreadMappingCache cache = new ThreadMappingCache(10);
        cache.put("chat-1", "th_cached");
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").sessionKey(" chat-1 ").build(), cache);

        handshake(turn);

        JsonNode frame = lastFrame(turn.sentMessages());
        assertEquals("thread/resume", frame.path("method").asText());
        assertEquals("th_cached", frame.path("params").path("threadId").asText());
    }

    @Test
    void shouldBuildTurnStartWithTextInputItem() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("Fix it").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_9\"}}}");

        JsonNode frame = lastFrame(turn.sentMessages());
        assertEquals("turn/start", frame.path("method").asText());
        assertEquals("th_9", frame.path("params").path("threadId").asText());
        JsonNode input = frame.path("params").path("input").get(0);
        assertEquals("text", input.path("type").asText());
        assertEquals("Fix it", input.path("text").asText());
    }

    @Test
    void shouldExtractNestedThreadIdFromRealWireShape() {
        // 实测 codex 0.154.0：thread id 嵌套在 result.thread.id
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":"
                + "{\"id\":\"01a0af32-c02f-7811-ae30-e154d84cf31a\",\"sessionId\":\"01a0af32\"}}}");

        JsonNode frame = lastFrame(turn.sentMessages());
        assertEquals("turn/start", frame.path("method").asText());
        assertEquals("01a0af32-c02f-7811-ae30-e154d84cf31a",
                frame.path("params").path("threadId").asText());
    }

    @Test
    void shouldAcceptLegacyTopLevelThreadId() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"threadId\":\"th_legacy\"}}");

        assertEquals("th_legacy", lastFrame(turn.sentMessages()).path("params").path("threadId").asText());
    }

    @Test
    void shouldFailWhenThreadStartResultHasNoThreadId() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}");

        assertTrue(turn.future().isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, () -> turn.future().join());
        assertInstanceOf(CodexAppServerException.class, ex.getCause());
    }

    @Test
    void shouldFailWhenInitializeRespondsWithError() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.begin();
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"boom\"}}");

        assertTrue(turn.future().isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, () -> turn.future().join());
        assertInstanceOf(CodexAppServerException.class, ex.getCause());
    }

    @Test
    void shouldFailWhenRpcRespondsWithError() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"error\":{\"code\":-1,\"message\":\"boom\"}}");

        assertTrue(turn.future().isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, () -> turn.future().join());
        assertInstanceOf(CodexAppServerException.class, ex.getCause());
    }

    @Test
    void shouldIgnoreUnknownRpcResponses() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":99,\"result\":{}}");

        assertFalse(turn.future().isDone());
    }



    @Test
    void shouldBridgeTurnAndDeltaEventsToListener() {
        List<String> events = new ArrayList<>();
        CodexAppServerListener listener = new CodexAppServerListener() {
            @Override
            public void onTurnStarted(String turnId) {
                events.add("turn:" + turnId);
            }

            @Override
            public void onTextDelta(String delta) {
                events.add("delta:" + delta);
            }

            @Override
            public void onItemCompleted(String itemType, String content) {
                events.add("item:" + itemType + ":" + content);
            }
        };
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").listener(listener).build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/started\",\"params\":{\"turnId\":\"turn_1\"}}");
        turn.handleFrame("{\"method\":\"item/agentMessage/delta\",\"params\":{\"itemId\":\"item_1\",\"delta\":\"你\"}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"id\":\"item_1\",\"type\":\"agentMessage\",\"text\":\"你\"}}}");

        assertEquals(List.of("turn:turn_1", "delta:你", "item:agentMessage:你"), events);
    }

    @Test
    void shouldStreamRealAgentMessageDeltas() {
        List<String> deltas = new ArrayList<>();
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").onDelta(deltas::add).build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"th_1\","
                + "\"turnId\":\"turn_1\",\"itemId\":\"item_1\",\"delta\":\"你\"}}");
        turn.handleFrame("{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"th_1\","
                + "\"turnId\":\"turn_1\",\"itemId\":\"item_1\",\"delta\":\"好\"}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertEquals(List.of("你", "好"), deltas);
        assertEquals("你好", turn.future().join().getContent());
    }

    @Test
    void shouldNotDuplicateCompletedMessageAfterStreamingDeltas() {
        List<String> deltas = new ArrayList<>();
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").onDelta(deltas::add).build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"th_1\","
                + "\"turnId\":\"turn_1\",\"itemId\":\"item_1\",\"delta\":\"你\"}}");
        turn.handleFrame("{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"th_1\","
                + "\"turnId\":\"turn_1\",\"itemId\":\"item_1\",\"delta\":\"好\"}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"id\":\"item_1\","
                + "\"type\":\"agentMessage\",\"text\":\"你好\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertEquals(List.of("你", "好"), deltas,
                "item/completed must not re-emit text that already arrived as real deltas");
        assertEquals("你好", turn.future().join().getContent(),
                "completed item must not duplicate already-streamed content");
    }

    @Test
    void shouldCollectAgentMessageItemsAndIgnoreOthers() {
        List<String> deltas = new ArrayList<>();
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").onDelta(deltas::add).build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"commandExecution\",\"text\":\"rm\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"agentMessage\",\"text\":\"你好\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"itemType\":\"agent_message\",\"content\":\"世界\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"agentMessage\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{\"message\":\"fallback\"}}");

        assertEquals(List.of("你好", "世界"), deltas);
        assertEquals("你好世界", turn.future().join().getContent());
        assertEquals("stop", turn.future().join().getFinishReason());
    }

    @Test
    void shouldFallBackToTurnCompletedMessageWithoutItems() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{\"message\":\"fallback text\"}}");

        assertEquals("fallback text", turn.future().join().getContent());
    }

    @Test
    void shouldPreserveTurnCompletionStatus() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"status\":\"completed\"}}}");

        assertEquals("completed", turn.future().join().getFinishReason());
    }

    @Test
    void shouldUseCompletedFinishReasonWhenServerOmitsStatus() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertEquals("completed", turn.future().join().getFinishReason());
    }

    @Test
    void shouldRememberThreadMappingOnTurnCompleted() {
        ThreadMappingCache cache = new ThreadMappingCache(10);
        CodexAppServerTurn turn = newTurn(
                AppServerTurnRequest.builder().prompt("hi").sessionKey("chat-7").build(), cache);

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_7\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertEquals("th_7", turn.future().join().getThreadId());
        assertEquals("th_7", cache.get("chat-7"));
    }

    @Test
    void shouldNotRememberMappingWithoutSessionKey() {
        ThreadMappingCache cache = new ThreadMappingCache(10);
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(), cache);

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertEquals(0, cache.size());
    }

    @Test
    void shouldFailOnTurnFailedNotification() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.handleFrame("{\"method\":\"turn/failed\",\"params\":{\"message\":\"model down\"}}");

        assertTrue(turn.future().isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, () -> turn.future().join());
        CodexAppServerException cause = assertInstanceOf(CodexAppServerException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("model down"));
    }

    @Test
    void shouldFailOnErrorNotification() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.handleFrame("{\"method\":\"error\",\"params\":{\"code\":500}}");

        assertTrue(turn.future().isCompletedExceptionally());
    }

    @Test
    void shouldIgnoreUnknownNotificationsAndInvalidFrames() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.handleFrame("{\"method\":\"thread/tokenUsage/updated\",\"params\":{}}");
        turn.handleFrame("not json at all");

        assertFalse(turn.future().isDone());
    }

    @Test
    void shouldFailWhenSocketClosesBeforeCompletion() {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        turn.onClose(null, 1000, "bye");

        assertTrue(turn.future().isCompletedExceptionally());
    }

    @Test
    void shouldCompleteWithoutErrorAfterTurnCompletedWhenSocketClosesLate() throws Exception {
        CodexAppServerTurn turn = newTurn(AppServerTurnRequest.builder().prompt("hi").build(),
                new ThreadMappingCache(10));

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");
        turn.onClose(null, 1000, "bye");

        assertTrue(turn.future().isDone());
        assertEquals("stop", turn.future().get(1, TimeUnit.SECONDS).getFinishReason());
    }

    @Test
    void shouldFailTurnWhenFrameExceedsCap() {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setMaxFrameChars(8);
        CodexAppServerTurn turn = new CodexAppServerTurn(
                AppServerTurnRequest.builder().prompt("hi").build(), config, mapper, new ThreadMappingCache(10), null);

        turn.onText(null, "way-too-long-frame-data", false);

        assertTrue(turn.future().isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, () -> turn.future().join());
        CodexAppServerException cause = assertInstanceOf(CodexAppServerException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("maxFrameChars"));
    }

    @Test
    void shouldTruncateContentAtCapWithoutFailingTurn() {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setMaxContentChars(10);
        List<String> deltas = new ArrayList<>();
        CodexAppServerTurn turn = new CodexAppServerTurn(
                AppServerTurnRequest.builder().prompt("hi").onDelta(deltas::add).build(),
                config, mapper, new ThreadMappingCache(10), null);

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"agentMessage\",\"text\":\"12345\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"agentMessage\",\"text\":\"67890\"}}}");
        turn.handleFrame("{\"method\":\"item/completed\",\"params\":{\"item\":{\"type\":\"agentMessage\",\"text\":\"ABCDE\"}}}");
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        AppServerTurnResult result = turn.future().join();
        assertEquals("1234567890", result.getContent());
        assertEquals(List.of("12345", "67890"), deltas, "truncated-to-empty text must not emit a delta");
        assertEquals("stop", result.getFinishReason());
    }

    @Test
    void shouldTreatNonPositiveCapsAsUnbounded() {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setMaxFrameChars(0);
        config.setMaxContentChars(-1);
        CodexAppServerTurn turn = new CodexAppServerTurn(
                AppServerTurnRequest.builder().prompt("hi").build(), config, mapper, new ThreadMappingCache(10), null);

        handshake(turn);
        turn.handleFrame("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"thread\":{\"id\":\"th_1\"}}}");
        turn.onText(null, "x".repeat(4096), false);
        turn.handleFrame("{\"method\":\"turn/completed\",\"params\":{}}");

        assertTrue(turn.future().isDone());
        assertFalse(turn.future().isCompletedExceptionally());
    }
}
