/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.codex.appserver;

/**
 * Storage abstraction for logical session-key to Codex thread-id mappings.
 */
public interface ThreadMappingStore {

    String get(String sessionKey);

    void put(String sessionKey, String threadId);

    void remove(String sessionKey);
}
