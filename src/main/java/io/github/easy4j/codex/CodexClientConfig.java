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

import lombok.Data;

/**
 * Configuration POJO for {@link CodexClient}.
 *
 * <p>This plain Java object captures every runtime knob the {@code codex} CLI
 * exposes, and is intentionally framework-free so it can be wired in three
 * common ways:</p>
 * <ul>
 *   <li>Constructed programmatically (e.g. {@code new CodexClientConfig()}).</li>
 *   <li>Mapped from external configuration sources such as YAML/JSON.</li>
 *   <li>Bound by Spring's {@code @ConfigurationProperties} mechanism.</li>
 * </ul>
 *
 * <p>Default values are tuned for safe, non-destructive use: the JSON output
 * mode is enabled by default so callers can rely on structured responses,
 * while the bypass flags default to {@code false} so that operations always
 * require approvals unless explicitly opted-in.</p>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see CodexClient
 */
@Data
public class CodexClientConfig {

    /** Name or absolute path of the local {@code codex} CLI executable. */
    private String localExecutable = "codex";

    /** Command execution timeout in seconds (passed to the OS-level watchdog). */
    private int localTimeoutSeconds = 600;

    /** Timeout in seconds used by {@link CodexClientConfig#probe()} when verifying CLI availability. */
    private int localProbeTimeoutSeconds = 5;

    /** Default model name (e.g. {@code gpt-5-codex}); propagated to every {@code exec} call when set. */
    private String defaultModel;

    /** Default sandbox mode (one of {@code read-only}, {@code workspace-write}, {@code danger-full-access}). */
    private String defaultSandbox;

    /** Default approval policy (one of {@code untrusted}, {@code on-request}, {@code never}). */
    private String defaultApprovalPolicy;

    /** Default configuration profile name. */
    private String defaultProfile;

    /** Whether to use the OSS provider instead of OpenAI-hosted models. */
    private boolean ossProvider;

    /** OSS provider name (e.g. {@code lmstudio}, {@code ollama}). */
    private String localProvider;

    /** Skip the precondition check that the current directory is a git repository. */
    private boolean skipGitRepoCheck;

    /** If {@code true}, sessions are not persisted to disk after the call completes. */
    private boolean ephemeral;

    /** Emit {@code codex} output as JSON Lines (the SDK parses these into {@link io.github.easy4j.codex.model.CodexEvent} instances). */
    private boolean jsonOutput = true;

    /** Path to a JSON Schema file describing the structured output expected from the agent. */
    private String outputSchema;

    /** Enable web-search tool during execution. */
    private boolean search;

    /** Path to an image attachment forwarded to the agent. */
    private String image;

    /** Inline {@code -c key=value} configuration overrides; each element becomes a separate {@code -c} flag. */
    private String[] configOverrides;

    /** Output file path used by {@code codex exec --output-last-message}. */
    private String outputFile;

    /** Additional directory granted to the agent at runtime. */
    private String addDir;

    /** Working directory in which the {@code codex} process is launched. */
    private String workingDir;

    /**
     * Bypass ALL approval prompts and the OS sandbox. <strong>Use with extreme
     * caution</strong> &mdash; the agent will run without any human gating.
     */
    private boolean dangerouslyBypassApprovalsAndSandbox;

    /**
     * Skip the trust verification step for plugin hooks. Enable only when you
     * fully control the hooks that will run.
     */
    private boolean dangerouslyBypassHookTrust;

    /** Fail fast on unknown configuration keys instead of silently ignoring them. */
    private boolean strictConfig;

    /** Feature flags to enable (passed as repeated {@code --enable <name>}). */
    private String[] enable;

    /** Feature flags to disable (passed as repeated {@code --disable <name>}). */
    private String[] disable;

    /** Disable the alternate-screen mode in the interactive TUI. */
    private boolean noAltScreen;
}
