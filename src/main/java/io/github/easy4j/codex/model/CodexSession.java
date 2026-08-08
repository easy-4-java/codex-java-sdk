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
 * Metadata describing a single Codex session, as decoded from the JSON output
 * of commands such as {@code codex resume --all}.
 *
 * <p>Codex stores every session in {@code ~/.codex/} as a JSON file. The
 * {@code --all} flag aggregates those files and prints them as a JSON array;
 * this class represents one element of that array.</p>
 *
 * <p>Unknown fields are intentionally tolerated so the SDK stays forward
 * compatible with future CLI revisions.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see CodexEvent
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexSession {

    /** Stable opaque identifier used by {@code codex resume <id>} and {@code codex fork <id>}. */
    private String id;

    /** Display name assigned to the session (often a prompt summary). */
    private String name;

    /** Absolute working directory captured when the session was created. */
    private String cwd;

    /** ISO-8601 timestamp of when the session was first persisted. */
    @JsonProperty("created_at")
    private String createdAt;

    /** ISO-8601 timestamp of the last mutation. */
    @JsonProperty("updated_at")
    private String updatedAt;

    /** {@code true} when the session has been moved out of the active list via {@code codex archive}. */
    @JsonProperty("is_archived")
    private boolean archived;

    /** {@code true} when the session was launched in interactive (TUI) mode rather than via {@code codex exec}. */
    @JsonProperty("is_interactive")
    private boolean interactive;

    /** Model identifier pinned by the session, or {@code null} when the global default was used. */
    @JsonProperty("model")
    private String model;
}
