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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    private final ThreadMappingCache threadBySession;
    private final Object httpClientLock = new Object();

    private volatile HttpClient httpClient;
    private volatile boolean closed;

    /**
     * Creates a new client bound to the given configuration.
     *
     * @param config runtime configuration; must not be {@code null}.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public CodexAppServerClient(CodexAppServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.threadBySession = new ThreadMappingCache(config.getMaxSessionMappings());
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

    /**
     * Closes the client. New turns are rejected afterwards; the shared
     * {@link HttpClient} is shut down once any in-flight turn completes.
     */
    @Override
    public void close() {
        closed = true;
        HttpClient client = httpClient;
        if (Objects.nonNull(client)) {
            client.close();
        }
    }

    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (Objects.isNull(client)) {
            synchronized (httpClientLock) {
                if (Objects.isNull(httpClient)) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
                            .build();
                }
                client = httpClient;
            }
        }
        return client;
    }
}
