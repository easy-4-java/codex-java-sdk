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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式真实集成测试：默认不执行，仅当同时传入
 * {@code -Dcodex.real.base-url=http://host:port}（token 可选
 * {@code -Dcodex.real.token=...}）时对真实 codex app-server 执行。
 *
 * <p>协议层验收点（2026-09-17 对 codex 0.154.0 实测提炼）：</p>
 * <ol>
 *   <li>initialize → notifications/initialized 握手必须先行，否则 -32600 Not initialized；</li>
 *   <li>thread id 嵌套在 {@code result.thread.id}，顶层 {@code threadId} 为空；</li>
 *   <li>turn 阶段失败必须来自模型/凭证层，而不是 {@code no threadId}
 *       这类协议层错误。</li>
 * </ol>
 *
 * @since 2.0.x
 */
@EnabledIfSystemProperty(named = "codex.real.base-url", matches = "https?://.+")
class CodexAppServerRealIntegrationTest {

    @Test
    void listThreadsShouldPassProtocolHandshake() {
        CodexAppServerClient client = client();
        try {
            // 走通用 RPC 通道（tolerant initialize）；能返回列表即证明握手与帧解析可用
            client.listThreads(5);
        } finally {
            client.close();
        }
    }

    @Test
    void runTurnMustGetPastThreadStartEvenWithoutModelCredentials() {
        CodexAppServerClient client = client();
        try {
            try {
                client.runTurn(AppServerTurnRequest.builder()
                        .prompt("只回复两个字母：OK")
                        .sessionKey("real-wire-smoke")
                        .build());
                // 有凭证时直接完成，也算通过
            } catch (CodexAppServerException ex) {
                String message = String.valueOf(ex.getMessage());
                assertFalse(message.contains("no threadId"),
                        "threadId 解析回退到了旧协议: " + message);
                assertFalse(message.contains("Not initialized"),
                        "initialize 握手缺失: " + message);
                // 无模型凭证的真实环境在此收到 turn/failed 类错误——协议层已通过
            }
        } finally {
            client.close();
        }
    }

    private CodexAppServerClient client() {
        CodexAppServerConfig config = new CodexAppServerConfig();
        config.setBaseUrl(System.getProperty("codex.real.base-url").trim());
        String token = System.getProperty("codex.real.token");
        if (token != null && !token.isBlank()) {
            config.setToken(token.trim());
        }
        assertNotNull(config.getBaseUrl());
        assertTrue(config.getConnectTimeoutMillis() > 0);
        return new CodexAppServerClient(config);
    }
}
