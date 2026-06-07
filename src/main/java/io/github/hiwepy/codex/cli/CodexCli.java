package io.github.hiwepy.codex.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地 {@code codex} CLI 命令封装 — 覆盖所有官方 CLI 命令。
 *
 * <h3>Session 管理（核心特性）</h3>
 * <ul>
 *   <li>{@link #resume(String) resume(sessionId)} — 恢复交互式会话</li>
 *   <li>{@link #resumeLast() resumeLast()} — 恢复最近会话</li>
 *   <li>{@link #fork(String) fork(sessionId)} — Fork 会话</li>
 *   <li>{@link #archive(String) archive(sessionId)} — 归档会话</li>
 *   <li>{@link #unarchive(String) unarchive(sessionId)} — 取消归档</li>
 *   <li>{@link #execResume(String) execResume(sessionId)} — 恢复非交互式会话</li>
 * </ul>
 *
 * @see <a href="https://github.com/openai/codex">Codex CLI</a>
 */
public class CodexCli {

    private static final Logger log = LoggerFactory.getLogger(CodexCli.class);

    private final CodexCliExecutor executor;

    public CodexCli(CodexCliExecutor executor) {
        this.executor = executor;
    }

    public CodexCliExecutor executor() {
        return executor;
    }

    // ============================================================
    // 全局
    // ============================================================

    /** {@code codex --version} */
    public CodexCliResult version() {
        return executor.execute("--version");
    }

    /** {@code codex --help} */
    public CodexCliResult help() {
        return executor.execute("--help");
    }

    // ============================================================
    // 交互式会话（全局选项 + 可选 prompt）
    // ============================================================

    /** {@code codex [prompt]} — 启动交互式会话 */
    public CodexCliResult startInteractive(String prompt) {
        return executor.execute(prompt);
    }

    /** {@code codex} — 启动交互式会话（无初始 prompt） */
    public CodexCliResult startInteractive() {
        return executor.execute();
    }

    /** {@code codex [global options] [prompt]} — 带全局选项的交互式会话 */
    public CodexCliResult startInteractive(GlobalOptions opts, String prompt) {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, opts.toArgs());
        if (prompt != null) args.add(prompt);
        return executor.execute(args.toArray(new String[0]));
    }

    // ============================================================
    // exec — 非交互执行
    // ============================================================

    /** {@code codex exec <prompt>} */
    public CodexCliResult exec(String prompt) {
        return executor.execute("exec", prompt);
    }

    /** {@code codex exec --model <model> <prompt>} */
    public CodexCliResult exec(String prompt, String model) {
        return executor.execute("exec", "--model", model, prompt);
    }

    /** {@code codex exec --model <model> --sandbox <mode> --json <prompt>} */
    public CodexCliResult exec(String prompt, String model, String sandbox, boolean json) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        if (model != null) { args.add("--model"); args.add(model); }
        if (sandbox != null) { args.add("--sandbox"); args.add(sandbox); }
        if (json) { args.add("--json"); }
        args.add(prompt);
        return executor.execute(args.toArray(new String[0]));
    }

    /** {@code codex exec --model <model> --sandbox <mode> --ephemeral --json --output-last-message <file> <prompt>} */
    public CodexCliResult exec(ExecOptions opts) {
        return executor.execute(opts.toArgs());
    }

    /** {@code codex exec -C <dir> --add-dir <dir> --skip-git-repo-check <prompt>} */
    public CodexCliResult execInDir(String workingDir, String prompt) {
        return executor.execute("exec", "-C", workingDir, prompt);
    }

    // ============================================================
    // exec resume — 恢复非交互会话
    // ============================================================

    /** {@code codex exec resume <sessionId> <prompt>} */
    public CodexCliResult execResume(String sessionId, String prompt) {
        return executor.execute("exec", "resume", sessionId, prompt);
    }

    /** {@code codex exec resume --last <prompt>} */
    public CodexCliResult execResumeLast(String prompt) {
        return executor.execute("exec", "resume", "--last", prompt);
    }

    /** {@code codex exec resume --last} */
    public CodexCliResult execResumeLast() {
        return executor.execute("exec", "resume", "--last");
    }

    /** {@code codex exec resume --last --json --output-last-message <file> <prompt>} */
    public CodexCliResult execResumeLast(String prompt, String outputFile) {
        return executor.execute("exec", "resume", "--last", "--json", "-o", outputFile, prompt);
    }

    /** {@code codex exec resume --all <sessionId> <prompt>} */
    public CodexCliResult execResumeAll(String sessionId, String prompt) {
        return executor.execute("exec", "resume", "--all", sessionId, prompt);
    }

    // ============================================================
    // review — 代码审查
    // ============================================================

    /** {@code codex review} */
    public CodexCliResult review() {
        return executor.execute("review");
    }

    /** {@code codex review --uncommitted} */
    public CodexCliResult reviewUncommitted() {
        return executor.execute("review", "--uncommitted");
    }

    /** {@code codex review --base <branch>} */
    public CodexCliResult reviewBase(String baseBranch) {
        return executor.execute("review", "--base", baseBranch);
    }

    /** {@code codex review --commit <sha>} */
    public CodexCliResult reviewCommit(String sha) {
        return executor.execute("review", "--commit", sha);
    }

    /** {@code codex review --title <title> <prompt>} */
    public CodexCliResult review(String prompt, String title) {
        return executor.execute("review", "--title", title, prompt);
    }

    // ============================================================
    // session — 会话生命周期
    // ============================================================

    /** {@code codex resume [sessionId] [prompt]} — 交互式恢复 */
    public CodexCliResult resume(String sessionId, String prompt) {
        if (prompt != null) {
            return executor.execute("resume", sessionId, prompt);
        }
        return executor.execute("resume", sessionId);
    }

    public CodexCliResult resume(String sessionId) {
        return resume(sessionId, null);
    }

    /** {@code codex resume --last} — 恢复最近会话 */
    public CodexCliResult resumeLast() {
        return executor.execute("resume", "--last");
    }

    /** {@code codex resume --last [prompt]} */
    public CodexCliResult resumeLast(String prompt) {
        return executor.execute("resume", "--last", prompt);
    }

    /** {@code codex resume --all} — 查看所有会话（包括其他目录的） */
    public CodexCliResult resumeAll() {
        return executor.execute("resume", "--all");
    }

    /** {@code codex resume --include-non-interactive} */
    public CodexCliResult resumeIncludeNonInteractive() {
        return executor.execute("resume", "--last", "--include-non-interactive");
    }

    /** {@code codex resume --model <model> <sessionId> [prompt]} */
    public CodexCliResult resume(String sessionId, String prompt, String model) {
        return executor.execute("resume", "--model", model, sessionId, prompt);
    }

    // ============================================================
    // fork — 分支会话
    // ============================================================

    /** {@code codex fork [sessionId] [prompt]} */
    public CodexCliResult fork(String sessionId, String prompt) {
        if (prompt != null) {
            return executor.execute("fork", sessionId, prompt);
        }
        return executor.execute("fork", sessionId);
    }

    public CodexCliResult fork(String sessionId) {
        return fork(sessionId, null);
    }

    /** {@code codex fork --last} */
    public CodexCliResult forkLast() {
        return executor.execute("fork", "--last");
    }

    /** {@code codex fork --last [prompt]} */
    public CodexCliResult forkLast(String prompt) {
        return executor.execute("fork", "--last", prompt);
    }

    /** {@code codex fork --all <sessionId>} */
    public CodexCliResult forkAll(String sessionId) {
        return executor.execute("fork", "--all", sessionId);
    }

    // ============================================================
    // archive / unarchive
    // ============================================================

    /** {@code codex archive <sessionId>} */
    public CodexCliResult archive(String sessionId) {
        return executor.execute("archive", sessionId);
    }

    /** {@code codex unarchive <sessionId>} */
    public CodexCliResult unarchive(String sessionId) {
        return executor.execute("unarchive", sessionId);
    }

    // ============================================================
    // apply
    // ============================================================

    /** {@code codex apply <taskId>} */
    public CodexCliResult apply(String taskId) {
        return executor.execute("apply", taskId);
    }

    // ============================================================
    // auth
    // ============================================================

    /** {@code codex login} */
    public CodexCliResult login() {
        return executor.execute("login");
    }

    /** {@code codex logout} */
    public CodexCliResult logout() {
        return executor.execute("logout");
    }

    // ============================================================
    // mcp
    // ============================================================

    /** {@code codex mcp list} */
    public CodexCliResult mcpList() {
        return executor.execute("mcp", "list");
    }

    /** {@code codex mcp get <name>} */
    public CodexCliResult mcpGet(String name) {
        return executor.execute("mcp", "get", name);
    }

    /** {@code codex mcp add <name> <command> [args...]} */
    public CodexCliResult mcpAdd(String name, String command, String... args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add("mcp"); allArgs.add("add"); allArgs.add(name); allArgs.add(command);
        for (String a : args) allArgs.add(a);
        return executor.execute(allArgs.toArray(new String[0]));
    }

    /** {@code codex mcp remove <name>} */
    public CodexCliResult mcpRemove(String name) {
        return executor.execute("mcp", "remove", name);
    }

    /** {@code codex mcp login <name>} */
    public CodexCliResult mcpLogin(String name) {
        return executor.execute("mcp", "login", name);
    }

    /** {@code codex mcp logout <name>} */
    public CodexCliResult mcpLogout(String name) {
        return executor.execute("mcp", "logout", name);
    }

    // ============================================================
    // plugin
    // ============================================================

    /** {@code codex plugin <subcommand...>} */
    public CodexCliResult plugin(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "plugin";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    // ============================================================
    // mcp-server
    // ============================================================

    /** {@code codex mcp-server} */
    public CodexCliResult mcpServer() {
        return executor.execute("mcp-server");
    }

    // ============================================================
    // app-server / remote-control
    // ============================================================

    public CodexCliResult appServer(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "app-server";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    public CodexCliResult remoteControl(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "remote-control";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    // ============================================================
    // app / update / doctor
    // ============================================================

    /** {@code codex app} */
    public CodexCliResult app() {
        return executor.execute("app");
    }

    /** {@code codex completion <shell>} */
    public CodexCliResult completion(String shell) {
        return executor.execute("completion", shell);
    }

    /** {@code codex update} */
    public CodexCliResult update() {
        return executor.execute("update");
    }

    /** {@code codex doctor} */
    public CodexCliResult doctor() {
        return executor.execute("doctor");
    }

    /** {@code codex doctor --json} */
    public CodexCliResult doctorJson() {
        return executor.execute("doctor", "--json");
    }

    /** {@code codex doctor --summary} */
    public CodexCliResult doctorSummary() {
        return executor.execute("doctor", "--summary");
    }

    // ============================================================
    // sandbox
    // ============================================================

    /** {@code codex sandbox <command...>} */
    public CodexCliResult sandbox(String... command) {
        String[] all = new String[command.length + 1];
        all[0] = "sandbox";
        System.arraycopy(command, 0, all, 1, command.length);
        return executor.execute(all);
    }

    /** {@code codex sandbox --permissions-profile <name> <command...>} */
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

    public CodexCliResult debug(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "debug";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    public CodexCliResult cloud(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "cloud";
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    public CodexCliResult features() {
        return executor.execute("features");
    }

    // ============================================================
    // exec-server
    // ============================================================

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
     * {@code codex exec} 的完整选项组装。
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

        public ExecOptions(String prompt) { this.prompt = prompt; }

        public ExecOptions model(String v) { this.model = v; return this; }
        public ExecOptions sandbox(String v) { this.sandbox = v; return this; }
        public ExecOptions approvalPolicy(String v) { this.approvalPolicy = v; return this; }
        public ExecOptions profile(String v) { this.profile = v; return this; }
        public ExecOptions workingDir(String v) { this.workingDir = v; return this; }
        public ExecOptions addDir(String v) { this.addDir = v; return this; }
        public ExecOptions outputFile(String v) { this.outputFile = v; return this; }
        public ExecOptions outputSchema(String v) { this.outputSchema = v; return this; }
        public ExecOptions json(boolean v) { this.json = v; return this; }
        public ExecOptions ephemeral(boolean v) { this.ephemeral = v; return this; }
        public ExecOptions skipGitRepoCheck(boolean v) { this.skipGitRepoCheck = v; return this; }
        public ExecOptions oss(boolean v) { this.oss = v; return this; }
        public ExecOptions localProvider(String v) { this.localProvider = v; return this; }
        public ExecOptions search(boolean v) { this.search = v; return this; }
        public ExecOptions image(String v) { this.image = v; return this; }
        public ExecOptions configOverrides(String... v) { this.configOverrides = v; return this; }
        public ExecOptions dangerouslyBypassApprovalsAndSandbox(boolean v) { this.dangerouslyBypassApprovalsAndSandbox = v; return this; }
        public ExecOptions dangerouslyBypassHookTrust(boolean v) { this.dangerouslyBypassHookTrust = v; return this; }
        public ExecOptions strictConfig(boolean v) { this.strictConfig = v; return this; }
        public ExecOptions enable(String... v) { this.enable = v; return this; }
        public ExecOptions disable(String... v) { this.disable = v; return this; }

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
     * {@code codex [GLOBAL_OPTIONS] [PROMPT]} 的全局选项组装。
     * 这些选项位于子命令之前，适用于交互式会话或任意子命令。
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

        public GlobalOptions model(String v) { this.model = v; return this; }
        public GlobalOptions sandbox(String v) { this.sandbox = v; return this; }
        public GlobalOptions approvalPolicy(String v) { this.approvalPolicy = v; return this; }
        public GlobalOptions profile(String v) { this.profile = v; return this; }
        public GlobalOptions workingDir(String v) { this.workingDir = v; return this; }
        public GlobalOptions addDir(String v) { this.addDir = v; return this; }
        public GlobalOptions oss(boolean v) { this.oss = v; return this; }
        public GlobalOptions localProvider(String v) { this.localProvider = v; return this; }
        public GlobalOptions search(boolean v) { this.search = v; return this; }
        public GlobalOptions image(String... v) { this.image = v; return this; }
        public GlobalOptions configOverrides(String... v) { this.configOverrides = v; return this; }
        public GlobalOptions dangerouslyBypassApprovalsAndSandbox(boolean v) { this.dangerouslyBypassApprovalsAndSandbox = v; return this; }
        public GlobalOptions dangerouslyBypassHookTrust(boolean v) { this.dangerouslyBypassHookTrust = v; return this; }
        public GlobalOptions strictConfig(boolean v) { this.strictConfig = v; return this; }
        public GlobalOptions enable(String... v) { this.enable = v; return this; }
        public GlobalOptions disable(String... v) { this.disable = v; return this; }
        public GlobalOptions noAltScreen(boolean v) { this.noAltScreen = v; return this; }

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
