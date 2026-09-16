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

import lombok.Data;

/**
 * Configuration for the Codex app-server WebSocket route.
 *
 * <p>The app-server route drives a remote Codex instance over JSON-RPC 2.0
 * on a WebSocket, as opposed to the local CLI subprocess route driven by
 * {@link io.github.easy4j.codex.CodexClient}. Field names intentionally
 * mirror the commonly used {@code CodexEndpoint} binding so a Spring
 * {@code @ConfigurationProperties} class can map onto this POJO without
 * translation.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient
 */
@Data
public class CodexAppServerConfig {

    /**
     * Base URL of the Codex app-server. {@code ws://} / {@code wss://} are used
     * as-is; {@code http://} / {@code https://} are upgraded to {@code ws://} /
     * {@code wss://} automatically. Trailing slashes are ignored.
     */
    private String baseUrl;

    /** Bearer token sent as {@code Authorization: Bearer <token>} on the WebSocket handshake; may be {@code null}. */
    private String token;

    /** Timeout in milliseconds for the TCP/TLS and WebSocket handshake phases. */
    private int connectTimeoutMillis = 5_000;

    /** Upper bound in milliseconds for a whole turn: connect, thread start/resume, turn start and completion. */
    private int readTimeoutMillis = 120_000;

    /**
     * Maximum number of {@code sessionKey &rarr; threadId} mappings retained for
     * session reuse. The cache is access-ordered LRU; evicted sessions silently
     * degrade to starting a fresh thread. Defaults to {@code 1000}.
     */
    private int maxSessionMappings = 1000;

    /**
     * Hard cap in characters for the frame accumulation buffer. A server frame
     * exceeding it fails the turn with {@link CodexAppServerException}. Values
     * {@code <= 0} mean unbounded. Defaults to 1&nbsp;MiB characters.
     */
    private int maxFrameChars = 1_048_576;

    /**
     * Hard cap in characters for the agent-message content accumulated per
     * turn; excess item text is truncated (with a warning) rather than failing
     * the turn. Values {@code <= 0} mean unbounded. Defaults to 1&nbsp;MiB
     * characters.
     */
    private int maxContentChars = 1_048_576;
}
