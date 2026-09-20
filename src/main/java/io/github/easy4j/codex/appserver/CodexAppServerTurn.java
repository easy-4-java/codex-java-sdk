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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One JSON-RPC turn against the Codex app-server, driven over a WebSocket.
 *
 * <p>The message sequence is {@code thread/start} (or {@code thread/resume}
 * when the request's session key already maps to a thread id) &rarr;
 * {@code turn/start} with a single text input item &rarr; consumption of
 * {@code item/completed} notifications (only agent messages surface as
 * deltas) &rarr; completion on {@code turn/completed}. Unknown notifications
 * are logged at debug level and never interrupt the turn.</p>
 *
 * <p>Each turn owns its WebSocket connection; a shared {@link HttpClient} and
 * a shared {@link ThreadMappingCache} are supplied by the owning
 * {@link CodexAppServerClient}, which keeps the class safe to run for
 * concurrent turns.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient
 */
@Slf4j
class CodexAppServerTurn implements WebSocket.Listener {

    /**
     * Accepted identifiers of agent-message items; the Codex protocol has
     * drifted between camelCase and snake_case across revisions.
     */
    private static final List<String> AGENT_MESSAGE_TYPES =
            List.of("agentMessage", "agent_message", "message");

    private final AppServerTurnRequest request;
    private final CodexAppServerConfig config;
    private final ObjectMapper objectMapper;
    private final ThreadMappingStore threadBySession;
    private final HttpClient httpClient;
    private final CompletableFuture<AppServerTurnResult> future = new CompletableFuture<>();
    private final Map<Long, CompletableFuture<JsonNode>> pendingRpcs = new ConcurrentHashMap<>();
    private final AtomicLong rpcIds = new AtomicLong();
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder frameBuffer = new StringBuilder();
    private final Set<String> streamedAgentItemIds = ConcurrentHashMap.newKeySet();
    /** Records outgoing RPC payloads; without a socket they are only recorded, for contract tests. */
    private final List<String> sentMessages = new ArrayList<>();

    private volatile WebSocket webSocket;
    private volatile CodexWebSocketSender sender;
    private volatile String threadId;
    private volatile String turnId;

    CodexAppServerTurn(AppServerTurnRequest request,
                       CodexAppServerConfig config,
                       ObjectMapper objectMapper,
                       ThreadMappingStore threadBySession,
                       HttpClient httpClient) {
        this.request = Objects.requireNonNull(request, "request");
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.threadBySession = Objects.requireNonNull(threadBySession, "threadBySession");
        this.httpClient = httpClient;
    }

    /**
     * Opens the WebSocket, mounts the read-timeout guard and starts the RPC
     * sequence once the handshake completes.
     *
     * @return the future completed with the turn result, or completed
     *         exceptionally with a {@link CodexAppServerException}.
     */
    CompletableFuture<AppServerTurnResult> start() {
        Objects.requireNonNull(httpClient, "httpClient");
        String url = toWebSocketUrl(config.getBaseUrl());
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
                        completeError(new CodexAppServerException("Codex WebSocket connection failed", error));
                    }
                });
        future.orTimeout(config.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    if (Objects.nonNull(error)) {
                        log.warn("Codex turn timed out after {} ms", config.getReadTimeoutMillis());
                    }
                    abort();
                });
        return future;
    }

    @Override
    public void onOpen(WebSocket socket) {
        this.webSocket = socket;
        this.sender = new CodexWebSocketSender(socket);
        socket.request(1);
        begin();
    }

    /**
     * Sends the JSON-RPC lifecycle handshake first — real codex app-servers
     * (verified against 0.154.0) reject any request before
     * {@code initialize} with {@code -32600 Not initialized} — then starts or
     * resumes the thread.
     *
     * <p>Split from {@link #onOpen(WebSocket)} so contract tests can drive the
     * turn without a socket; requests are only recorded in
     * {@link #sentMessages} when no socket is attached.</p>
     */
    void begin() {
        CompletableFuture<JsonNode> initRpc = newRpc(CodexAppServerProtocol.INITIALIZE, buildInitializeParams());
        initRpc.thenAccept(result ->
                sendNotification(CodexAppServerProtocol.INITIALIZED)
                        .whenComplete((ignored, sendError) -> {
                            if (Objects.nonNull(sendError)) {
                                completeError(new CodexAppServerException(
                                        "Codex initialized notification send failed",
                                        unwrap(sendError)));
                                return;
                            }
                            startOrResumeThread();
                        })
        ).exceptionally(error -> {
            completeError(unwrap(error));
            return null;
        });
    }

    private void startOrResumeThread() {
        String sessionKey = request.normalizedSessionKey();
        String previousThreadId = Objects.isNull(sessionKey) ? null : threadBySession.get(sessionKey);
        boolean resume = hasText(previousThreadId);
        CompletableFuture<JsonNode> rpc = newRpc(resume ? CodexAppServerProtocol.THREAD_RESUME : CodexAppServerProtocol.THREAD_START,
                buildThreadStartParams(resume ? previousThreadId : null));
        rpc.thenAccept(result -> {
            threadId = extractThreadId(result);
            if (!hasText(threadId)) {
                completeError(new CodexAppServerException("Codex thread/start returned no threadId"));
                return;
            }
            newRpc(CodexAppServerProtocol.TURN_START, buildTurnStartParams(threadId));
        }).exceptionally(error -> {
            completeError(unwrap(error));
            return null;
        });
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
        if (frameBuffer.length() + data.length() > effectiveMaxFrameChars()) {
            completeError(new CodexAppServerException(
                    "Codex frame buffer exceeded maxFrameChars=" + config.getMaxFrameChars()));
            return null;
        }
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
        completeError(new CodexAppServerException("Codex WebSocket error", error));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
        if (!future.isDone()) {
            completeError(new CodexAppServerException(
                    "Codex WebSocket closed before completion: statusCode=" + statusCode + " reason=" + reason));
        }
        return null;
    }

    /**
     * Handles one JSON-RPC frame: messages carrying an {@code id} are RPC
     * responses, everything else is dispatched as a notification.
     *
     * @param frame the raw JSON text.
     */
    void handleFrame(String frame) {
        JsonNode node;
        try {
            node = objectMapper.readTree(frame);
        } catch (Exception ex) {
            log.warn("Ignored non-JSON frame from Codex app-server");
            return;
        }
        if (node.hasNonNull("id")) {
            CompletableFuture<JsonNode> rpc = pendingRpcs.remove(node.get("id").asLong());
            if (Objects.isNull(rpc)) {
                return;
            }
            if (node.hasNonNull("error")) {
                rpc.completeExceptionally(new CodexAppServerException(
                        "Codex RPC failed: " + node.get("error").toString()));
            } else {
                rpc.complete(node.path("result"));
            }
            return;
        }
        String method = node.path("method").asText("");
        JsonNode params = node.path("params");
        switch (method) {
            case CodexAppServerProtocol.TURN_STARTED -> onTurnStarted(params);
            case CodexAppServerProtocol.AGENT_MESSAGE_DELTA -> onAgentMessageDelta(params);
            case CodexAppServerProtocol.ITEM_COMPLETED -> onItemCompleted(params);
            case CodexAppServerProtocol.TURN_COMPLETED -> onTurnCompleted(params);
            case CodexAppServerProtocol.TURN_FAILED -> completeError(new CodexAppServerException(
                    "Codex turn failed: " + params.path("message").asText("unknown")));
            case CodexAppServerProtocol.ERROR -> completeError(new CodexAppServerException(
                    "Codex server error: " + params.toString()));
            default -> log.debug("Ignored Codex notification: method={}", method);
        }
    }

    private void onTurnStarted(JsonNode params) {
        String reported = firstText(params, "turnId", "turn_id");
        if (hasText(reported)) {
            turnId = reported;
            if (Objects.nonNull(request.getOnTurnStarted())) {
                request.getOnTurnStarted().accept(reported);
            }
            if (Objects.nonNull(request.getListener())) {
                request.getListener().onTurnStarted(reported);
            }
        }
    }

    private void onAgentMessageDelta(JsonNode params) {
        String delta = firstText(params, "delta");
        if (!hasText(delta)) {
            return;
        }
        String itemId = firstText(params, "itemId", "item_id");
        if (hasText(itemId)) {
            streamedAgentItemIds.add(itemId);
        }
        String applied = truncateToContentCap(delta);
        content.append(applied);
        if (!applied.isEmpty() && Objects.nonNull(request.getOnDelta())) {
            request.getOnDelta().accept(applied);
        }
        if (!applied.isEmpty() && Objects.nonNull(request.getListener())) {
            request.getListener().onTextDelta(applied);
        }
    }

    private void onItemCompleted(JsonNode params) {
        JsonNode item = params.path("item");
        String type = firstText(item, "type", "itemType");
        if (hasText(type) && !AGENT_MESSAGE_TYPES.contains(type)) {
            log.debug("Ignored completed item: type={}", type);
            return;
        }
        String text = firstText(item, "text", "content");
        if (!hasText(text)) {
            return;
        }
        String itemId = firstText(item, "id", "itemId", "item_id");
        if (Objects.nonNull(request.getListener())) {
            request.getListener().onItemCompleted(type, text);
        }
        if (hasText(itemId) && streamedAgentItemIds.contains(itemId)) {
            return;
        }
        String applied = truncateToContentCap(text);
        content.append(applied);
        if (!applied.isEmpty() && Objects.nonNull(request.getOnDelta())) {
            request.getOnDelta().accept(applied);
        }
    }

    /** Applies the {@code maxContentChars} hard cap; excess text is dropped with a single warning. */
    private String truncateToContentCap(String text) {
        int cap = effectiveMaxContentChars();
        if (content.length() >= cap) {
            return "";
        }
        if (content.length() + text.length() > cap) {
            log.warn("Codex turn content truncated at maxContentChars={}", config.getMaxContentChars());
            return text.substring(0, cap - content.length());
        }
        return text;
    }

    private int effectiveMaxFrameChars() {
        return config.getMaxFrameChars() <= 0 ? Integer.MAX_VALUE : config.getMaxFrameChars();
    }

    private int effectiveMaxContentChars() {
        return config.getMaxContentChars() <= 0 ? Integer.MAX_VALUE : config.getMaxContentChars();
    }

    private void onTurnCompleted(JsonNode params) {
        rememberThreadMapping();
        String finalContent = content.length() > 0 ? content.toString() : params.path("message").asText("");
        future.complete(AppServerTurnResult.builder()
                .threadId(threadId)
                .turnId(turnId)
                .content(finalContent)
                .finishReason("stop")
                .build());
        close();
    }

    private CompletableFuture<JsonNode> newRpc(String method, Map<String, Object> params) {
        long id = rpcIds.incrementAndGet();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.put("params", params);
        CompletableFuture<JsonNode> rpc = new CompletableFuture<>();
        pendingRpcs.put(id, rpc);
        rpc.exceptionally(error -> {
            completeError(unwrap(error));
            return null;
        });
        sendText(toJson(payload)).whenComplete((ignored, sendError) -> {
            if (Objects.nonNull(sendError)) {
                rpc.completeExceptionally(new CodexAppServerException(
                        "Codex WebSocket send failed for " + method,
                        unwrap(sendError)));
            }
        });
        return rpc;
    }

    Map<String, Object> buildThreadStartParams(String resumeThreadId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (hasText(resumeThreadId)) {
            params.put("threadId", resumeThreadId);
        }
        return params;
    }

    Map<String, Object> buildInitializeParams() {
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("name", "easy4j-codex-java-sdk");
        clientInfo.put("version", "2.0.x");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("clientInfo", clientInfo);
        return params;
    }

    private CompletionStage<WebSocket> sendNotification(String method) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        return sendText(toJson(payload));
    }

    Map<String, Object> buildTurnStartParams(String targetThreadId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "text");
        input.put("text", request.getPrompt());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", targetThreadId);
        params.put("input", List.of(input));
        return params;
    }

    private void rememberThreadMapping() {
        String sessionKey = request.normalizedSessionKey();
        if (Objects.nonNull(sessionKey) && hasText(threadId)) {
            threadBySession.put(sessionKey, threadId);
        }
    }

    private CompletionStage<WebSocket> sendText(String text) {
        sentMessages.add(text);
        CodexWebSocketSender currentSender = sender;
        if (Objects.isNull(currentSender)) {
            return CompletableFuture.completedFuture(webSocket);
        }
        return currentSender.send(text);
    }

    private void close() {
        WebSocket socket = webSocket;
        if (Objects.nonNull(socket)) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "turn completed");
        }
    }

    private void abort() {
        WebSocket socket = webSocket;
        if (Objects.nonNull(socket)) {
            socket.abort();
        }
    }

    private void completeError(Throwable error) {
        if (!future.isDone()) {
            future.completeExceptionally(error);
        }
        abort();
    }

    private String extractThreadId(JsonNode result) {
        // codex ≥0.14x 将线程对象嵌套在 result.thread（实测 0.154.0 返回
        // result.thread.id = UUID）；旧版本为顶层 threadId/thread_id。两者都兼容。
        String threadId = firstText(result.path("thread"), "id", "threadId", "thread_id");
        if (!hasText(threadId)) {
            threadId = firstText(result, "threadId", "thread_id");
        }
        return hasText(threadId) ? threadId : null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (Objects.isNull(node)) {
            return null;
        }
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new CodexAppServerException("Codex RPC serialization failed", ex);
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && Objects.nonNull(current.getCause())) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.trim().isEmpty();
    }

    List<String> sentMessages() {
        return sentMessages;
    }

    CompletableFuture<AppServerTurnResult> future() {
        return future;
    }

    /**
     * Normalizes the configured base URL into a WebSocket address.
     *
     * <p>{@code ws}/{@code wss} are kept as-is; {@code http}/{@code https} are
     * upgraded; only trailing slashes are stripped and no path is appended
     * (the Codex app-server listens on the root path).</p>
     *
     * @param baseUrl the configured base URL.
     * @return the WebSocket URL.
     * @throws IllegalArgumentException when the URL is blank or uses an
     *                                  unsupported scheme.
     */
    static String toWebSocketUrl(String baseUrl) {
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("Codex base-url must not be blank");
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
            return trimmed;
        }
        if (trimmed.startsWith("https://")) {
            return "wss://" + trimmed.substring("https://".length());
        }
        if (trimmed.startsWith("http://")) {
            return "ws://" + trimmed.substring("http://".length());
        }
        throw new IllegalArgumentException("Codex base-url must be a ws/wss/http/https address: " + baseUrl);
    }
}
