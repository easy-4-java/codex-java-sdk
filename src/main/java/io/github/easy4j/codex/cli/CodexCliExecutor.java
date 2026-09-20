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
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around Apache Commons {@code exec} that launches the local
 * {@code codex} CLI as a child process.
 *
 * <p>Every call to {@link #execute(String...)} performs the following steps:</p>
 * <ol>
 *   <li>Build a {@link CommandLine} rooted at {@link CodexClientConfig#getLocalExecutable()}.</li>
 *   <li>Append each non-{@code null} argument via
 *       {@link CommandLine#addArgument(String)} &mdash; the Apache Commons
 *       implementation automatically quotes arguments containing whitespace.</li>
 *   <li>Capture stdout and stderr into in-memory buffers.</li>
 *   <li>Run the process under an {@link ExecuteWatchdog} whose timeout is
 *       derived from {@link CodexClientConfig#getLocalTimeoutSeconds()}.</li>
 *   <li>Return a {@link CodexCliResult}.</li>
 * </ol>
 *
 * <p>The class is intentionally synchronous and stateless (apart from the
 * injected configuration) so it can be safely shared between threads and
 * pooled by higher-level components.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexCliResult
 */
public class CodexCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(CodexCliExecutor.class);

    private final CodexClientConfig config;

    /**
     * Creates a new executor bound to the given configuration.
     *
     * @param config the runtime configuration providing the executable path,
     *               timeout, and probe-timeout settings; must not be {@code null}.
     */
    public CodexCliExecutor(CodexClientConfig config) {
        this.config = config;
    }

    /**
     * Runs the {@code codex} executable with the given CLI arguments.
     *
     * <p>Arguments are appended verbatim using Apache Commons {@code exec},
     * which quotes any value that contains whitespace. {@code null} entries in
     * {@code args} are skipped silently to make varargs usage easier.</p>
     *
     * <p>Failure modes:</p>
     * <ul>
     *   <li>Process timeout &mdash; {@link CodexCliResult#isTimeout()} returns
     *       {@code true}; exit code is {@code -1}; stderr contains the timeout
     *       notice.</li>
     *   <li>Non-zero process exit &mdash; the real exit code is preserved in
     *       {@link CodexCliResult#getExitCode()}, and both captured streams are
     *       returned as-is ({@link CodexCliResult#isSuccess()} is simply
     *       {@code exitCode == 0}).</li>
     *   <li>IOException (missing executable, permission denied, etc.) &mdash;
     *       the {@link IOException#getMessage()} is captured in
     *       {@link CodexCliResult#getStderr()} and the exit code is {@code -1}.</li>
     * </ul>
     *
     * @param args CLI arguments to pass to the {@code codex} binary.
     * @return a {@link CodexCliResult} describing the outcome; never {@code null}.
     */
    public CodexCliResult execute(String... args) {
        return runProcess(null, config.getLocalTimeoutSeconds() * 1000L, args);
    }

    /**
     * Runs the {@code codex} executable with the given CLI arguments, feeding
     * {@code stdin} to the child process.
     *
     * <p>Used by commands that read their payload from standard input, such as
     * {@code codex login --with-api-key}. A {@code null} or empty {@code stdin}
     * behaves exactly like {@link #execute(String...)} &mdash; the child
     * inherits no pipe content. Failure modes are identical to the varargs
     * overload.</p>
     *
     * @param stdin optional text piped to the child process's standard input.
     * @param args  CLI arguments to pass to the {@code codex} binary.
     * @return a {@link CodexCliResult} describing the outcome; never {@code null}.
     */
    public CodexCliResult executeWithStdin(String stdin, String... args) {
        return runProcess(stdin, config.getLocalTimeoutSeconds() * 1000L, args);
    }

    private CodexCliResult executeWithTimeoutSeconds(int timeoutSeconds, String... args) {
        return runProcess(null, timeoutSeconds * 1000L, args);
    }

    private CodexCliResult runProcess(String stdin, long timeoutMs, String... args) {
        CommandLine cmd = CommandLine.parse(config.getLocalExecutable());
        for (String arg : args) {
            if (arg != null) {
                // handleQuoting=false: the child is spawned via exec(argv), not
                // a shell — commons-exec's default quoting would embed literal
                // double quotes inside arguments containing spaces (prompts,
                // config overrides, paths), corrupting them on arrival.
                cmd.addArgument(arg, false);
            }
        }

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        // Always hand the child a (possibly empty) stdin pipe that closes
        // right after the payload: consumers like `codex login --with-api-key`
        // read to EOF, and a closed pipe cannot race the input pump.
        byte[] stdinBytes = stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8);
        executor.setStreamHandler(new org.apache.commons.exec.PumpStreamHandler(stdout, stderr,
                new ByteArrayInputStream(stdinBytes)));

        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);

        long startNanos = System.nanoTime();
        try {
            int exitCode = executor.execute(cmd);
            String out = stdout.toString(StandardCharsets.UTF_8).trim();
            String err = stderr.toString(StandardCharsets.UTF_8).trim();
            log.debug("codex CLI executed: exitCode={}, stdout.len={}", exitCode, out.length());
            if (watchdog.killedProcess()) {
                return new CodexCliResult(-1, out, "codex CLI timed out after " + timeoutMs + " ms\n" + err);
            }
            return new CodexCliResult(exitCode, out, err);
        } catch (ExecuteException e) {
            // commons-exec throws ExecuteException for EVERY non-zero exit
            // (and for watchdog kills). The stream pumps are joined before it
            // is thrown, so both buffers are complete — surface them together
            // with the real exit code instead of discarding the output. The
            // deadline check makes the timeout verdict race-free even when
            // {@code watchdog.killedProcess()} has not observed the kill yet.
            String out = stdout.toString(StandardCharsets.UTF_8).trim();
            String err = stderr.toString(StandardCharsets.UTF_8).trim();
            boolean timedOut = watchdog.killedProcess()
                    || System.nanoTime() - startNanos >= timeoutMs * 1_000_000L;
            if (timedOut) {
                return new CodexCliResult(-1, out, "codex CLI timed out after " + timeoutMs + " ms\n" + err);
            }
            log.debug("codex CLI failed: exitCode={}, stdout.len={}, stderr.len={}",
                    e.getExitValue(), out.length(), err.length());
            return new CodexCliResult(e.getExitValue(), out, err);
        } catch (IOException e) {
            return new CodexCliResult(-1, "", e.getMessage());
        }
    }

    /**
     * Lightweight reachability probe used by {@code CodexClient#isAvailable()}.
     *
     * <p>Runs {@code codex --version} with the configured timeout and returns
     * {@code true} only if the process exits with status {@code 0}. Any
     * exception (missing executable, non-zero exit, timeout) is swallowed and
     * reported as {@code false} so callers can use the result without a
     * try/catch block.</p>
     *
     * @return {@code true} if the local CLI is reachable and reports a version,
     *         {@code false} otherwise.
     */
    public boolean probe() {
        try {
            CodexCliResult result = executeWithTimeoutSeconds(config.getLocalProbeTimeoutSeconds(), "--version");
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
}
