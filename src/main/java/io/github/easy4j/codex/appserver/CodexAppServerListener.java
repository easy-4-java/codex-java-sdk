/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.easy4j.codex.appserver;

/**
 * Listener for high-value Codex app-server turn events.
 *
 * <p>All methods are optional. Implementations should return quickly because
 * callbacks are invoked on the WebSocket event path.</p>
 */
public interface CodexAppServerListener {

    default void onTurnStarted(String turnId) {
    }

    default void onTextDelta(String delta) {
    }

    default void onItemCompleted(String itemType, String content) {
    }

    default void onTokenUsage(String rawJson) {
    }

    default void onWarning(String rawJson) {
    }
}
