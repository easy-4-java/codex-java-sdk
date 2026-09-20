/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.codex.appserver;

import java.net.http.WebSocket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Serializes text writes for one JDK {@link WebSocket}. The JDK WebSocket
 * contract permits only one outstanding send at a time, so every write is
 * chained after the previous write future.
 */
final class CodexWebSocketSender {

    private final WebSocket socket;
    private CompletionStage<WebSocket> tail;

    CodexWebSocketSender(WebSocket socket) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.tail = CompletableFuture.completedFuture(socket);
    }

    synchronized CompletionStage<WebSocket> send(String text) {
        Objects.requireNonNull(text, "text");
        CompletionStage<WebSocket> next =
                tail.thenCompose(ignored -> socket.sendText(text, true));
        tail = next;
        return next;
    }
}
