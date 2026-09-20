/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.codex.appserver;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Serializes asynchronous work per logical session key while allowing
 * independent sessions to run concurrently.
 */
final class SessionExecutionCoordinator {

    private final ConcurrentHashMap<String, CompletableFuture<Void>> tails =
            new ConcurrentHashMap<>();

    <T> CompletableFuture<T> submit(
            String sessionKey,
            Supplier<CompletableFuture<T>> task) {
        Objects.requireNonNull(task, "task");

        String key = normalize(sessionKey);
        if (key == null) {
            return invoke(task);
        }

        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> previous = tails.put(key, gate);

        CompletableFuture<Void> ready = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, error) -> null);

        CompletableFuture<T> result = ready.thenCompose(ignored -> invoke(task));
        result.whenComplete((value, error) -> {
            gate.complete(null);
            tails.remove(key, gate);
        });
        return result;
    }

    int activeSessionCount() {
        return tails.size();
    }

    private static <T> CompletableFuture<T> invoke(
            Supplier<CompletableFuture<T>> task) {
        try {
            CompletableFuture<T> future = task.get();
            if (future == null) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(
                        new NullPointerException("session task returned null future"));
                return failed;
            }
            return future;
        } catch (Throwable error) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }
    }

    private static String normalize(String sessionKey) {
        if (sessionKey == null) {
            return null;
        }
        String trimmed = sessionKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
