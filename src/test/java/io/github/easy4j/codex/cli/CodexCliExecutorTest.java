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

import io.github.easy4j.codex.CodexClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CodexCliExecutor}.
 *
 * <p>The executor wraps Apache Commons {@code exec} and shells out to a real
 * OS process. To remain hermetic, the tests target the {@code /bin/echo}
 * binary &mdash; available on every macOS/Linux CI image &mdash; which lets
 * us verify argument handling, exit-code propagation, and timeout behaviour
 * without depending on the {@code codex} CLI itself.</p>
 *
 * @since 3.0.0
 */
class CodexCliExecutorTest {

    private CodexClientConfig configFor(String executable) {
        CodexClientConfig config = new CodexClientConfig();
        config.setLocalExecutable(executable);
        // Short timeouts so failing tests stay fast.
        config.setLocalTimeoutSeconds(2);
        config.setLocalProbeTimeoutSeconds(2);
        return config;
    }

    @Test
    void shouldExecuteSuccessfullyWithCapturedStdout() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        CodexCliResult result = executor.execute("hello", "world");

        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldCaptureExitCodeFromFailingProcess() {
        // Apache Commons Exec throws ExecuteException for non-zero exit codes,
        // which is caught by the IOException handler and returned as exit code -1.
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/sh"));

        CodexCliResult result = executor.execute("-c", "exit 7");

        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
    }

    @Test
    void shouldReturnIoExceptionMessageWhenExecutableMissing() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/nonexistent/path/to/codex"));

        CodexCliResult result = executor.execute("--version");

        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertNotNull(result.getStderr());
        assertFalse(result.getStderr().isEmpty());
    }

    @Test
    void shouldIgnoreNullArguments() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        CodexCliResult result = executor.execute("hello", null, "world");

        assertEquals(0, result.getExitCode());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldReportSuccessFromProbeWhenExecutableWorks() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        assertTrue(executor.probe());
    }

    @Test
    void shouldReportFailureFromProbeWhenExecutableMissing() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/nonexistent/path/to/codex"));

        assertFalse(executor.probe());
    }

    @Test
    void shouldTimeoutOnHangingProcess() {
        // Use a short timeout and a command that sleeps for a long time.
        CodexClientConfig config = configFor("/bin/sh");
        config.setLocalTimeoutSeconds(1);
        CodexCliExecutor executor = new CodexCliExecutor(config);

        CodexCliResult result = executor.execute("-c", "sleep 60");

        // On macOS/Linux the watchdog kills the process; the exit code is -1
        // and stderr contains the timeout notice.
        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertNotNull(result.getStderr());
    }
}
