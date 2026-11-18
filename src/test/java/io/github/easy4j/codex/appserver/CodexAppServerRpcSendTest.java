package io.github.easy4j.codex.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class CodexAppServerRpcSendTest {

    @Test
    void queuesInitializedAndMainRequestUntilPreviousWebSocketSendCompletes() throws Exception {
        List<String> sent = new ArrayList<>();
        Queue<CompletableFuture<WebSocket>> gates = new ArrayDeque<>();
        CompletableFuture<WebSocket> initialize = new CompletableFuture<>();
        CompletableFuture<WebSocket> initialized = new CompletableFuture<>();
        CompletableFuture<WebSocket> main = new CompletableFuture<>();
        gates.add(initialize);
        gates.add(initialized);
        gates.add(main);
        AtomicBoolean pending = new AtomicBoolean();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(
                WebSocket.class.getClassLoader(), new Class<?>[]{WebSocket.class},
                (proxy, method, args) -> {
                    if ("sendText".equals(method.getName())) {
                        if (!pending.compareAndSet(false, true)) {
                            throw new IllegalStateException("Send pending");
                        }
                        sent.add((String) args[0]);
                        CompletableFuture<WebSocket> gate = gates.remove();
                        return gate.thenApply(ignored -> {
                            pending.set(false);
                            return (WebSocket) proxy;
                        });
                    }
                    if ("request".equals(method.getName())) {
                        return proxy;
                    }
                    return null;
                });

        CodexAppServerRpc rpc = new CodexAppServerRpc(
                new CodexAppServerConfig(), JsonMapper.builder().build(),
                CodexAppServerProtocol.THREAD_LIST, Map.of());
        rpc.onOpen(socket);
        assertEquals(1, sent.size());

        rpc.onText(socket, "{\"jsonrpc\":\"2.0\",\"id\":-1,\"result\":{}}", true);
        assertEquals(1, sent.size(), "notification must wait for initialize send completion");

        initialize.complete(socket);
        assertEquals(2, sent.size());
        initialized.complete(socket);
        assertEquals(3, sent.size());
        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals(CodexAppServerProtocol.INITIALIZE,
                mapper.readTree(sent.get(0)).path("method").asText());
        assertEquals(CodexAppServerProtocol.INITIALIZED,
                mapper.readTree(sent.get(1)).path("method").asText());
        assertEquals(CodexAppServerProtocol.THREAD_LIST,
                mapper.readTree(sent.get(2)).path("method").asText());
        main.complete(socket);
    }
}
