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

import lombok.Builder;
import lombok.Data;

/**
 * Outcome of a successful app-server turn.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient#runTurn(AppServerTurnRequest)
 */
@Data
@Builder
public class AppServerTurnResult {

    /** Codex thread id the turn ran on; also the value stored in the session mapping. */
    private String threadId;

    /** Turn id reported by {@code turn/started}; usable for steer/interrupt while running. */
    private String turnId;

    /**
     * Concatenated text of every agent-message item completed during the turn;
     * falls back to the {@code message} carried by {@code turn/completed} when
     * no agent-message item was received.
     */
    private String content;

    /** Terminal reason; currently always {@code stop} for successful turns. */
    private String finishReason;
}
