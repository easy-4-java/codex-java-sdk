/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.codex.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

class CodexWebSocketSenderTest {

    @Test
    void shouldSerializeWritesUntilPreviousSendCompletes() {
        List<String> sent = new ArrayList<>();
        Queue<CompletableFuture<WebSocket>> completions = new ArrayDeque<>();
        CompletableFuture<WebSocket> firstGate = new CompletableFuture<>();
        CompletableFuture<WebSocket> secondGate = new CompletableFuture<>();
        completions.add(firstGate);
        completions.add(secondGate);
        WebSocket socket = socket(sent, completions);

        CodexWebSocketSender sender = new CodexWebSocketSender(socket);
        CompletionStage<WebSocket> first = sender.send("one");
        CompletionStage<WebSocket> second = sender.send("two");

        assertEquals(List.of("one"), sent, "second write must wait for the first send future");

        firstGate.complete(socket);
        assertEquals(List.of("one", "two"), sent);

        secondGate.complete(socket);
        first.toCompletableFuture().join();
        second.toCompletableFuture().join();
    }

    @Test
    void shouldShortCircuitLaterWritesWhenPreviousSendFails() {
        List<String> sent = new ArrayList<>();
        Queue<CompletableFuture<WebSocket>> completions = new ArrayDeque<>();
        CompletableFuture<WebSocket> firstGate = new CompletableFuture<>();
        completions.add(firstGate);
        WebSocket socket = socket(sent, completions);

        CodexWebSocketSender sender = new CodexWebSocketSender(socket);
        CompletionStage<WebSocket> first = sender.send("one");
        CompletionStage<WebSocket> second = sender.send("two");

        RuntimeException failure = new RuntimeException("write failed");
        firstGate.completeExceptionally(failure);

        assertThrows(CompletionException.class, () -> first.toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> second.toCompletableFuture().join());
        assertEquals(List.of("one"), sent, "failed connection must not emit queued writes");
    }

    private WebSocket socket(List<String> sent,
                             Queue<CompletableFuture<WebSocket>> completions) {
        return (WebSocket) Proxy.newProxyInstance(
                WebSocket.class.getClassLoader(),
                new Class<?>[]{WebSocket.class},
                (proxy, method, args) -> {
                    if ("sendText".equals(method.getName())) {
                        sent.add((String) args[0]);
                        CompletableFuture<WebSocket> completion = completions.poll();
                        if (completion == null) {
                            return CompletableFuture.completedFuture((WebSocket) proxy);
                        }
                        return completion;
                    }
                    if ("getSubprotocol".equals(method.getName())) {
                        return "";
                    }
                    if ("isOutputClosed".equals(method.getName())
                            || "isInputClosed".equals(method.getName())) {
                        return false;
                    }
                    if ("sendClose".equals(method.getName())
                            || "sendPing".equals(method.getName())
                            || "sendPong".equals(method.getName())
                            || "sendBinary".equals(method.getName())) {
                        return CompletableFuture.completedFuture((WebSocket) proxy);
                    }
                    return null;
                });
    }
}
