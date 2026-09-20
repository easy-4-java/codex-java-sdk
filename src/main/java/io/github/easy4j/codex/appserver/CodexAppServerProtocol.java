/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.easy4j.codex.appserver;

/**
 * Canonical method and notification names used by the Codex app-server
 * integration. Keeping protocol strings in one place prevents the turn and
 * generic-RPC transports from drifting apart.
 */
final class CodexAppServerProtocol {

    static final String INITIALIZE = "initialize";
    static final String INITIALIZED = "notifications/initialized";

    static final String THREAD_START = "thread/start";
    static final String THREAD_RESUME = "thread/resume";
    static final String THREAD_LIST = "thread/list";
    static final String THREAD_READ = "thread/read";
    static final String THREAD_FORK = "thread/fork";
    static final String THREAD_ARCHIVE = "thread/archive";
    static final String THREAD_UNARCHIVE = "thread/unarchive";
    static final String THREAD_DELETE = "thread/delete";

    static final String TURN_START = "turn/start";
    static final String TURN_STEER = "turn/steer";
    static final String TURN_INTERRUPT = "turn/interrupt";

    static final String TURN_STARTED = "turn/started";
    static final String TURN_COMPLETED = "turn/completed";
    static final String TURN_FAILED = "turn/failed";
    static final String ITEM_STARTED = "item/started";
    static final String ITEM_COMPLETED = "item/completed";
    static final String AGENT_MESSAGE_DELTA = "item/agentMessage/delta";
    static final String TOKEN_USAGE_UPDATED = "thread/tokenUsage/updated";
    static final String ERROR = "error";

    private CodexAppServerProtocol() {
    }
}
