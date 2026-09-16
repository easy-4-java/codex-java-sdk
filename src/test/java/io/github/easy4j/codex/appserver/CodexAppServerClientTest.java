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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CodexAppServerClient} validation and failure
 * semantics that do not require a server.
 *
 * @since 3.0.0
 */
class CodexAppServerClientTest {

    private CodexAppServerConfig configWithUrl(String baseUrl) {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setBaseUrl(baseUrl);
        return config;
    }

    @Test
    void shouldExposeSecureDefaults() {
        CodexAppServerConfig config = new CodexAppServerConfig();

        assertNull(config.getToken());
        assertEquals(5_000, config.getConnectTimeoutMillis());
        assertEquals(120_000, config.getReadTimeoutMillis());
        assertEquals(1000, config.getMaxSessionMappings());
    }

    @Test
    void shouldRejectNullRequestAndBlankPrompt() {
        try (CodexAppServerClient client = new CodexAppServerClient(configWithUrl("ws://localhost:8081"))) {
            assertThrows(NullPointerException.class, () -> client.runTurnAsync(null));
            assertThrows(IllegalArgumentException.class,
                    () -> client.runTurn(AppServerTurnRequest.builder().prompt("  ").build()));
            assertThrows(IllegalArgumentException.class,
                    () -> client.runTurn(AppServerTurnRequest.builder().build()));
        }
    }

    @Test
    void shouldRejectUnsupportedBaseUrlSchemes() {
        try (CodexAppServerClient client = new CodexAppServerClient(configWithUrl("ftp://localhost:8081"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.runTurn(AppServerTurnRequest.builder().prompt("hi").build()));
        }
    }

    @Test
    void shouldRejectTurnsAfterClose() {
        CodexAppServerClient client = new CodexAppServerClient(configWithUrl("ws://localhost:8081"));
        client.close();

        assertThrows(IllegalStateException.class,
                () -> client.runTurn(AppServerTurnRequest.builder().prompt("hi").build()));
    }

    @Test
    void shouldFailTurnWhenServerIsUnreachable() {
        CodexAppServerConfig config = configWithUrl("ws://127.0.0.1:1");
        config.setConnectTimeoutMillis(500);
        try (CodexAppServerClient client = new CodexAppServerClient(config)) {
            CodexAppServerException ex = assertThrows(CodexAppServerException.class,
                    () -> client.runTurn(AppServerTurnRequest.builder().prompt("hi").build()));
            assertNotNull(ex.getMessage());
        }
    }
}
