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
package io.github.easy4j.codex.cli;

import lombok.Data;

/**
 * Immutable value object returned by {@link CodexCliExecutor} for every
 * subprocess invocation.
 *
 * <p>The three captured fields cover the entire surface area a caller needs to
 * reason about a subprocess outcome:</p>
 * <ul>
 *   <li>{@link #getExitCode()} &mdash; the OS-level exit status, or {@code -1}
 *       when the process never completed normally (timeout or I/O failure).</li>
 *   <li>{@link #getStdout()} &mdash; trimmed standard-output text.</li>
 *   <li>{@link #getStderr()} &mdash; trimmed standard-error text.</li>
 * </ul>
 *
 * <p>The convenience accessors {@link #isSuccess()} and {@link #isTimeout()}
 * encapsulate the common predicate checks so that callers do not have to
 * inspect the raw exit code or stderr payload themselves.</p>
 *
 * @author easy-4-java contributors
 * @since 3.0.0
 * @see CodexCliExecutor
 * @see CodexCli
 */
@Data
public class CodexCliResult {

    /** OS-level exit code, or {@code -1} when the process did not terminate normally. */
    private final int exitCode;

    /** Trimmed standard-output text captured from the subprocess. */
    private final String stdout;

    /** Trimmed standard-error text captured from the subprocess. */
    private final String stderr;

    /**
     * Returns {@code true} when the subprocess terminated with exit code {@code 0}.
     *
     * @return {@code true} if the call succeeded, {@code false} otherwise.
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /**
     * Returns {@code true} when the subprocess was terminated by the executor's
     * watchdog timer.
     *
     * <p>This is detected heuristically: an exit code of {@code -1} combined
     * with a stderr payload that contains the substring {@code "timed out"}.</p>
     *
     * @return {@code true} if the call exceeded the configured timeout.
     */
    public boolean isTimeout() {
        return exitCode == -1 && stderr != null && stderr.contains("timed out");
    }
}
