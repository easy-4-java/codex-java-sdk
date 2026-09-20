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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

        assertFalse(result.isSuccess());
    }

    @Test
    void shouldPreserveRealExitCodeAndStreamsOnNonZeroExit() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/sh"));

        CodexCliResult result = executor.execute("-c", "echo out-marker; echo err-marker 1>&2; exit 7");

        assertEquals(7, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getStdout().contains("out-marker"), "stdout must survive a non-zero exit");
        assertTrue(result.getStderr().contains("err-marker"), "stderr must survive a non-zero exit");
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
    void shouldPassArgumentsRawWithoutEmbeddedQuotes() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        CodexCliResult result = executor.execute("Write a failing test", "-c", "key=some value");

        assertEquals("Write a failing test -c key=some value", result.getStdout(),
                "multi-word arguments must arrive without embedded literal quotes");
    }

    @Test
    void shouldDecodeChildOutputAsUtf8() {
        // The CLIs emit UTF-8 regardless of platform; decoding with the
        // platform default charset would mojibake on GBK-default Windows.
        // You = \344\275\240, Hao = \345\245\275 (POSIX printf octal escapes).
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/sh"));

        CodexCliResult result = executor.executeWithStdin(null, "-c", "printf '\\344\\275\\240\\345\\245\\275'");

        assertEquals("你好", result.getStdout(),
                "child output must be decoded as UTF-8, not the platform default charset");
    }

    @Test
    void shouldIgnoreNullArguments() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        CodexCliResult result = executor.execute("hello", null, "world");

        assertEquals(0, result.getExitCode());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldFeedStdinToChildProcess() {
        // `cat` with no file arguments echoes its standard input verbatim,
        // which is how `codex login --with-api-key` consumes the key.
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/cat"));

        CodexCliResult result = executor.executeWithStdin("secret-api-key");

        assertEquals(0, result.getExitCode());
        assertEquals("secret-api-key", result.getStdout());
    }

    @Test
    void shouldExecuteWithoutStdinAsBefore() {
        CodexCliExecutor executor = new CodexCliExecutor(configFor("/bin/echo"));

        assertEquals("plain", executor.executeWithStdin(null, "plain").getStdout());
        assertEquals("plain", executor.executeWithStdin("", "plain").getStdout());
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
    void shouldUseProbeTimeoutWithoutChangingNormalCommandTimeout() throws Exception {
        Path script = Files.createTempFile("slow-codex-", ".sh");
        Files.write(script, Arrays.asList(
                "#!/bin/sh",
                "sleep 2",
                "echo codex-test"
        ), StandardCharsets.UTF_8);
        assertTrue(script.toFile().setExecutable(true));

        CodexClientConfig config = configFor(script.toAbsolutePath().toString());
        config.setLocalProbeTimeoutSeconds(1);
        config.setLocalTimeoutSeconds(5);
        CodexCliExecutor executor = new CodexCliExecutor(config);

        long probeStarted = System.nanoTime();
        assertFalse(executor.probe());
        long probeElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - probeStarted);
        assertTrue(probeElapsedMs < 3_500,
                "probe must use localProbeTimeoutSeconds instead of localTimeoutSeconds");

        long executeStarted = System.nanoTime();
        CodexCliResult normal = executor.execute("--version");
        long executeElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - executeStarted);
        assertTrue(normal.isSuccess(), "normal command must still use localTimeoutSeconds");
        assertTrue(executeElapsedMs >= 1_500,
                "normal command should be allowed to outlive the probe timeout");

        Files.deleteIfExists(script);
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
