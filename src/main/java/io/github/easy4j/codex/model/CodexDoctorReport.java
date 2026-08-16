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
import lombok.Data;

import java.util.List;

/**
 * Strongly-typed representation of the JSON document emitted by
 * {@code codex doctor --json}.
 *
 * <p>The doctor sub-command probes the local environment (CLI version, Node
 * version, platform, network reachability, etc.) and prints a structured
 * payload. This class lets SDK callers parse that payload without depending on
 * the raw JSON shape. Unknown properties are tolerated so the SDK remains
 * compatible with future CLI versions.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexEvent
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexDoctorReport {

    /** Version string reported by the {@code codex} CLI. */
    private String version;

    /** Platform identifier reported by the {@code codex} CLI (e.g. {@code darwin-arm64}). */
    private String platform;

    /** Node.js runtime version bundled with the CLI. */
    private String nodeVersion;

    /** Individual environment checks performed by the doctor sub-command. */
    private List<CheckItem> checks;

    /**
     * Single row of the doctor report's {@code checks} array.
     *
     * <p>Each {@code CheckItem} describes one probe &mdash; for example an
     * authentication check or an MCP connectivity check &mdash; together with
     * its outcome and an optional diagnostic message.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckItem {
        /** Human-readable name of the probe (e.g. {@code "Login"}). */
        private String name;

        /** Status string reported by the CLI; the canonical values are {@code "ok"} and {@code "warn"} / {@code "error"}. */
        private String status;

        /** Free-form diagnostic text, may be {@code null} when no further detail is needed. */
        private String message;

        /** Convenience boolean that mirrors {@link #status}: {@code true} when the probe passed. */
        private boolean passed;
    }
}
