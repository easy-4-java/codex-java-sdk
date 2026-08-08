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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type-safe facade over every command exposed by the local {@code codex} CLI.
 *
 * <p>This class is the lowest-level Java abstraction in the SDK: each public
 * method corresponds to exactly one CLI invocation. Higher-level components
 * such as {@link io.github.easy4j.codex.CodexClient} compose these primitives
 * to deliver ergonomic Java APIs.</p>
 *
 * <h3>Session management (core feature)</h3>
 * <ul>
 *   <li>{@link #resume(String)} &mdash; resume an interactive session.</li>
 *   <li>{@link #resumeLast()} &mdash; resume the most recent session.</li>
 *   <li>{@link #fork(String)} &mdash; fork an existing session into a new branch.</li>
 *   <li>{@link #archive(String)} / {@link #unarchive(String)} &mdash; move sessions in and out of the archive.</li>
 *   <li>{@link #execResume(String)} &mdash; resume a non-interactive (one-shot) session.</li>
 * </ul>
 *
 * <p>The two inner option builders {@link ExecOptions} and
 * {@link GlobalOptions} collect every flag supported by the CLI and emit them
 * via {@code toArgs()}. They are the only classes in the SDK that know the
 * exact CLI flag spelling, isolating that knowledge from the rest of the code
 * base.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see <a href="https://github.com/openai/codex">Codex CLI</a>
 * @see CodexCliExecutor
 */
public class CodexCli {

    private static final Logger log = LoggerFactory.getLogger(CodexCli.class);

    private final CodexCliExecutor executor;

    /**
     * Creates a new {@code CodexCli} that delegates process execution to the
     * provided executor.
     *
     * @param executor the subprocess executor; must not be {@code null}.
     */
    public CodexCli(CodexCliExecutor executor) {
        this.executor = executor;
    }

    /**
     * Returns the underlying executor for callers that need direct access.
     *
     * @return the executor backing this CLI instance; never {@code null}.
     */
    public CodexCliExecutor executor() {
        return executor;
    }

    // ============================================================
    // 全局
    // ============================================================

    /**
     * Runs {@code codex --version}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult version() {
        return executor.execute("--version");
    }

    /**
     * Runs {@code codex --help}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult help() {
        return executor.execute("--help");
    }

    // ============================================================
    // 交互式会话（全局选项 + 可选 prompt）
    // ============================================================

    /**
     * Runs {@code codex [prompt]} &mdash; launches an interactive session with
     * the supplied initial prompt.
     *
     * @param prompt the opening prompt to feed to the TUI; may be {@code null}
     *               when the user should be prompted at startup.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult startInteractive(String prompt) {
        return executor.execute(prompt);
    }

    /**
     * Runs {@code codex} with no arguments &mdash; launches an interactive
     * session with an empty prompt.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult startInteractive() {
        return executor.execute();
    }

    /**
     * Runs {@code codex [global options] [prompt]} &mdash; launches an
     * interactive session pre-configured with the supplied global options.
     *
     * @param opts   global options applied before the optional prompt; must not be {@code null}.
     * @param prompt opening prompt; may be {@code null}.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult startInteractive(GlobalOptions opts, String prompt) {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, opts.toArgs());
        if (prompt != null) args.add(prompt);
        return executor.execute(args.toArray(new String[0]));
    }

    // ============================================================
    // exec — 非交互执行
    // ============================================================

    /**
     * Runs {@code codex exec <prompt>}.
     *
     * @param prompt the prompt to feed to the agent.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(String prompt) {
        return executor.execute("exec", prompt);
    }

    /**
     * Runs {@code codex exec --model <model> <prompt>}.
     *
     * @param prompt the prompt to feed to the agent.
     * @param model  the model identifier to pin for this run.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(String prompt, String model) {
        return executor.execute("exec", "--model", model, prompt);
    }

    /**
     * Runs {@code codex exec --model <model> --sandbox <mode> [--json] <prompt>}.
     *
     * @param prompt  the prompt to feed to the agent.
     * @param model   model identifier, or {@code null} to omit {@code --model}.
     * @param sandbox sandbox mode, or {@code null} to omit {@code --sandbox}.
     * @param json    whether to enable {@code --json} output.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(String prompt, String model, String sandbox, boolean json) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        if (model != null) { args.add("--model"); args.add(model); }
        if (sandbox != null) { args.add("--sandbox"); args.add(sandbox); }
        if (json) { args.add("--json"); }
        args.add(prompt);
        return executor.execute(args.toArray(new String[0]));
    }

    /**
     * Runs {@code codex exec} using every flag carried by the supplied
     * {@link ExecOptions}.
     *
     * @param opts assembled execution options; must not be {@code null}.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult exec(ExecOptions opts) {
        return executor.execute(opts.toArgs());
    }

    /**
     * Runs {@code codex exec -C <dir> <prompt>}.
     *
     * @param workingDir the working directory passed via {@code -C}.
     * @param prompt     the prompt to feed to the agent.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execInDir(String workingDir, String prompt) {
        return executor.execute("exec", "-C", workingDir, prompt);
    }

    // ============================================================
    // exec resume — 恢复非交互会话
    // ============================================================

    /**
     * Runs {@code codex exec resume <sessionId> <prompt>}.
     *
     * @param sessionId the session to resume.
     * @param prompt    the prompt to append to the resumed session.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResume(String sessionId, String prompt) {
        return executor.execute("exec", "resume", sessionId, prompt);
    }

    /**
     * Runs {@code codex exec resume --last <prompt>}.
     *
     * @param prompt the prompt to append to the last non-interactive session.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLast(String prompt) {
        return executor.execute("exec", "resume", "--last", prompt);
    }

    /**
     * Runs {@code codex exec resume --last} with no additional prompt.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLast() {
        return executor.execute("exec", "resume", "--last");
    }

    /**
     * Runs {@code codex exec resume --last --json -o <file> <prompt>}.
     *
     * @param prompt     the prompt to append to the last session.
     * @param outputFile path where the final message is written.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeLast(String prompt, String outputFile) {
        return executor.execute("exec", "resume", "--last", "--json", "-o", outputFile, prompt);
    }

    /**
     * Runs {@code codex exec resume --all <sessionId> <prompt>}.
     *
     * @param sessionId the session to resume, scanning all directories.
     * @param prompt    the prompt to append.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execResumeAll(String sessionId, String prompt) {
        return executor.execute("exec", "resume", "--all", sessionId, prompt);
    }

    // ============================================================
    // review — 代码审查
    // ============================================================

    /**
     * Runs {@code codex review}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult review() {
        return executor.execute("review");
    }

    /**
     * Runs {@code codex review --uncommitted}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewUncommitted() {
        return executor.execute("review", "--uncommitted");
    }

    /**
     * Runs {@code codex review --base <branch>}.
     *
     * @param baseBranch the branch used as the review baseline.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewBase(String baseBranch) {
        return executor.execute("review", "--base", baseBranch);
    }

    /**
     * Runs {@code codex review --commit <sha>}.
     *
     * @param sha the commit SHA used as the review baseline.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult reviewCommit(String sha) {
        return executor.execute("review", "--commit", sha);
    }

    /**
     * Runs {@code codex review --title <title> <prompt>}.
     *
     * @param prompt the review prompt.
     * @param title  the human-readable review title.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult review(String prompt, String title) {
        return executor.execute("review", "--title", title, prompt);
    }

    // ============================================================
    // session — 会话生命周期
    // ============================================================

    /**
     * Runs {@code codex resume [sessionId] [prompt]}.
     *
     * @param sessionId the session to resume.
     * @param prompt    the prompt to append; may be {@code null} to just resume.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resume(String sessionId, String prompt) {
        if (prompt != null) {
            return executor.execute("resume", sessionId, prompt);
        }
        return executor.execute("resume", sessionId);
    }

    /**
     * Runs {@code codex resume [sessionId]} with no additional prompt.
     *
     * @param sessionId the session to resume.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resume(String sessionId) {
        return resume(sessionId, null);
    }

    /**
     * Runs {@code codex resume --last}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeLast() {
        return executor.execute("resume", "--last");
    }

    /**
     * Runs {@code codex resume --last [prompt]}.
     *
     * @param prompt the prompt to append; may be {@code null}.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeLast(String prompt) {
        return executor.execute("resume", "--last", prompt);
    }

    /**
     * Runs {@code codex resume --all}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeAll() {
        return executor.execute("resume", "--all");
    }

    /**
     * Runs {@code codex resume --last --include-non-interactive}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resumeIncludeNonInteractive() {
        return executor.execute("resume", "--last", "--include-non-interactive");
    }

    /**
     * Runs {@code codex resume --model <model> <sessionId> [prompt]}.
     *
     * @param sessionId the session to resume.
     * @param prompt    the prompt to append; may be {@code null}.
     * @param model     the model identifier to pin.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult resume(String sessionId, String prompt, String model) {
        return executor.execute("resume", "--model", model, sessionId, prompt);
    }

    // ============================================================
    // fork — 分支会话
    // ============================================================

    /**
     * Runs {@code codex fork [sessionId] [prompt]}.
     *
     * @param sessionId the session to fork.
     * @param prompt    the prompt to append to the forked session; may be {@code null}.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult fork(String sessionId, String prompt) {
        if (prompt != null) {
            return executor.execute("fork", sessionId, prompt);
        }
        return executor.execute("fork", sessionId);
    }

    /**
     * Runs {@code codex fork <sessionId>} with no additional prompt.
     *
     * @param sessionId the session to fork.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult fork(String sessionId) {
        return fork(sessionId, null);
    }

    /**
     * Runs {@code codex fork --last}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkLast() {
        return executor.execute("fork", "--last");
    }

    /**
     * Runs {@code codex fork --last [prompt]}.
     *
     * @param prompt the prompt to append; may be {@code null}.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkLast(String prompt) {
        return executor.execute("fork", "--last", prompt);
    }

    /**
     * Runs {@code codex fork --all <sessionId>}.
     *
     * @param sessionId the session to fork from the full session index.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult forkAll(String sessionId) {
        return executor.execute("fork", "--all", sessionId);
    }

    // ============================================================
    // archive / unarchive
    // ============================================================

    /**
     * Runs {@code codex archive <sessionId>}.
     *
     * @param sessionId the session to archive.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult archive(String sessionId) {
        return executor.execute("archive", sessionId);
    }

    /**
     * Runs {@code codex unarchive <sessionId>}.
     *
     * @param sessionId the session to unarchive.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult unarchive(String sessionId) {
        return executor.execute("unarchive", sessionId);
    }

    // ============================================================
    // apply
    // ============================================================

    /**
     * Runs {@code codex apply <taskId>}.
     *
     * @param taskId the task identifier whose diff should be applied.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult apply(String taskId) {
        return executor.execute("apply", taskId);
    }

    // ============================================================
    // auth
    // ============================================================

    /**
     * Runs {@code codex login}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult login() {
        return executor.execute("login");
    }

    /**
     * Runs {@code codex logout}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult logout() {
        return executor.execute("logout");
    }

    // ============================================================
    // mcp
    // ============================================================

    /**
     * Runs {@code codex mcp list}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpList() {
        return executor.execute("mcp", "list");
    }

    /**
     * Runs {@code codex mcp get <name>}.
     *
     * @param name the MCP server name.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpGet(String name) {
        return executor.execute("mcp", "get", name);
    }

    /**
     * Runs {@code codex mcp add <name> <command> [args...]}.
     *
     * @param name    the MCP server name.
     * @param command the launcher command for the MCP server.
     * @param args    optional extra arguments forwarded to the MCP server.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpAdd(String name, String command, String... args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add("mcp"); allArgs.add("add"); allArgs.add(name); allArgs.add(command);
        for (String a : args) allArgs.add(a);
        return executor.execute(allArgs.toArray(new String[0]));
    }

    /**
     * Runs {@code codex mcp remove <name>}.
     *
     * @param name the MCP server name to remove.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpRemove(String name) {
        return executor.execute("mcp", "remove", name);
    }

    /**
     * Runs {@code codex mcp login <name>}.
     *
     * @param name the MCP server to authenticate against.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpLogin(String name) {
        return executor.execute("mcp", "login", name);
    }

    /**
     * Runs {@code codex mcp logout <name>}.
     *
     * @param name the MCP server to log out of.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpLogout(String name) {
        return executor.execute("mcp", "logout", name);
    }

    // ============================================================
    // plugin
    // ============================================================

    /**
     * Runs {@code codex plugin <args...>}.
     *
     * @param args subcommand and arguments forwarded to the plugin subsystem.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult plugin(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "plugin";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    // ============================================================
    // mcp-server
    // ============================================================

    /**
     * Runs {@code codex mcp-server}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult mcpServer() {
        return executor.execute("mcp-server");
    }

    // ============================================================
    // app-server / remote-control
    // ============================================================

    /**
     * Runs {@code codex app-server <args...>}.
     *
     * @param args arguments forwarded to the app-server sub-command.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult appServer(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "app-server";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    /**
     * Runs {@code codex remote-control <args...>}.
     *
     * @param args arguments forwarded to the remote-control sub-command.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult remoteControl(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "remote-control";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    // ============================================================
    // app / update / doctor
    // ============================================================

    /**
     * Runs {@code codex app}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult app() {
        return executor.execute("app");
    }

    /**
     * Runs {@code codex completion <shell>}.
     *
     * @param shell the target shell name (e.g. {@code bash}, {@code zsh}).
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult completion(String shell) {
        return executor.execute("completion", shell);
    }

    /**
     * Runs {@code codex update}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult update() {
        return executor.execute("update");
    }

    /**
     * Runs {@code codex doctor}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctor() {
        return executor.execute("doctor");
    }

    /**
     * Runs {@code codex doctor --json}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctorJson() {
        return executor.execute("doctor", "--json");
    }

    /**
     * Runs {@code codex doctor --summary}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult doctorSummary() {
        return executor.execute("doctor", "--summary");
    }

    // ============================================================
    // sandbox
    // ============================================================

    /**
     * Runs {@code codex sandbox <command...>}.
     *
     * @param command the shell command to execute inside the sandbox.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult sandbox(String... command) {
        String[] all = new String[command.length + 1];
        all[0] = "sandbox";
        System.arraycopy(command, 0, all, 1, command.length);
        return executor.execute(all);
    }

    /**
     * Runs {@code codex sandbox --permissions-profile <name> <command...>}.
     *
     * @param profile the named permissions profile to load.
     * @param command the shell command to execute inside the sandbox.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult sandbox(String profile, String... command) {
        List<String> all = new ArrayList<>();
        all.add("sandbox");
        all.add("--permissions-profile");
        all.add(profile);
        for (String c : command) all.add(c);
        return executor.execute(all.toArray(new String[0]));
    }

    // ============================================================
    // debug / cloud / features
    // ============================================================

    /**
     * Runs {@code codex debug <args...>}.
     *
     * @param args arguments forwarded to the debug sub-command.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult debug(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "debug";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    /**
     * Runs {@code codex cloud <args...>}.
     *
     * @param args arguments forwarded to the cloud sub-command.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult cloud(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "cloud";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    /**
     * Runs {@code codex features}.
     *
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult features() {
        return executor.execute("features");
    }

    // ============================================================
    // exec-server
    // ============================================================

    /**
     * Runs {@code codex exec-server <args...>}.
     *
     * @param args arguments forwarded to the exec-server sub-command.
     * @return the CLI invocation result; never {@code null}.
     */
    public CodexCliResult execServer(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "exec-server";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    // ============================================================
    // ExecOptions builder — 组合所有 exec 选项
    // ============================================================

    /**
     * Fluent builder that materialises every flag accepted by
     * {@code codex exec}.
     *
     * <p>{@link #toArgs()} converts the configured state into the exact
     * {@code String[]} that {@link CodexCliExecutor} expects. Unknown future
     * flags can be supplied through {@link #configOverrides(String...)} without
     * requiring changes to this class.</p>
     *
     * @author [@Loong Wan](https://github.com/loong10k)
     * @since 3.0.0
     */
    public static class ExecOptions {
        private String prompt;
        private String model;
        private String sandbox;
        private String approvalPolicy;
        private String profile;
        private String workingDir;
        private String addDir;
        private String outputFile;
        private String outputSchema;
        private boolean json = true;
        private boolean ephemeral;
        private boolean skipGitRepoCheck;
        private boolean oss;
        private String localProvider;
        private boolean search;
        private String image;
        private String[] configOverrides;
        private boolean dangerouslyBypassApprovalsAndSandbox;
        private boolean dangerouslyBypassHookTrust;
        private boolean strictConfig;
        private String[] enable;
        private String[] disable;

        /**
         * Creates a new instance bound to the given prompt.
         *
         * @param prompt the prompt to feed to the agent.
         */
        public ExecOptions(String prompt) { this.prompt = prompt; }

        /**
         * Sets the {@code --model} flag.
         *
         * @param v the model identifier (e.g. {@code gpt-5-codex}).
         * @return this builder for chaining.
         */
        public ExecOptions model(String v) { this.model = v; return this; }
        /**
         * Sets the {@code --sandbox} flag.
         *
         * @param v the sandbox mode (e.g. {@code read-only}, {@code workspace-write}).
         * @return this builder for chaining.
         */
        public ExecOptions sandbox(String v) { this.sandbox = v; return this; }
        /**
         * Sets the {@code --ask-for-approval} flag.
         *
         * @param v the approval policy (e.g. {@code untrusted}, {@code on-request}, {@code never}).
         * @return this builder for chaining.
         */
        public ExecOptions approvalPolicy(String v) { this.approvalPolicy = v; return this; }
        /**
         * Sets the {@code --profile} flag.
         *
         * @param v the configuration profile name.
         * @return this builder for chaining.
         */
        public ExecOptions profile(String v) { this.profile = v; return this; }
        /**
         * Sets the {@code -C} flag.
         *
         * @param v the working directory for the CLI process.
         * @return this builder for chaining.
         */
        public ExecOptions workingDir(String v) { this.workingDir = v; return this; }
        /**
         * Sets the {@code --add-dir} flag.
         *
         * @param v additional directory granted to the agent at runtime.
         * @return this builder for chaining.
         */
        public ExecOptions addDir(String v) { this.addDir = v; return this; }
        /**
         * Sets the {@code -o} flag.
         *
         * @param v destination file path for the final message.
         * @return this builder for chaining.
         */
        public ExecOptions outputFile(String v) { this.outputFile = v; return this; }
        /**
         * Sets the {@code --output-schema} flag.
         *
         * @param v path to a JSON Schema file describing the expected structured output.
         * @return this builder for chaining.
         */
        public ExecOptions outputSchema(String v) { this.outputSchema = v; return this; }
        /**
         * Sets the {@code --json} flag (default {@code true}).
         *
         * @param v {@code true} to enable JSON-Lines output, {@code false} to disable.
         * @return this builder for chaining.
         */
        public ExecOptions json(boolean v) { this.json = v; return this; }
        /**
         * Sets the {@code --ephemeral} flag.
         *
         * @param v {@code true} to prevent session persistence to disk.
         * @return this builder for chaining.
         */
        public ExecOptions ephemeral(boolean v) { this.ephemeral = v; return this; }
        /**
         * Sets the {@code --skip-git-repo-check} flag.
         *
         * @param v {@code true} to skip the git repository precondition check.
         * @return this builder for chaining.
         */
        public ExecOptions skipGitRepoCheck(boolean v) { this.skipGitRepoCheck = v; return this; }
        /**
         * Sets the {@code --oss} flag.
         *
         * @param v {@code true} to use the OSS provider instead of OpenAI-hosted models.
         * @return this builder for chaining.
         */
        public ExecOptions oss(boolean v) { this.oss = v; return this; }
        /**
         * Sets the {@code --local-provider} flag.
         *
         * @param v the local provider name (e.g. {@code lmstudio}, {@code ollama}).
         * @return this builder for chaining.
         */
        public ExecOptions localProvider(String v) { this.localProvider = v; return this; }
        /**
         * Sets the {@code --search} flag.
         *
         * @param v {@code true} to enable the web-search tool during execution.
         * @return this builder for chaining.
         */
        public ExecOptions search(boolean v) { this.search = v; return this; }
        /**
         * Sets the {@code --image} flag.
         *
         * @param v path to an image attachment forwarded to the agent.
         * @return this builder for chaining.
         */
        public ExecOptions image(String v) { this.image = v; return this; }
        /**
         * Sets one or more {@code -c key=value} overrides.
         *
         * @param v configuration override entries; each element becomes a separate {@code -c} flag.
         * @return this builder for chaining.
         */
        public ExecOptions configOverrides(String... v) { this.configOverrides = v; return this; }
        /**
         * Sets the {@code --dangerously-bypass-approvals-and-sandbox} flag.
         *
         * @param v {@code true} to bypass ALL approval prompts and the OS sandbox.
         * @return this builder for chaining.
         */
        public ExecOptions dangerouslyBypassApprovalsAndSandbox(boolean v) { this.dangerouslyBypassApprovalsAndSandbox = v; return this; }
        /**
         * Sets the {@code --dangerously-bypass-hook-trust} flag.
         *
         * @param v {@code true} to skip trust verification for plugin hooks.
         * @return this builder for chaining.
         */
        public ExecOptions dangerouslyBypassHookTrust(boolean v) { this.dangerouslyBypassHookTrust = v; return this; }
        /**
         * Sets the {@code --strict-config} flag.
         *
         * @param v {@code true} to fail fast on unknown configuration keys.
         * @return this builder for chaining.
         */
        public ExecOptions strictConfig(boolean v) { this.strictConfig = v; return this; }
        /**
         * Sets one or more {@code --enable} flags.
         *
         * @param v feature flag names to enable.
         * @return this builder for chaining.
         */
        public ExecOptions enable(String... v) { this.enable = v; return this; }
        /**
         * Sets one or more {@code --disable} flags.
         *
         * @param v feature flag names to disable.
         * @return this builder for chaining.
         */
        public ExecOptions disable(String... v) { this.disable = v; return this; }

        /**
         * Materialises the configured options into a positional argument list.
         *
         * @return the CLI argument list, beginning with {@code "exec"}.
         */
        public String[] toArgs() {
            List<String> args = new ArrayList<>();
            args.add("exec");
            if (model != null) { args.add("--model"); args.add(model); }
            if (sandbox != null) { args.add("--sandbox"); args.add(sandbox); }
            if (approvalPolicy != null) { args.add("--ask-for-approval"); args.add(approvalPolicy); }
            if (profile != null) { args.add("--profile"); args.add(profile); }
            if (workingDir != null) { args.add("-C"); args.add(workingDir); }
            if (addDir != null) { args.add("--add-dir"); args.add(addDir); }
            if (outputFile != null) { args.add("-o"); args.add(outputFile); }
            if (outputSchema != null) { args.add("--output-schema"); args.add(outputSchema); }
            if (json) { args.add("--json"); }
            if (ephemeral) { args.add("--ephemeral"); }
            if (skipGitRepoCheck) { args.add("--skip-git-repo-check"); }
            if (oss) { args.add("--oss"); }
            if (localProvider != null) { args.add("--local-provider"); args.add(localProvider); }
            if (search) { args.add("--search"); }
            if (image != null) { args.add("--image"); args.add(image); }
            if (configOverrides != null) {
                for (String c : configOverrides) { args.add("-c"); args.add(c); }
            }
            if (dangerouslyBypassApprovalsAndSandbox) { args.add("--dangerously-bypass-approvals-and-sandbox"); }
            if (dangerouslyBypassHookTrust) { args.add("--dangerously-bypass-hook-trust"); }
            if (strictConfig) { args.add("--strict-config"); }
            if (enable != null) {
                for (String e : enable) { args.add("--enable"); args.add(e); }
            }
            if (disable != null) {
                for (String d : disable) { args.add("--disable"); args.add(d); }
            }
            if (prompt != null) { args.add(prompt); }
            return args.toArray(new String[0]);
        }
    }

    // ============================================================
    // GlobalOptions — 全局选项（用于交互式会话或任意子命令）
    // ============================================================

    /**
     * Fluent builder that materialises the global flags accepted before the
     * sub-command in {@code codex [GLOBAL_OPTIONS] [SUBCOMMAND]}.
     *
     * <p>Unlike {@link ExecOptions}, this builder does NOT prepend
     * {@code "exec"} because the global flags apply to whichever sub-command
     * follows them.</p>
     *
     * @author [@Loong Wan](https://github.com/loong10k)
     * @since 3.0.0
     */
    public static class GlobalOptions {
        private String model;
        private String sandbox;
        private String approvalPolicy;
        private String profile;
        private String workingDir;
        private String addDir;
        private boolean oss;
        private String localProvider;
        private boolean search;
        private String[] image;
        private String[] configOverrides;
        private boolean dangerouslyBypassApprovalsAndSandbox;
        private boolean dangerouslyBypassHookTrust;
        private boolean strictConfig;
        private String[] enable;
        private String[] disable;
        private boolean noAltScreen;

        /**
         * Sets the {@code --model} flag.
         *
         * @param v the model identifier (e.g. {@code gpt-5-codex}).
         * @return this builder for chaining.
         */
        public GlobalOptions model(String v) { this.model = v; return this; }
        /**
         * Sets the {@code --sandbox} flag.
         *
         * @param v the sandbox mode (e.g. {@code read-only}, {@code workspace-write}).
         * @return this builder for chaining.
         */
        public GlobalOptions sandbox(String v) { this.sandbox = v; return this; }
        /**
         * Sets the {@code --ask-for-approval} flag.
         *
         * @param v the approval policy (e.g. {@code untrusted}, {@code on-request}, {@code never}).
         * @return this builder for chaining.
         */
        public GlobalOptions approvalPolicy(String v) { this.approvalPolicy = v; return this; }
        /**
         * Sets the {@code --profile} flag.
         *
         * @param v the configuration profile name.
         * @return this builder for chaining.
         */
        public GlobalOptions profile(String v) { this.profile = v; return this; }
        /**
         * Sets the {@code -C} flag.
         *
         * @param v the working directory for the CLI process.
         * @return this builder for chaining.
         */
        public GlobalOptions workingDir(String v) { this.workingDir = v; return this; }
        /**
         * Sets the {@code --add-dir} flag.
         *
         * @param v additional directory granted to the agent at runtime.
         * @return this builder for chaining.
         */
        public GlobalOptions addDir(String v) { this.addDir = v; return this; }
        /**
         * Sets the {@code --oss} flag.
         *
         * @param v {@code true} to use the OSS provider instead of OpenAI-hosted models.
         * @return this builder for chaining.
         */
        public GlobalOptions oss(boolean v) { this.oss = v; return this; }
        /**
         * Sets the {@code --local-provider} flag.
         *
         * @param v the local provider name (e.g. {@code lmstudio}, {@code ollama}).
         * @return this builder for chaining.
         */
        public GlobalOptions localProvider(String v) { this.localProvider = v; return this; }
        /**
         * Sets the {@code --search} flag.
         *
         * @param v {@code true} to enable the web-search tool during execution.
         * @return this builder for chaining.
         */
        public GlobalOptions search(boolean v) { this.search = v; return this; }
        /**
         * Sets one or more {@code --image} flags.
         *
         * @param v paths to image attachments forwarded to the agent.
         * @return this builder for chaining.
         */
        public GlobalOptions image(String... v) { this.image = v; return this; }
        /**
         * Sets one or more {@code -c key=value} overrides.
         *
         * @param v configuration override entries; each element becomes a separate {@code -c} flag.
         * @return this builder for chaining.
         */
        public GlobalOptions configOverrides(String... v) { this.configOverrides = v; return this; }
        /**
         * Sets the {@code --dangerously-bypass-approvals-and-sandbox} flag.
         *
         * @param v {@code true} to bypass ALL approval prompts and the OS sandbox.
         * @return this builder for chaining.
         */
        public GlobalOptions dangerouslyBypassApprovalsAndSandbox(boolean v) { this.dangerouslyBypassApprovalsAndSandbox = v; return this; }
        /**
         * Sets the {@code --dangerously-bypass-hook-trust} flag.
         *
         * @param v {@code true} to skip trust verification for plugin hooks.
         * @return this builder for chaining.
         */
        public GlobalOptions dangerouslyBypassHookTrust(boolean v) { this.dangerouslyBypassHookTrust = v; return this; }
        /**
         * Sets the {@code --strict-config} flag.
         *
         * @param v {@code true} to fail fast on unknown configuration keys.
         * @return this builder for chaining.
         */
        public GlobalOptions strictConfig(boolean v) { this.strictConfig = v; return this; }
        /**
         * Sets one or more {@code --enable} flags.
         *
         * @param v feature flag names to enable.
         * @return this builder for chaining.
         */
        public GlobalOptions enable(String... v) { this.enable = v; return this; }
        /**
         * Sets one or more {@code --disable} flags.
         *
         * @param v feature flag names to disable.
         * @return this builder for chaining.
         */
        public GlobalOptions disable(String... v) { this.disable = v; return this; }
        /**
         * Sets the {@code --no-alt-screen} flag.
         *
         * @param v {@code true} to disable the alternate-screen mode in the interactive TUI.
         * @return this builder for chaining.
         */
        public GlobalOptions noAltScreen(boolean v) { this.noAltScreen = v; return this; }

        /**
         * Materialises the configured options into a positional argument list
         * that contains ONLY the global flags &mdash; the caller is expected
         * to append the desired sub-command and its own arguments afterwards.
         *
         * @return the CLI argument list.
         */
        public String[] toArgs() {
            List<String> args = new ArrayList<>();
            if (model != null) { args.add("--model"); args.add(model); }
            if (sandbox != null) { args.add("--sandbox"); args.add(sandbox); }
            if (approvalPolicy != null) { args.add("--ask-for-approval"); args.add(approvalPolicy); }
            if (profile != null) { args.add("--profile"); args.add(profile); }
            if (workingDir != null) { args.add("-C"); args.add(workingDir); }
            if (addDir != null) { args.add("--add-dir"); args.add(addDir); }
            if (oss) { args.add("--oss"); }
            if (localProvider != null) { args.add("--local-provider"); args.add(localProvider); }
            if (search) { args.add("--search"); }
            if (image != null) {
                for (String img : image) { args.add("--image"); args.add(img); }
            }
            if (configOverrides != null) {
                for (String c : configOverrides) { args.add("-c"); args.add(c); }
            }
            if (dangerouslyBypassApprovalsAndSandbox) { args.add("--dangerously-bypass-approvals-and-sandbox"); }
            if (dangerouslyBypassHookTrust) { args.add("--dangerously-bypass-hook-trust"); }
            if (strictConfig) { args.add("--strict-config"); }
            if (enable != null) {
                for (String e : enable) { args.add("--enable"); args.add(e); }
            }
            if (disable != null) {
                for (String d : disable) { args.add("--disable"); args.add(d); }
            }
            if (noAltScreen) { args.add("--no-alt-screen"); }
            return args.toArray(new String[0]);
        }
    }
}
