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
import org.apache.commons.exec.ExecuteWatchdog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
     *   <li>IOException (missing executable, permission denied, etc.) &mdash;
     *       the {@link IOException#getMessage()} is captured in
     *       {@link CodexCliResult#getStderr()} and the exit code is {@code -1}.</li>
     * </ul>
     *
     * @param args CLI arguments to pass to the {@code codex} binary.
     * @return a {@link CodexCliResult} describing the outcome; never {@code null}.
     */
    public CodexCliResult execute(String... args) {
        CommandLine cmd = CommandLine.parse(config.getLocalExecutable());
        for (String arg : args) {
            if (arg != null) {
                cmd.addArgument(arg);
            }
        }

        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        executor.setStreamHandler(new org.apache.commons.exec.PumpStreamHandler(stdout, stderr));

        long timeoutMs = config.getLocalTimeoutSeconds() * 1000L;
        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);

        try {
            int exitCode = executor.execute(cmd);
            String out = stdout.toString().trim();
            String err = stderr.toString().trim();
            log.debug("codex CLI executed: exitCode={}, stdout.len={}", exitCode, out.length());
            if (watchdog.killedProcess()) {
                return new CodexCliResult(-1, out, "codex CLI timed out after " + timeoutMs + " ms\n" + err);
            }
            return new CodexCliResult(exitCode, out, err);
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
            CodexCliResult result = execute("--version");
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
}
