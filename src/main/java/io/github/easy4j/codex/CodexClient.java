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
package io.github.easy4j.codex;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.easy4j.codex.cli.CodexCli;
import io.github.easy4j.codex.cli.CodexCliExecutor;
import io.github.easy4j.codex.cli.CodexCliResult;
import io.github.easy4j.codex.model.CodexDoctorReport;
import io.github.easy4j.codex.model.CodexEvent;
import io.github.easy4j.codex.model.CodexSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * High-level Java facade that wraps every local {@code codex} CLI invocation
 * behind ergonomic, strongly-typed methods.
 *
 * <p>This class is the recommended entry point for application code. It owns
 * a single {@link CodexClientConfig} and a single {@link CodexCli}, forwarding
 * the configured defaults to every call so that callers only need to supply
 * the call-specific overrides (e.g. the prompt or output file).</p>
 *
 * <h3>Session management</h3>
 * <p>Codex persists sessions as files under {@code ~/.codex/} and exposes
 * session lifecycle through the {@code resume} / {@code fork} / {@code archive}
 * sub-commands. This SDK models each of those sub-commands as a Java method
 * so application code does not have to reason about CLI spelling.</p>
 *
 * <h3>JSON-Lines parsing</h3>
 * <p>{@link #execAndParse(String)} runs {@code codex exec --json <prompt>} and
 * decodes the standard output into a {@code List<CodexEvent>} using a private
 * Jackson {@link ObjectMapper} that ignores unknown properties.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexClientConfig
 * @see CodexCli
 * @see CodexCliResult
 */
public class CodexClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CodexClient.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final CodexClientConfig config;
    private final CodexCli cli;

    /**
     * Creates a new client backed by the given configuration. A default
     * {@link CodexCli} and {@link CodexCliExecutor} are constructed
     * automatically.
     *
     * @param config runtime configuration; must not be {@code null}.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public CodexClient(CodexClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.cli = new CodexCli(new CodexCliExecutor(config));
    }

    /**
     * Creates a new client that delegates to the supplied {@link CodexCli}.
     *
     * <p>This constructor exists primarily for testing &mdash; it lets a
     * caller substitute a {@link CodexCli} backed by a mocked executor while
     * still using the default behaviour of the surrounding facade.</p>
     *
     * @param config runtime configuration; must not be {@code null}.
     * @param cli    the CLI facade to delegate to; must not be {@code null}.
     * @throws NullPointerException if either argument is {@code null}.
     */
    public CodexClient(CodexClientConfig config, CodexCli cli) {
        this.config = Objects.requireNonNull(config, "config");
        this.cli = Objects.requireNonNull(cli, "cli");
    }

    // ============================================================
    // Basic info
    // ============================================================

    /**
     * Runs {@code codex --version}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult version() { return cli.version(); }

    /**
     * Runs {@code codex --help}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult help() { return cli.help(); }

    // ============================================================
    // exec — non-interactive execution
    // ============================================================

    /**
     * Sends {@code prompt} and blocks until the {@code codex exec} call
     * returns, propagating every default from the client configuration.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(String prompt) {
        CodexCli.ExecOptions opts = defaultOptions(prompt);
        return cli.executor().execute(opts.toArgs());
    }

    /**
     * Sends {@code prompt} using the specified {@code model} and enables
     * {@code --json} output so the result can be parsed into events.
     *
     * @param prompt the prompt to feed to the agent.
     * @param model  the model identifier to pin for this call.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(String prompt, String model) {
        return cli.exec(new CodexCli.ExecOptions(prompt).model(model).json(true));
    }

    /**
     * Runs {@code codex exec} with the supplied, fully-configured options.
     *
     * @param opts execution options; must not be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(CodexCli.ExecOptions opts) {
        return cli.executor().execute(opts.toArgs());
    }

    /**
     * Convenience wrapper that runs {@code exec(prompt)} and decodes the
     * standard output into a list of {@link CodexEvent} instances.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the parsed JSON-Lines events; an empty list when the output is
     *         empty or unparseable.
     */
    public List<CodexEvent> execAndParse(String prompt) {
        CodexCliResult result = exec(prompt);
        return parseJsonlOutput(result.getStdout());
    }

    /**
     * Runs {@code codex exec} with the {@code -C} flag pointed at the given
     * directory.
     *
     * @param workingDir the working directory passed via {@code -C}.
     * @param prompt     the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execInDir(String workingDir, String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).workingDir(workingDir));
    }

    /**
     * Runs {@code codex exec --ephemeral <prompt>} &mdash; the session is not
     * persisted to disk after the call completes.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execEphemeral(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).ephemeral(true));
    }

    /**
     * Runs {@code codex exec --search <prompt>} so the agent can call the
     * web-search tool during execution.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithSearch(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).search(true));
    }

    /**
     * Runs {@code codex exec -o <outputFile> <prompt>}.
     *
     * @param prompt     the prompt to feed to the agent.
     * @param outputFile destination file for the final message.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execToFile(String prompt, String outputFile) {
        return cli.exec(new CodexCli.ExecOptions(prompt).outputFile(outputFile));
    }

    /**
     * Runs {@code codex exec --output-schema <schema> <prompt>}.
     *
     * @param prompt       the prompt to feed to the agent.
     * @param outputSchema JSON Schema path describing the expected structured output.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithSchema(String prompt, String outputSchema) {
        return cli.exec(new CodexCli.ExecOptions(prompt).outputSchema(outputSchema));
    }

    /**
     * Runs {@code codex exec --image <imagePath> <prompt>}.
     *
     * @param prompt    the prompt to feed to the agent.
     * @param imagePath path to an image attachment forwarded to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithImage(String prompt, String imagePath) {
        return cli.exec(new CodexCli.ExecOptions(prompt).image(imagePath));
    }

    /**
     * Runs {@code codex exec} with one or more {@code -c key=value} overrides.
     *
     * @param prompt          the prompt to feed to the agent.
     * @param configOverrides each element becomes a separate {@code -c} flag.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithConfigOverrides(String prompt, String... configOverrides) {
        return cli.exec(new CodexCli.ExecOptions(prompt).configOverrides(configOverrides));
    }

    /**
     * Runs {@code codex exec --dangerously-bypass-approvals-and-sandbox
     * <prompt>}. <strong>Use with extreme caution</strong>.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execDangerously(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).dangerouslyBypassApprovalsAndSandbox(true));
    }

    /**
     * Runs {@code codex exec --dangerously-bypass-hook-trust <prompt>}.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execBypassHookTrust(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).dangerouslyBypassHookTrust(true));
    }

    /**
     * Runs {@code codex exec --enable <feature>... <prompt>}.
     *
     * @param prompt   the prompt to feed to the agent.
     * @param features feature flags to enable.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithEnable(String prompt, String... features) {
        return cli.exec(new CodexCli.ExecOptions(prompt).enable(features));
    }

    /**
     * Runs {@code codex exec --disable <feature>... <prompt>}.
     *
     * @param prompt   the prompt to feed to the agent.
     * @param features feature flags to disable.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execWithDisable(String prompt, String... features) {
        return cli.exec(new CodexCli.ExecOptions(prompt).disable(features));
    }

    // ============================================================
    // Interactive session
    // ============================================================

    /**
     * Starts an interactive session with no initial prompt.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult startSession() {
        return cli.startInteractive();
    }

    /**
     * Starts an interactive session seeded with the given prompt.
     *
     * @param prompt the opening prompt; may be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult startSession(String prompt) {
        return cli.startInteractive(prompt);
    }

    /**
     * Starts an interactive session configured with the supplied global
     * options and seeded with the given prompt.
     *
     * @param opts   the global options to apply before the prompt.
     * @param prompt the opening prompt; may be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult startSession(CodexCli.GlobalOptions opts, String prompt) {
        return cli.startInteractive(opts, prompt);
    }

    // ============================================================
    // exec resume — resuming non-interactive sessions
    // ============================================================

    /**
     * Resumes the named session and appends the supplied prompt.
     *
     * @param sessionId the session identifier to resume.
     * @param prompt    the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResume(String sessionId, String prompt) {
        return cli.execResume(sessionId, prompt);
    }

    /**
     * Resumes the most recent non-interactive session and appends the
     * supplied prompt.
     *
     * @param prompt the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLast(String prompt) {
        return cli.execResumeLast(prompt);
    }

    /**
     * Resumes the most recent session without appending any prompt.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLast() {
        return cli.execResumeLast();
    }

    /**
     * Resumes the most recent session, appends the supplied prompt, and
     * writes the final message to {@code outputFile}.
     *
     * @param prompt     the prompt to append.
     * @param outputFile destination file for the final message.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLastToFile(String prompt, String outputFile) {
        return cli.execResumeLast(prompt, outputFile);
    }

    /**
     * Resumes the named session together with its entire history and appends
     * the supplied prompt.
     *
     * @param sessionId the session identifier to resume.
     * @param prompt    the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeAll(String sessionId, String prompt) {
        return cli.execResumeAll(sessionId, prompt);
    }

    // ============================================================
    // review — code review
    // ============================================================

    /**
     * Runs {@code codex review}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult review() { return cli.review(); }

    /**
     * Runs {@code codex review --uncommitted}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewUncommitted() { return cli.reviewUncommitted(); }

    /**
     * Runs {@code codex review --base <branch>}.
     *
     * @param branch the branch used as the review baseline.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewBase(String branch) { return cli.reviewBase(branch); }

    /**
     * Runs {@code codex review --commit <sha>}.
     *
     * @param sha the commit SHA used as the review baseline.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewCommit(String sha) { return cli.reviewCommit(sha); }

    /**
     * Runs {@code codex review --title <title> <prompt>}.
     *
     * @param prompt the review prompt.
     * @param title  the human-readable review title.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult review(String prompt, String title) {
        return cli.review(prompt, title);
    }

    // ============================================================
    // Session lifecycle
    // ============================================================

    /**
     * Resumes an interactive session by id.
     *
     * @param sessionId the session identifier to resume.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeSession(String sessionId) {
        return cli.resume(sessionId);
    }

    /**
     * Resumes an interactive session by id and appends the supplied prompt.
     *
     * @param sessionId the session identifier to resume.
     * @param prompt    the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeSession(String sessionId, String prompt) {
        return cli.resume(sessionId, prompt);
    }

    /**
     * Resumes the most recent interactive session.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeLastSession() {
        return cli.resumeLast();
    }

    /**
     * Resumes the most recent interactive session and appends the supplied
     * prompt.
     *
     * @param prompt the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeLastSession(String prompt) {
        return cli.resumeLast(prompt);
    }

    /**
     * Lists every session across all directories.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeAllSessions() {
        return cli.resumeAll();
    }

    /**
     * Resumes the most recent session, including non-interactive ones.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeIncludeNonInteractive() {
        return cli.resumeIncludeNonInteractive();
    }

    /**
     * Forks the named session.
     *
     * @param sessionId the session identifier to fork.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkSession(String sessionId) {
        return cli.fork(sessionId);
    }

    /**
     * Forks the named session and appends the supplied prompt.
     *
     * @param sessionId the session identifier to fork.
     * @param prompt    the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkSession(String sessionId, String prompt) {
        return cli.fork(sessionId, prompt);
    }

    /**
     * Forks the most recent session.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkLastSession() {
        return cli.forkLast();
    }

    /**
     * Forks the most recent session and appends the supplied prompt.
     *
     * @param prompt the prompt to append.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkLastSession(String prompt) {
        return cli.forkLast(prompt);
    }

    /**
     * Forks the named session, scanning the entire session index.
     *
     * @param sessionId the session identifier to fork.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkAllSessions(String sessionId) {
        return cli.forkAll(sessionId);
    }

    /**
     * Archives the named session.
     *
     * @param sessionId the session identifier to archive.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult archiveSession(String sessionId) {
        return cli.archive(sessionId);
    }

    /**
     * Unarchives the named session.
     *
     * @param sessionId the session identifier to unarchive.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult unarchiveSession(String sessionId) {
        return cli.unarchive(sessionId);
    }

    // ============================================================
    // apply
    // ============================================================

    /**
     * Applies the diff produced by the given task to the working tree.
     *
     * @param taskId the task identifier.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult apply(String taskId) {
        return cli.apply(taskId);
    }

    // ============================================================
    // auth
    // ============================================================

    /**
     * Runs {@code codex login}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult login() { return cli.login(); }

    /**
     * Runs {@code codex logout}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult logout() { return cli.logout(); }

    // ============================================================
    // mcp
    // ============================================================

    /**
     * Runs {@code codex mcp list}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpList() { return cli.mcpList(); }

    /**
     * Runs {@code codex mcp get <name>}.
     *
     * @param name the MCP server name.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpGet(String name) { return cli.mcpGet(name); }

    /**
     * Runs {@code codex mcp add <name> <command> [args...]}.
     *
     * @param name    the MCP server name.
     * @param command the launcher command.
     * @param args    optional extra arguments.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpAdd(String name, String command, String... args) { return cli.mcpAdd(name, command, args); }

    /**
     * Runs {@code codex mcp remove <name>}.
     *
     * @param name the MCP server name.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpRemove(String name) { return cli.mcpRemove(name); }

    /**
     * Runs {@code codex mcp login <name>}.
     *
     * @param name the MCP server name.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpLogin(String name) { return cli.mcpLogin(name); }

    /**
     * Runs {@code codex mcp logout <name>}.
     *
     * @param name the MCP server name.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpLogout(String name) { return cli.mcpLogout(name); }

    // ============================================================
    // doctor
    // ============================================================

    /**
     * Runs {@code codex doctor}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctor() { return cli.doctor(); }

    /**
     * Runs {@code codex doctor --json}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctorJson() { return cli.doctorJson(); }

    /**
     * Runs {@code codex doctor --summary}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctorSummary() { return cli.doctorSummary(); }

    // ============================================================
    // Other commands
    // ============================================================

    /**
     * Runs {@code codex update}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult update() { return cli.update(); }

    /**
     * Runs {@code codex completion <shell>}.
     *
     * @param shell the target shell name.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult completion(String shell) { return cli.completion(shell); }

    /**
     * Runs {@code codex features}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult features() { return cli.features(); }

    /**
     * Runs {@code codex mcp-server}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpServer() { return cli.mcpServer(); }

    /**
     * Runs {@code codex app}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult app() { return cli.app(); }

    /**
     * Runs {@code codex sandbox <command...>}.
     *
     * @param command the shell command to execute inside the sandbox.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult sandbox(String... command) { return cli.sandbox(command); }

    /**
     * Runs {@code codex sandbox --permissions-profile <profile> <command...>}.
     *
     * @param profile the permissions profile name.
     * @param command the shell command to execute inside the sandbox.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult sandbox(String profile, String... command) { return cli.sandbox(profile, command); }

    /**
     * Runs {@code codex debug <args...>}.
     *
     * @param args arguments forwarded to the debug sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult debug(String... args) { return cli.debug(args); }

    /**
     * Runs {@code codex cloud <args...>}.
     *
     * @param args arguments forwarded to the cloud sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult cloud(String... args) { return cli.cloud(args); }

    /**
     * Runs {@code codex app-server <args...>}.
     *
     * @param args arguments forwarded to the app-server sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult appServer(String... args) { return cli.appServer(args); }

    /**
     * Runs {@code codex remote-control <args...>}.
     *
     * @param args arguments forwarded to the remote-control sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult remoteControl(String... args) { return cli.remoteControl(args); }

    /**
     * Runs {@code codex exec-server <args...>}.
     *
     * @param args arguments forwarded to the exec-server sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execServer(String... args) { return cli.execServer(args); }

    /**
     * Runs {@code codex plugin <args...>}.
     *
     * @param args arguments forwarded to the plugin sub-command.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult plugin(String... args) { return cli.plugin(args); }

    /**
     * Parses the JSON document produced by {@code codex doctor --json}.
     *
     * <p>This helper invokes {@link CodexCli#doctorJson()} and decodes the
     * standard output as a {@link CodexDoctorReport}. If the call failed or
     * the output is empty, {@code null} is returned.</p>
     *
     * @return the decoded report, or {@code null} if the CLI failed or the
     *         payload could not be parsed.
     */
    public CodexDoctorReport parseDoctorReport() {
        CodexCliResult result = cli.doctorJson();
        if (!result.isSuccess() || result.getStdout().isEmpty()) return null;
        try {
            return MAPPER.readValue(result.getStdout(), CodexDoctorReport.class);
        } catch (Exception e) {
            log.debug("Failed to parse doctor report", e);
            return null;
        }
    }

    /**
     * Parses the JSON array emitted by session-listing commands such as
     * {@code codex resume --all}.
     *
     * @param result the CLI result of a session-listing command.
     * @return the decoded list, or an empty list if the call failed or the
     *         payload could not be parsed.
     */
    public List<CodexSession> parseSessionList(CodexCliResult result) {
        if (!result.isSuccess() || result.getStdout().isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(result.getStdout(), new TypeReference<List<CodexSession>>() {});
        } catch (Exception e) {
            log.debug("Failed to parse session list", e);
            return Collections.emptyList();
        }
    }

    /**
     * Executes the underlying CLI with arbitrary arguments.
     *
     * <p>Useful as an escape hatch for callers that need to invoke a CLI
     * flag combination not yet wrapped by a dedicated method.</p>
     *
     * @param args the raw argument list.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public CodexCliResult execute(String... args) {
        return cli.executor().execute(args);
    }

    // ============================================================
    // CLI instance
    // ============================================================

    /**
     * Returns the underlying {@link CodexCli} for advanced callers.
     *
     * @return the CLI facade backing this client; never {@code null}.
     */
    public CodexCli cli() { return cli; }

    /**
     * Returns the runtime configuration used by this client.
     *
     * @return the configuration; never {@code null}.
     */
    public CodexClientConfig getConfig() { return config; }

    // ============================================================
    // Utility helpers
    // ============================================================

    /**
     * Builds an {@link CodexCli.ExecOptions} seeded with every default from
     * the client configuration that is relevant to {@code codex exec}.
     *
     * @param prompt the prompt to embed in the resulting options.
     * @return a fresh options instance; never {@code null}.
     */
    private CodexCli.ExecOptions defaultOptions(String prompt) {
        CodexCli.ExecOptions opts = new CodexCli.ExecOptions(prompt).json(true);
        if (config.getDefaultModel() != null) opts.model(config.getDefaultModel());
        if (config.getDefaultSandbox() != null) opts.sandbox(config.getDefaultSandbox());
        if (config.getDefaultApprovalPolicy() != null) opts.approvalPolicy(config.getDefaultApprovalPolicy());
        if (config.getDefaultProfile() != null) opts.profile(config.getDefaultProfile());
        if (config.getWorkingDir() != null) opts.workingDir(config.getWorkingDir());
        if (config.getAddDir() != null) opts.addDir(config.getAddDir());
        if (config.getOutputFile() != null) opts.outputFile(config.getOutputFile());
        if (config.getOutputSchema() != null) opts.outputSchema(config.getOutputSchema());
        if (config.isEphemeral()) opts.ephemeral(true);
        if (config.isSkipGitRepoCheck()) opts.skipGitRepoCheck(true);
        if (config.isOssProvider()) opts.oss(true);
        if (config.getLocalProvider() != null) opts.localProvider(config.getLocalProvider());
        if (config.isSearch()) opts.search(true);
        if (config.getImage() != null) opts.image(config.getImage());
        if (config.getConfigOverrides() != null) opts.configOverrides(config.getConfigOverrides());
        if (config.isDangerouslyBypassApprovalsAndSandbox()) opts.dangerouslyBypassApprovalsAndSandbox(true);
        if (config.isDangerouslyBypassHookTrust()) opts.dangerouslyBypassHookTrust(true);
        if (config.isStrictConfig()) opts.strictConfig(true);
        if (config.getEnable() != null) opts.enable(config.getEnable());
        if (config.getDisable() != null) opts.disable(config.getDisable());
        return opts;
    }

    /**
     * Decodes a JSON-Lines blob into a list of {@link CodexEvent}s.
     *
     * <p>Blank lines and lines that fail to parse are silently skipped; the
     * returned list contains only successfully-decoded events in their
     * original order.</p>
     *
     * @param stdout the trimmed CLI standard output.
     * @return the decoded events; never {@code null}.
     */
    private List<CodexEvent> parseJsonlOutput(String stdout) {
        List<CodexEvent> events = new ArrayList<>();
        if (stdout == null || stdout.isEmpty()) return events;
        for (String line : stdout.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                CodexEvent event = MAPPER.readValue(line, CodexEvent.class);
                events.add(event);
            } catch (Exception e) {
                log.debug("Failed to parse JSONL line: {}", line, e);
            }
        }
        return events;
    }

    /**
     * Closes this client. The default implementation is a no-op because the
     * underlying {@link CodexCliExecutor} does not hold any long-lived
     * resources.
     */
    @Override
    public void close() {
    }
}
