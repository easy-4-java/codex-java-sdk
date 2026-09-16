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

import java.util.Objects;
import java.util.function.Consumer;

import lombok.Builder;
import lombok.Data;

/**
 * One turn to run on the Codex app-server.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient#runTurn(AppServerTurnRequest)
 */
@Data
@Builder
public class AppServerTurnRequest {

    /** Prompt sent to the agent as the single {@code {"type":"text"}} input item; must not be blank. */
    private String prompt;

    /**
     * Optional logical session key. When present, the client remembers the
     * {@code sessionKey &rarr; threadId} mapping after a successful turn and
     * resumes the same thread on the next turn with the same key. When absent
     * every turn starts a fresh thread.
     */
    private String sessionKey;

    /**
     * Optional streaming callback invoked once per {@code item/completed}
     * notification whose item is an agent message. Callbacks are invoked
     * serially in arrival order, never concurrently.
     */
    private Consumer<String> onDelta;

    /**
     * Returns the session key in a comparable form, or {@code null} when unset.
     *
     * @return the trimmed session key, or {@code null} when blank.
     */
    public String normalizedSessionKey() {
        if (Objects.isNull(sessionKey)) {
            return null;
        }
        String trimmed = sessionKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
