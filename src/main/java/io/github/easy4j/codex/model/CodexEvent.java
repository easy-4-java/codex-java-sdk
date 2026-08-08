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
package io.github.easy4j.codex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Single JSON-Lines event emitted by {@code codex exec --json}.
 *
 * <p>When the agent runs with the {@code --json} flag it prints one JSON
 * object per line on standard output. {@link CodexClient#execAndParse(String)}
 * decodes every line into an instance of this class so callers can react to
 * lifecycle events (task creation, message, completion, error) without parsing
 * raw JSON themselves.</p>
 *
 * <p>{@code data} and {@code error} are intentionally typed as
 * {@link Object} because the inner shape varies by event type &mdash; callers
 * who need strongly-typed access should re-deserialise those fields with
 * Jackson once the concrete schema is known.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see CodexDoctorReport
 * @see CodexSession
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexEvent {

    /** Discriminator string identifying the event type (e.g. {@code "task_started"}, {@code "message"}). */
    private String type;

    /** Human-readable message associated with the event, may be {@code null}. */
    private String message;

    /** Identifier of the task the event refers to. */
    @JsonProperty("task_id")
    private String taskId;

    /** Identifier of the session the event belongs to. */
    @JsonProperty("session_id")
    private String sessionId;

    /** Type-specific structured payload; schema depends on {@link #type}. */
    private Object data;

    /** Error payload present when the agent reports a failure for the current step. */
    private Object error;
}
