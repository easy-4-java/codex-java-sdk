package io.github.easy4j.codex;

import lombok.Data;

/**
 * Codex CLI 客户端配置（纯 POJO，可与 Spring {@code @ConfigurationProperties} 映射）。
 */
@Data
public class CodexClientConfig {

    /** 本地 CLI 可执行文件名或绝对路径 */
    private String localExecutable = "codex";

    /** 命令执行超时（秒） */
    private int localTimeoutSeconds = 600;

    /** 探测 CLI 是否可用的超时（秒） */
    private int localProbeTimeoutSeconds = 5;

    /** 默认模型 */
    private String defaultModel;

    /** 默认 sandbox 模式（read-only, workspace-write, danger-full-access） */
    private String defaultSandbox;

    /** 默认审批策略（untrusted, on-request, never） */
    private String defaultApprovalPolicy;

    /** 默认配置 profile */
    private String defaultProfile;

    /** 是否使用 OSS provider */
    private boolean ossProvider;

    /** OSS provider 名称（lmstudio / ollama） */
    private String localProvider;

    /** 是否跳过 git repo 检查 */
    private boolean skipGitRepoCheck;

    /** 是否为临时会话（不持久化） */
    private boolean ephemeral;

    /** 是否输出 JSONL 格式 */
    private boolean jsonOutput = true;

    /** 输出 Schema 文件路径（结构化输出） */
    private String outputSchema;

    /** 是否启用 web search */
    private boolean search;

    /** 图片文件路径 */
    private String image;

    /** 配置覆盖（-c key=value） */
    private String[] configOverrides;

    /** 输出文件路径（output-last-message） */
    private String outputFile;

    /** 额外目录 */
    private String addDir;

    /** 工作目录 */
    private String workingDir;

    /** 是否跳过所有审批和沙箱（极度危险） */
    private boolean dangerouslyBypassApprovalsAndSandbox;

    /** 是否跳过 hook 信任检查 */
    private boolean dangerouslyBypassHookTrust;

    /** 严格配置模式（报错而非忽略未知字段） */
    private boolean strictConfig;

    /** 启用的 feature（可重复） */
    private String[] enable;

    /** 禁用的 feature（可重复） */
    private String[] disable;

    /** 禁用 alternate screen 模式（TUI） */
    private boolean noAltScreen;
}
