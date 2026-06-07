package io.github.hiwepy.codex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hiwepy.codex.cli.CodexCli;
import io.github.hiwepy.codex.cli.CodexCliExecutor;
import io.github.hiwepy.codex.cli.CodexCliResult;
import io.github.hiwepy.codex.model.CodexDoctorReport;
import io.github.hiwepy.codex.model.CodexEvent;
import io.github.hiwepy.codex.model.CodexSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Codex 客户端门面，封装本地 CLI 子进程调用。
 *
 * <h3>Session 管理</h3>
 * Codex 将 session 保存为文件（在 {@code ~/.codex/} 中），
 * 通过 {@code resume}/{@code fork}/{@code archive} 子命令管理。
 * 本 SDK 通过调用这些子命令来实现 session 生命周期管理。
 */
public class CodexClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CodexClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CodexClientConfig config;
    private final CodexCli cli;

    public CodexClient(CodexClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.cli = new CodexCli(new CodexCliExecutor(config));
    }

    public CodexClient(CodexClientConfig config, CodexCli cli) {
        this.config = Objects.requireNonNull(config, "config");
        this.cli = Objects.requireNonNull(cli, "cli");
    }

    // ============================================================
    // 基本信息
    // ============================================================

    public CodexCliResult version() { return cli.version(); }
    public CodexCliResult help() { return cli.help(); }

    // ============================================================
    // exec — 非交互执行
    // ============================================================

    /** 发送 prompt 并阻塞等待结果 */
    public CodexCliResult exec(String prompt) {
        CodexCli.ExecOptions opts = defaultOptions(prompt);
        return cli.executor().execute(opts.toArgs());
    }

    /** 发送 prompt（指定模型），返回 JSONL 事件 */
    public CodexCliResult exec(String prompt, String model) {
        return cli.exec(new CodexCli.ExecOptions(prompt).model(model).json(true));
    }

    /** 完整参数执行 */
    public CodexCliResult exec(CodexCli.ExecOptions opts) {
        return cli.executor().execute(opts.toArgs());
    }

    /** 执行并解析 JSONL 输出为 CodexEvent 列表 */
    public List<CodexEvent> execAndParse(String prompt) {
        CodexCliResult result = exec(prompt);
        return parseJsonlOutput(result.getStdout());
    }

    /** 在指定目录中执行 */
    public CodexCliResult execInDir(String workingDir, String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).workingDir(workingDir));
    }

    /** 临时执行（不持久化 session） */
    public CodexCliResult execEphemeral(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).ephemeral(true));
    }

    /** 执行并启用 web search */
    public CodexCliResult execWithSearch(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).search(true));
    }

    /** 执行并输出最后消息到文件 */
    public CodexCliResult execToFile(String prompt, String outputFile) {
        return cli.exec(new CodexCli.ExecOptions(prompt).outputFile(outputFile));
    }

    /** 执行并指定输出 Schema */
    public CodexCliResult execWithSchema(String prompt, String outputSchema) {
        return cli.exec(new CodexCli.ExecOptions(prompt).outputSchema(outputSchema));
    }

    /** 执行并附带图片 */
    public CodexCliResult execWithImage(String prompt, String imagePath) {
        return cli.exec(new CodexCli.ExecOptions(prompt).image(imagePath));
    }

    /** 执行并覆盖配置项 */
    public CodexCliResult execWithConfigOverrides(String prompt, String... configOverrides) {
        return cli.exec(new CodexCli.ExecOptions(prompt).configOverrides(configOverrides));
    }

    /** 执行并跳过所有审批和沙箱（极度危险） */
    public CodexCliResult execDangerously(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).dangerouslyBypassApprovalsAndSandbox(true));
    }

    /** 执行并跳过 hook 信任检查 */
    public CodexCliResult execBypassHookTrust(String prompt) {
        return cli.exec(new CodexCli.ExecOptions(prompt).dangerouslyBypassHookTrust(true));
    }

    /** 执行并启用指定 feature */
    public CodexCliResult execWithEnable(String prompt, String... features) {
        return cli.exec(new CodexCli.ExecOptions(prompt).enable(features));
    }

    /** 执行并禁用指定 feature */
    public CodexCliResult execWithDisable(String prompt, String... features) {
        return cli.exec(new CodexCli.ExecOptions(prompt).disable(features));
    }

    // ============================================================
    // 交互式会话
    // ============================================================

    /** 启动交互式会话（无初始 prompt） */
    public CodexCliResult startSession() {
        return cli.startInteractive();
    }

    /** 启动交互式会话 */
    public CodexCliResult startSession(String prompt) {
        return cli.startInteractive(prompt);
    }

    /** 带全局选项的交互式会话 */
    public CodexCliResult startSession(CodexCli.GlobalOptions opts, String prompt) {
        return cli.startInteractive(opts, prompt);
    }

    // ============================================================
    // exec resume — 恢复非交互会话
    // ============================================================

    /** 恢复指定 session 并追加 prompt */
    public CodexCliResult execResume(String sessionId, String prompt) {
        return cli.execResume(sessionId, prompt);
    }

    /** 恢复最近的非交互 session */
    public CodexCliResult execResumeLast(String prompt) {
        return cli.execResumeLast(prompt);
    }

    /** 恢复最近的 session（不追加 prompt） */
    public CodexCliResult execResumeLast() {
        return cli.execResumeLast();
    }

    /** 恢复并保存最后消息到文件 */
    public CodexCliResult execResumeLastToFile(String prompt, String outputFile) {
        return cli.execResumeLast(prompt, outputFile);
    }

    /** 恢复指定 session（包含所有历史）并追加 prompt */
    public CodexCliResult execResumeAll(String sessionId, String prompt) {
        return cli.execResumeAll(sessionId, prompt);
    }

    // ============================================================
    // review — 代码审查
    // ============================================================

    public CodexCliResult review() { return cli.review(); }
    public CodexCliResult reviewUncommitted() { return cli.reviewUncommitted(); }
    public CodexCliResult reviewBase(String branch) { return cli.reviewBase(branch); }
    public CodexCliResult reviewCommit(String sha) { return cli.reviewCommit(sha); }

    /** 带标题的 code review */
    public CodexCliResult review(String prompt, String title) {
        return cli.review(prompt, title);
    }

    // ============================================================
    // Session 生命周期
    // ============================================================

    /** 恢复交互式 session（通过 ID） */
    public CodexCliResult resumeSession(String sessionId) {
        return cli.resume(sessionId);
    }

    /** 恢复交互式 session 并追加 prompt */
    public CodexCliResult resumeSession(String sessionId, String prompt) {
        return cli.resume(sessionId, prompt);
    }

    /** 恢复最近交互式 session */
    public CodexCliResult resumeLastSession() {
        return cli.resumeLast();
    }

    /** 恢复最近交互式 session 并追加 prompt */
    public CodexCliResult resumeLastSession(String prompt) {
        return cli.resumeLast(prompt);
    }

    /** 查看所有 session（跨目录） */
    public CodexCliResult resumeAllSessions() {
        return cli.resumeAll();
    }

    /** 恢复最近 session（含非交互式） */
    public CodexCliResult resumeIncludeNonInteractive() {
        return cli.resumeIncludeNonInteractive();
    }

    /** Fork 会话（保留历史，创建新分支） */
    public CodexCliResult forkSession(String sessionId) {
        return cli.fork(sessionId);
    }

    /** Fork 会话并追加 prompt */
    public CodexCliResult forkSession(String sessionId, String prompt) {
        return cli.fork(sessionId, prompt);
    }

    /** Fork 最近会话 */
    public CodexCliResult forkLastSession() {
        return cli.forkLast();
    }

    /** Fork 最近会话并追加 prompt */
    public CodexCliResult forkLastSession(String prompt) {
        return cli.forkLast(prompt);
    }

    /** Fork 所有历史会话 */
    public CodexCliResult forkAllSessions(String sessionId) {
        return cli.forkAll(sessionId);
    }

    /** 归档会话 */
    public CodexCliResult archiveSession(String sessionId) {
        return cli.archive(sessionId);
    }

    /** 取消归档 */
    public CodexCliResult unarchiveSession(String sessionId) {
        return cli.unarchive(sessionId);
    }

    // ============================================================
    // apply
    // ============================================================

    /** 应用 task diff 到当前工作树 */
    public CodexCliResult apply(String taskId) {
        return cli.apply(taskId);
    }

    // ============================================================
    // auth
    // ============================================================

    public CodexCliResult login() { return cli.login(); }
    public CodexCliResult logout() { return cli.logout(); }

    // ============================================================
    // mcp
    // ============================================================

    public CodexCliResult mcpList() { return cli.mcpList(); }
    public CodexCliResult mcpGet(String name) { return cli.mcpGet(name); }
    public CodexCliResult mcpAdd(String name, String command, String... args) { return cli.mcpAdd(name, command, args); }
    public CodexCliResult mcpRemove(String name) { return cli.mcpRemove(name); }
    public CodexCliResult mcpLogin(String name) { return cli.mcpLogin(name); }
    public CodexCliResult mcpLogout(String name) { return cli.mcpLogout(name); }

    // ============================================================
    // doctor
    // ============================================================

    public CodexCliResult doctor() { return cli.doctor(); }
    public CodexCliResult doctorJson() { return cli.doctorJson(); }
    public CodexCliResult doctorSummary() { return cli.doctorSummary(); }

    // ============================================================
    // 其他命令
    // ============================================================

    public CodexCliResult update() { return cli.update(); }
    public CodexCliResult completion(String shell) { return cli.completion(shell); }
    public CodexCliResult features() { return cli.features(); }
    public CodexCliResult mcpServer() { return cli.mcpServer(); }

    /** {@code codex app} */
    public CodexCliResult app() { return cli.app(); }

    /** {@code codex sandbox <command...>} */
    public CodexCliResult sandbox(String... command) { return cli.sandbox(command); }

    /** {@code codex sandbox --permissions-profile <profile> <command...>} */
    public CodexCliResult sandbox(String profile, String... command) { return cli.sandbox(profile, command); }

    /** {@code codex debug [args...]} */
    public CodexCliResult debug(String... args) { return cli.debug(args); }

    /** {@code codex cloud [args...]} */
    public CodexCliResult cloud(String... args) { return cli.cloud(args); }

    /** {@code codex app-server [args...]} */
    public CodexCliResult appServer(String... args) { return cli.appServer(args); }

    /** {@code codex remote-control [args...]} */
    public CodexCliResult remoteControl(String... args) { return cli.remoteControl(args); }

    /** {@code codex exec-server [args...]} */
    public CodexCliResult execServer(String... args) { return cli.execServer(args); }

    /** {@code codex plugin [args...]} */
    public CodexCliResult plugin(String... args) { return cli.plugin(args); }

    /** 解析 doctor --json 输出 */
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

    /** 解析 session list JSON 输出（从 resume --all 等命令） */
    public List<CodexSession> parseSessionList(CodexCliResult result) {
        if (!result.isSuccess() || result.getStdout().isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(result.getStdout(), new TypeReference<List<CodexSession>>() {});
        } catch (Exception e) {
            log.debug("Failed to parse session list", e);
            return Collections.emptyList();
        }
    }

    /** 执行自定义 CLI 参数 */
    public CodexCliResult execute(String... args) {
        return cli.executor().execute(args);
    }

    // ============================================================
    // CLI 实例
    // ============================================================

    public CodexCli cli() { return cli; }
    public CodexClientConfig getConfig() { return config; }

    // ============================================================
    // 工具方法
    // ============================================================

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

    @Override
    public void close() {
    }
}
