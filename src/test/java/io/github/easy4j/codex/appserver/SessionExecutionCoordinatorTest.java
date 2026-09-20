/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.codex.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SessionExecutionCoordinatorTest {

    @Test
    void shouldSerializeTasksForSameSessionKey() {
        SessionExecutionCoordinator coordinator = new SessionExecutionCoordinator();
        CompletableFuture<String> firstGate = new CompletableFuture<>();
        AtomicInteger starts = new AtomicInteger();

        CompletableFuture<String> first = coordinator.submit("chat", () -> {
            starts.incrementAndGet();
            return firstGate;
        });
        CompletableFuture<String> second = coordinator.submit("chat", () -> {
            starts.incrementAndGet();
            return CompletableFuture.completedFuture("second");
        });

        assertEquals(1, starts.get(), "second same-session task must wait");
        firstGate.complete("first");

        assertEquals("first", first.join());
        assertEquals("second", second.join());
        assertEquals(2, starts.get());
    }

    @Test
    void shouldAllowDifferentSessionsToRunConcurrently() {
        SessionExecutionCoordinator coordinator = new SessionExecutionCoordinator();
        CompletableFuture<String> gateA = new CompletableFuture<>();
        CompletableFuture<String> gateB = new CompletableFuture<>();
        AtomicInteger starts = new AtomicInteger();

        CompletableFuture<String> a = coordinator.submit("a", () -> {
            starts.incrementAndGet();
            return gateA;
        });
        CompletableFuture<String> b = coordinator.submit("b", () -> {
            starts.incrementAndGet();
            return gateB;
        });

        assertEquals(2, starts.get(), "different sessions must not block each other");
        assertFalse(a.isDone());
        assertFalse(b.isDone());

        gateA.complete("a");
        gateB.complete("b");
        assertEquals("a", a.join());
        assertEquals("b", b.join());
    }

    @Test
    void shouldRemoveSessionTailAfterCompletionAndFailure() {
        SessionExecutionCoordinator coordinator = new SessionExecutionCoordinator();

        coordinator.submit("ok", () -> CompletableFuture.completedFuture("ok")).join();

        CompletableFuture<String> failed = coordinator.submit("bad", () -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("boom"));
            return future;
        });
        try {
            failed.join();
        } catch (RuntimeException ignored) {
            // expected
        }

        assertEquals(0, coordinator.activeSessionCount());
    }
}
