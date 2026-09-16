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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Single JSON-RPC 2.0 call against the Codex app-server over a short-lived
 * WebSocket connection, with the documented {@code initialize} handshake.
 *
 * <p>On connect the helper sends {@code initialize} (tolerantly — a server
 * that does not implement it responds with an error which is logged at debug
 * and ignored), then the {@code initialized} notification, then the actual
 * request. Threads are stateful server-side, so lifecycle operations such as
 * {@code turn/interrupt} may target a thread whose turn runs on a different
 * connection.</p>
 *
 * <p>One instance performs exactly one call; failures surface as
 * {@link CodexAppServerException} on the returned future.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient#execRpc(String, Map)
 */
@Slf4j
class CodexAppServerRpc implements WebSocket.Listener {

    private static final long INIT_REQUEST_ID = -1L;
    private static final long MAIN_REQUEST_ID = 1L;

    private final CodexAppServerConfig config;
    private final ObjectMapper objectMapper;
    private final String method;
    private final Map<String, Object> params;
    private final CompletableFuture<JsonNode> future = new CompletableFuture<>();
    private final Map<Long, CompletableFuture<JsonNode>> pendingRpcs = new ConcurrentHashMap<>();
    private final StringBuilder frameBuffer = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile boolean mainRequestSent;

    CodexAppServerRpc(CodexAppServerConfig config,
                      ObjectMapper objectMapper,
                      String method,
                      Map<String, Object> params) {
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.method = Objects.requireNonNull(method, "method");
        this.params = Objects.requireNonNull(params, "params");
    }

    /**
     * Connects, performs the tolerant initialize handshake and dispatches the
     * request.
     *
     * @param httpClient the shared HTTP client.
     * @return the future completed with the JSON-RPC {@code result} node.
     */
    CompletableFuture<JsonNode> start(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient");
        String url = CodexAppServerTurn.toWebSocketUrl(config.getBaseUrl());
        if (hasText(config.getToken()) && url.startsWith("ws://")) {
            log.warn("Codex app-server token is sent over an unencrypted ws:// connection; prefer wss:// in production");
        }
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        builder.connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()));
        if (hasText(config.getToken())) {
            builder.header("Authorization", "Bearer " + config.getToken().trim());
        }
        builder.buildAsync(URI.create(url), this)
                .whenComplete((socket, error) -> {
                    if (Objects.nonNull(error)) {
                        fail(new CodexAppServerException("Codex WebSocket connection failed", error));
                    }
                });
        future.orTimeout(config.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    if (Objects.nonNull(error)) {
                        log.warn("Codex RPC {} timed out after {} ms", method, config.getReadTimeoutMillis());
                    }
                    WebSocket socket = webSocket;
                    if (Objects.nonNull(socket)) {
                        socket.abort();
                    }
                });
        return future;
    }

    @Override
    public void onOpen(WebSocket socket) {
        this.webSocket = socket;
        socket.request(1);
        // Tolerant initialize handshake: the app-server spec requires it, but
        // older daemons may not implement it — an error response is logged and
        // ignored, and the main request proceeds either way.
        Map<String, Object> clientInfo = new java.util.LinkedHashMap<>();
        clientInfo.put("name", "codex-java-sdk");
        clientInfo.put("version", "3.0.x");
        Map<String, Object> initParams = new java.util.LinkedHashMap<>();
        initParams.put("clientInfo", clientInfo);
        sendRpc(INIT_REQUEST_ID, "initialize", initParams);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
        frameBuffer.append(data);
        if (last) {
            String frame = frameBuffer.toString();
            frameBuffer.setLength(0);
            handleFrame(frame);
            socket.request(1);
        }
        return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        fail(new CodexAppServerException("Codex WebSocket error during " + method, error));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
        if (!future.isDone()) {
            fail(new CodexAppServerException(
                    "Codex WebSocket closed before " + method + " completed: statusCode=" + statusCode));
        }
        return null;
    }

    private void handleFrame(String frame) {
        JsonNode node;
        try {
            node = objectMapper.readTree(frame);
        } catch (Exception ex) {
            log.warn("Ignored non-JSON frame from Codex app-server");
            return;
        }
        if (node.hasNonNull("id")) {
            long id = node.get("id").asLong();
            CompletableFuture<JsonNode> rpc = pendingRpcs.remove(id);
            if (Objects.isNull(rpc)) {
                return;
            }
            if (node.hasNonNull("error")) {
                CodexAppServerException codexError = new CodexAppServerException(
                        "Codex RPC " + method + " failed: " + node.get("error").toString());
                rpc.completeExceptionally(codexError);
                if (id == MAIN_REQUEST_ID) {
                    fail(codexError);
                }
            } else {
                JsonNode result = node.path("result");
                rpc.complete(result);
                if (id == MAIN_REQUEST_ID) {
                    // The caller blocks on the instance-level future, not on
                    // the per-request future stored in the pending map.
                    future.complete(result);
                }
            }
            if (id == INIT_REQUEST_ID) {
                // Initialize settled (successfully or not) — proceed with the
                // main request and the `initialized` notification.
                handleInitializeSettled();
            }
            return;
        }
        log.debug("Ignored Codex notification: method={}", node.path("method").asText(""));
    }

    private void sendMainRequest() {
        if (mainRequestSent) {
            return;
        }
        mainRequestSent = true;
        sendRpc(MAIN_REQUEST_ID, method, params);
    }

    private void sendRpc(long id, String rpcMethod, Map<String, Object> rpcParams) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", rpcMethod);
        payload.put("params", rpcParams);
        CompletableFuture<JsonNode> rpc = new CompletableFuture<>();
        pendingRpcs.put(id, rpc);
        rpc.exceptionally(error -> {
            // Initialize rejections must not sink the main request.
            if (id == INIT_REQUEST_ID) {
                log.debug("Codex initialize handshake rejected (continuing): {}", error.toString());
                if (!future.isDone()) {
                    handleInitializeSettled();
                }
                return null;
            }
            fail(error instanceof CodexAppServerException codexError
                    ? codexError : new CodexAppServerException("Codex RPC failed", error));
            return null;
        });
        sendText(toJson(payload));
    }

    private void handleInitializeSettled() {
        WebSocket socket = webSocket;
        if (socket == null) {
            return;
        }
        // The JDK WebSocket allows only one in-flight sendText — chain the
        // `initialized` notification and the main request instead of firing
        // both back-to-back from inside the onText callback.
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "initialized");
        payload.put("params", java.util.Collections.emptyMap());
        socket.sendText(toJson(payload), true).whenComplete((w, error) -> {
            if (Objects.nonNull(error)) {
                fail(new CodexAppServerException("Codex initialized notification failed", error));
                return;
            }
            sendMainRequest();
        });
    }

    private void sendText(String text) {
        WebSocket socket = webSocket;
        if (Objects.nonNull(socket)) {
            socket.sendText(text, true);
        }
    }

    private void fail(Throwable error) {
        if (!future.isDone()) {
            future.completeExceptionally(error);
        }
        WebSocket socket = webSocket;
        if (Objects.nonNull(socket)) {
            socket.abort();
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new CodexAppServerException("Codex RPC serialization failed", ex);
        }
    }

    private static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.trim().isEmpty();
    }

    /** Unwraps a completed RPC future into its result or a runtime exception. */
    static JsonNode join(CompletableFuture<JsonNode> future, String method) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = Objects.isNull(ex.getCause()) ? ex : ex.getCause();
            if (cause instanceof CodexAppServerException codexException) {
                throw codexException;
            }
            throw new CodexAppServerException("Codex RPC " + method + " failed", cause);
        }
    }
}
