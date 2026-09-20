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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * High-level client for the Codex app-server WebSocket route.
 *
 * <p>Where {@link io.github.easy4j.codex.CodexClient} drives a local
 * {@code codex} CLI subprocess, this client drives a remote Codex instance
 * speaking JSON-RPC 2.0 over WebSocket: one turn maps to a
 * {@code thread/start} (or {@code thread/resume}) &rarr; {@code turn/start}
 * &rarr; notifications &rarr; {@code turn/completed} sequence, with the
 * connection opened per turn and closed when the turn finishes.</p>
 *
 * <p>Session continuity across turns is opt-in via
 * {@link AppServerTurnRequest#getSessionKey()}: after a successful turn the
 * resulting thread id is remembered in a bounded LRU cache (see
 * {@link CodexAppServerConfig#getMaxSessionMappings()}) and the next turn with
 * the same key resumes that thread. Sessions evicted from the cache silently
 * degrade to starting a fresh thread.</p>
 *
 * <p>The client is thread-safe: concurrent turns each get their own
 * connection while sharing one {@link HttpClient} and one session mapping.</p>
 *
 * <pre>{@code
 * CodexAppServerConfig config = new CodexAppServerConfig();
 * config.setBaseUrl("ws://codex-host:8081");
 * config.setToken("capability-token");
 * try (CodexAppServerClient client = new CodexAppServerClient(config)) {
 *     AppServerTurnResult result = client.runTurn(AppServerTurnRequest.builder()
 *             .prompt("Fix the failing test")
 *             .sessionKey("chat-42")
 *             .onDelta(delta -> System.out.print(delta))
 *             .build());
 *     System.out.println(result.getContent());
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerConfig
 * @see AppServerTurnRequest
 * @see AppServerTurnResult
 */
public class CodexAppServerClient implements AutoCloseable {

    private final CodexAppServerConfig config;
    private final ObjectMapper objectMapper =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final ThreadMappingStore threadBySession;
    private final SessionExecutionCoordinator sessionCoordinator = new SessionExecutionCoordinator();
    private final Object httpClientLock = new Object();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "codex-app-server-client");
        thread.setDaemon(true);
        return thread;
    });

    private volatile HttpClient httpClient;
    private volatile boolean closed;

    /**
     * Creates a new client bound to the given configuration.
     *
     * @param config runtime configuration; must not be {@code null}.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public CodexAppServerClient(CodexAppServerConfig config) {
        this(config, new ThreadMappingCache(
                Objects.requireNonNull(config, "config").getMaxSessionMappings()));
    }

    public CodexAppServerClient(
            CodexAppServerConfig config,
            ThreadMappingStore threadMappingStore) {
        this.config = Objects.requireNonNull(config, "config");
        this.threadBySession = Objects.requireNonNull(threadMappingStore, "threadMappingStore");
    }

    /**
     * Runs one turn and returns its result, blocking until the turn completes.
     *
     * @param request the turn request; must not be {@code null}.
     * @return the turn result; never {@code null}.
     * @throws CodexAppServerException   when the turn fails (connection,
     *                                   RPC, {@code turn/failed}, {@code error}
     *                                   or read timeout).
     * @throws IllegalArgumentException  when the prompt is blank or the
     *                                   configured base URL is not a
     *                                   ws/wss/http/https address.
     * @throws IllegalStateException     when the client has been closed.
     */
    public AppServerTurnResult runTurn(AppServerTurnRequest request) {
        try {
            return runTurnAsync(request).join();
        } catch (CompletionException ex) {
            Throwable cause = Objects.isNull(ex.getCause()) ? ex : ex.getCause();
            if (cause instanceof CodexAppServerException codexException) {
                throw codexException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CodexAppServerException("Codex app-server turn failed", cause);
        }
    }

    /**
     * Runs one turn asynchronously.
     *
     * @param request the turn request; must not be {@code null}.
     * @return a future completed with the turn result, or completed
     *         exceptionally with a {@link CodexAppServerException}.
     */
    public CompletableFuture<AppServerTurnResult> runTurnAsync(AppServerTurnRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed) {
            throw new IllegalStateException("Codex app-server client is closed");
        }
        if (Objects.isNull(request.getPrompt()) || request.getPrompt().trim().isEmpty()) {
            throw new IllegalArgumentException("Codex prompt must not be blank");
        }
        String sessionKey = request.normalizedSessionKey();
        if (Objects.isNull(sessionKey)) {
            return startTurn(request);
        }
        return sessionCoordinator.submit(sessionKey, () -> startTurn(request));
    }

    private CompletableFuture<AppServerTurnResult> startTurn(AppServerTurnRequest request) {
        if (closed) {
            CompletableFuture<AppServerTurnResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Codex app-server client is closed"));
            return failed;
        }
        CodexAppServerTurn turn =
                new CodexAppServerTurn(request, config, objectMapper, threadBySession, httpClient());
        return turn.start();
    }

    /**
     * Returns the runtime configuration used by this client.
     *
     * @return the configuration; never {@code null}.
     */
    public CodexAppServerConfig getConfig() {
        return config;
    }

    // ============================================================
    // app-server protocol — thread lifecycle & turn control
    // ============================================================

    /**
     * Lists threads via {@code thread/list}.
     *
     * @param limit optional page size ({@code <= 0} omits the field); never {@code null}.
     * @return thread summaries, newest first per server defaults; never {@code null}.
     * @since 3.0.0
     */
    public List<AppServerThread> listThreads(int limit) {
        return listThreads(limit, null);
    }

    /**
     * Lists threads via {@code thread/list} with pagination.
     *
     * @param limit  optional page size ({@code <= 0} omits the field).
     * @param cursor opaque cursor from a previous call; may be {@code null}.
     * @return thread summaries; never {@code null}.
     * @since 3.0.0
     */
    public List<AppServerThread> listThreads(int limit, String cursor) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (hasText(cursor)) {
            params.put("cursor", cursor.trim());
        }
        if (limit > 0) {
            params.put("limit", limit);
        }
        JsonNode result = execRpcNode(CodexAppServerProtocol.THREAD_LIST, params);
        List<AppServerThread> threads = new ArrayList<>();
        for (JsonNode node : result.path("threads")) {
            threads.add(parseThread(node));
        }
        return threads;
    }

    /**
     * Reads a thread via {@code thread/read} (metadata only, no turn history).
     *
     * @param threadId the thread to read.
     * @return the thread summary; never {@code null}.
     * @since 3.0.0
     */
    public AppServerThread readThread(String threadId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        JsonNode result = execRpcNode(CodexAppServerProtocol.THREAD_READ, params);
        return parseThread(result.path("thread"));
    }

    /**
     * Reads a thread including its full turn history via
     * {@code thread/read} with {@code includeTurns=true}.
     *
     * @param threadId the thread to read.
     * @return the raw JSON-RPC {@code result} as a JSON string; never {@code null}.
     * @since 3.0.0
     */
    public String readThreadRaw(String threadId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        params.put("includeTurns", true);
        return toJson(execRpcNode("thread/read", params));
    }

    /**
     * Forks a thread into a new one via {@code thread/fork}.
     *
     * @param threadId the thread to fork.
     * @return the forked thread summary (its {@code id} is the new thread).
     * @since 3.0.0
     */
    public AppServerThread forkThread(String threadId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        JsonNode result = execRpcNode(CodexAppServerProtocol.THREAD_FORK, params);
        return parseThread(result.path("thread"));
    }

    /** Archives a thread via {@code thread/archive}. */
    public void archiveThread(String threadId) {
        simpleThreadCall(CodexAppServerProtocol.THREAD_ARCHIVE, threadId);
    }

    /** Unarchives a thread via {@code thread/unarchive}. */
    public void unarchiveThread(String threadId) {
        simpleThreadCall(CodexAppServerProtocol.THREAD_UNARCHIVE, threadId);
    }

    /**
     * Permanently deletes a thread and its spawned descendants via
     * {@code thread/delete}. Ephemeral roots cannot be deleted.
     */
    public void deleteThread(String threadId) {
        simpleThreadCall(CodexAppServerProtocol.THREAD_DELETE, threadId);
    }

    /**
     * Interrupts a running turn via {@code turn/interrupt}; the turn ends with
     * status {@code interrupted}. Thread state lives server-side, so the
     * interrupt may be issued while the original turn connection is still open.
     *
     * @param threadId the thread owning the turn.
     * @param turnId   the turn to interrupt.
     * @since 3.0.0
     */
    public void interruptTurn(String threadId, String turnId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        params.put("turnId", Objects.requireNonNull(turnId, "turnId").trim());
        execRpcNode(CodexAppServerProtocol.TURN_INTERRUPT, params);
    }

    /**
     * Steers a running turn with additional input via {@code turn/steer};
     * {@code expectedTurnId} must match the currently active turn (see
     * {@link AppServerTurnRequest#getOnTurnStarted()}).
     *
     * @param threadId       the thread owning the turn.
     * @param expectedTurnId the active turn id.
     * @param prompt         the steering input.
     * @since 3.0.0
     */
    public void steerTurn(String threadId, String expectedTurnId, String prompt) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "text");
        input.put("text", Objects.requireNonNull(prompt, "prompt"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        params.put("expectedTurnId", Objects.requireNonNull(expectedTurnId, "expectedTurnId").trim());
        params.put("input", List.of(input));
        execRpcNode(CodexAppServerProtocol.TURN_STEER, params);
    }

    /**
     * Executes an arbitrary documented app-server method and returns the raw
     * JSON-RPC {@code result} as a JSON string — the escape hatch for methods
     * the SDK does not model yet.
     *
     * @param method exact method name (e.g. {@code model/list}).
     * @param params request parameters; may be empty, never {@code null}.
     * @return the serialized {@code result}; never {@code null}.
     * @since 3.0.0
     */
    public String execRpc(String method, Map<String, Object> params) {
        return toJson(execRpcNode(method, params == null ? new LinkedHashMap<>() : params));
    }

    private JsonNode execRpcNode(String method, Map<String, Object> params) {
        if (closed) {
            throw new IllegalStateException("Codex app-server client is closed");
        }
        CodexAppServerRpc rpc = new CodexAppServerRpc(config, objectMapper, method, params);
        return CodexAppServerRpc.join(rpc.start(httpClient()), method);
    }

    private void simpleThreadCall(String method, String threadId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", Objects.requireNonNull(threadId, "threadId").trim());
        execRpcNode(method, params);
    }

    private AppServerThread parseThread(JsonNode node) {
        return AppServerThread.builder()
                .id(firstText(node, "id"))
                .name(firstText(node, "name"))
                .cwd(firstText(node, "cwd"))
                .createdAt(firstText(node, "createdAt", "created_at"))
                .updatedAt(firstText(node, "updatedAt", "updated_at"))
                .archived(firstBool(node, "isArchived", "is_archived", "archived"))
                .build();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (Objects.nonNull(value) && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private boolean firstBool(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                return node.get(field).asBoolean(false);
            }
        }
        return false;
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new CodexAppServerException("Codex result serialization failed", ex);
        }
    }

    /**
     * Closes the client. New turns are rejected afterwards; the executor
     * backing the shared {@link HttpClient} is shut down gracefully. The
     * executor is owned by this client so closing works on every JDK line
     * ({@code HttpClient.close()} only exists since JDK 21).
     */
    @Override
    public void close() {
        closed = true;
        clientExecutor.shutdown();
    }

    private static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.trim().isEmpty();
    }

    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (Objects.isNull(client)) {
            synchronized (httpClientLock) {
                if (Objects.isNull(httpClient)) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
                            .executor(clientExecutor)
                            .build();
                }
                client = httpClient;
            }
        }
        return client;
    }
}
