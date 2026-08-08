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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CodexCli} and its inner option builders
 * ({@link CodexCli.ExecOptions} and {@link CodexCli.GlobalOptions}).
 *
 * <p>Uses {@code /bin/echo} as the CLI executable so that argument
 * assembly can be verified without depending on the real {@code codex}
 * binary.</p>
 *
 * @since 3.0.0
 */
class CodexCliTest {

    private static CodexCli echoCli() {
        CodexClientConfig config = new CodexClientConfig();
        config.setLocalExecutable("/bin/echo");
        config.setLocalTimeoutSeconds(2);
        return new CodexCli(new CodexCliExecutor(config));
    }

    // ----------------------------------------------------------------
    // Basic delegation
    // ----------------------------------------------------------------

    @Test
    void shouldExposeExecutor() {
        CodexCli cli = echoCli();
        assertNotNull(cli.executor());
    }

    @Test
    void shouldDelegateVersion() {
        CodexCliResult result = echoCli().version();
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("--version"));
    }

    @Test
    void shouldDelegateHelp() {
        CodexCliResult result = echoCli().help();
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("--help"));
    }

    // ----------------------------------------------------------------
    // exec
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateExecWithPrompt() {
        CodexCliResult result = echoCli().exec("hello");
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("exec"));
        assertTrue(result.getStdout().contains("hello"));
    }

    @Test
    void shouldDelegateExecWithPromptAndModel() {
        CodexCliResult result = echoCli().exec("hello", "gpt-5");
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("gpt-5"));
        assertTrue(out.contains("hello"));
    }

    @Test
    void shouldDelegateExecWithFullArgs() {
        CodexCliResult result = echoCli().exec("hello", "gpt-5", "read-only", true);
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("--sandbox"));
        assertTrue(out.contains("--json"));
    }

    @Test
    void shouldDelegateExecWithOptions() {
        CodexCli.ExecOptions opts = new CodexCli.ExecOptions("test")
                .model("gpt-5")
                .json(true)
                .ephemeral(true);
        CodexCliResult result = echoCli().exec(opts);
        String out = result.getStdout();
        assertTrue(out.contains("exec"));
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("--ephemeral"));
    }

    @Test
    void shouldDelegateExecInDir() {
        CodexCliResult result = echoCli().execInDir("/tmp", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("-C"));
        assertTrue(out.contains("/tmp"));
    }

    // ----------------------------------------------------------------
    // exec resume
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateExecResume() {
        CodexCliResult result = echoCli().execResume("sess-1", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("exec"));
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateExecResumeLastWithPrompt() {
        CodexCliResult result = echoCli().execResumeLast("hello");
        String out = result.getStdout();
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("--last"));
    }

    @Test
    void shouldDelegateExecResumeLastWithoutPrompt() {
        CodexCliResult result = echoCli().execResumeLast();
        String out = result.getStdout();
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("--last"));
    }

    @Test
    void shouldDelegateExecResumeLastWithOutputFile() {
        CodexCliResult result = echoCli().execResumeLast("hello", "/tmp/out.md");
        String out = result.getStdout();
        assertTrue(out.contains("-o"));
        assertTrue(out.contains("/tmp/out.md"));
    }

    @Test
    void shouldDelegateExecResumeAll() {
        CodexCliResult result = echoCli().execResumeAll("sess-1", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("--all"));
        assertTrue(out.contains("sess-1"));
    }

    // ----------------------------------------------------------------
    // review
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateReview() {
        CodexCliResult result = echoCli().review();
        assertTrue(result.getStdout().contains("review"));
    }

    @Test
    void shouldDelegateReviewUncommitted() {
        CodexCliResult result = echoCli().reviewUncommitted();
        assertTrue(result.getStdout().contains("--uncommitted"));
    }

    @Test
    void shouldDelegateReviewBase() {
        CodexCliResult result = echoCli().reviewBase("main");
        assertTrue(result.getStdout().contains("--base"));
        assertTrue(result.getStdout().contains("main"));
    }

    @Test
    void shouldDelegateReviewCommit() {
        CodexCliResult result = echoCli().reviewCommit("abc123");
        assertTrue(result.getStdout().contains("--commit"));
        assertTrue(result.getStdout().contains("abc123"));
    }

    @Test
    void shouldDelegateReviewWithTitle() {
        CodexCliResult result = echoCli().review("check this", "My Review");
        assertTrue(result.getStdout().contains("--title"));
        assertTrue(result.getStdout().contains("My Review"));
    }

    // ----------------------------------------------------------------
    // session lifecycle
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateResumeSessionWithPrompt() {
        CodexCliResult result = echoCli().resume("sess-1", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateResumeSessionWithoutPrompt() {
        CodexCliResult result = echoCli().resume("sess-1");
        String out = result.getStdout();
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateResumeLast() {
        CodexCliResult result = echoCli().resumeLast();
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateResumeLastWithPrompt() {
        CodexCliResult result = echoCli().resumeLast("hello");
        String out = result.getStdout();
        assertTrue(out.contains("--last"));
        assertTrue(out.contains("hello"));
    }

    @Test
    void shouldDelegateResumeAll() {
        CodexCliResult result = echoCli().resumeAll();
        assertTrue(result.getStdout().contains("--all"));
    }

    @Test
    void shouldDelegateResumeIncludeNonInteractive() {
        CodexCliResult result = echoCli().resumeIncludeNonInteractive();
        assertTrue(result.getStdout().contains("--include-non-interactive"));
    }

    @Test
    void shouldDelegateResumeWithModel() {
        CodexCliResult result = echoCli().resume("sess-1", "hello", "gpt-5");
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("gpt-5"));
    }

    // ----------------------------------------------------------------
    // fork
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateForkWithPrompt() {
        CodexCliResult result = echoCli().fork("sess-1", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("fork"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateForkWithoutPrompt() {
        CodexCliResult result = echoCli().fork("sess-1");
        String out = result.getStdout();
        assertTrue(out.contains("fork"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateForkLast() {
        CodexCliResult result = echoCli().forkLast();
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateForkLastWithPrompt() {
        CodexCliResult result = echoCli().forkLast("hello");
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateForkAll() {
        CodexCliResult result = echoCli().forkAll("sess-1");
        assertTrue(result.getStdout().contains("--all"));
    }

    // ----------------------------------------------------------------
    // archive / unarchive
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateArchive() {
        CodexCliResult result = echoCli().archive("sess-1");
        assertTrue(result.getStdout().contains("archive"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    @Test
    void shouldDelegateUnarchive() {
        CodexCliResult result = echoCli().unarchive("sess-1");
        assertTrue(result.getStdout().contains("unarchive"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    // ----------------------------------------------------------------
    // apply / auth
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateApply() {
        CodexCliResult result = echoCli().apply("task-1");
        assertTrue(result.getStdout().contains("apply"));
        assertTrue(result.getStdout().contains("task-1"));
    }

    @Test
    void shouldDelegateLogin() {
        CodexCliResult result = echoCli().login();
        assertTrue(result.getStdout().contains("login"));
    }

    @Test
    void shouldDelegateLogout() {
        CodexCliResult result = echoCli().logout();
        assertTrue(result.getStdout().contains("logout"));
    }

    // ----------------------------------------------------------------
    // mcp
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateMcpList() {
        CodexCliResult result = echoCli().mcpList();
        assertTrue(result.getStdout().contains("mcp"));
        assertTrue(result.getStdout().contains("list"));
    }

    @Test
    void shouldDelegateMcpGet() {
        CodexCliResult result = echoCli().mcpGet("my-server");
        assertTrue(result.getStdout().contains("mcp"));
        assertTrue(result.getStdout().contains("get"));
        assertTrue(result.getStdout().contains("my-server"));
    }

    @Test
    void shouldDelegateMcpAdd() {
        CodexCliResult result = echoCli().mcpAdd("my-server", "npx", "--verbose");
        String out = result.getStdout();
        assertTrue(out.contains("mcp"));
        assertTrue(out.contains("add"));
        assertTrue(out.contains("my-server"));
        assertTrue(out.contains("--verbose"));
    }

    @Test
    void shouldDelegateMcpRemove() {
        CodexCliResult result = echoCli().mcpRemove("my-server");
        assertTrue(result.getStdout().contains("remove"));
        assertTrue(result.getStdout().contains("my-server"));
    }

    @Test
    void shouldDelegateMcpLogin() {
        CodexCliResult result = echoCli().mcpLogin("my-server");
        assertTrue(result.getStdout().contains("login"));
        assertTrue(result.getStdout().contains("my-server"));
    }

    @Test
    void shouldDelegateMcpLogout() {
        CodexCliResult result = echoCli().mcpLogout("my-server");
        assertTrue(result.getStdout().contains("logout"));
        assertTrue(result.getStdout().contains("my-server"));
    }

    // ----------------------------------------------------------------
    // plugin / mcp-server / app-server / remote-control
    // ----------------------------------------------------------------

    @Test
    void shouldDelegatePlugin() {
        CodexCliResult result = echoCli().plugin("list");
        assertTrue(result.getStdout().contains("plugin"));
        assertTrue(result.getStdout().contains("list"));
    }

    @Test
    void shouldDelegateMcpServer() {
        CodexCliResult result = echoCli().mcpServer();
        assertTrue(result.getStdout().contains("mcp-server"));
    }

    @Test
    void shouldDelegateAppServer() {
        CodexCliResult result = echoCli().appServer("--port", "8080");
        String out = result.getStdout();
        assertTrue(out.contains("app-server"));
        assertTrue(out.contains("--port"));
    }

    @Test
    void shouldDelegateRemoteControl() {
        CodexCliResult result = echoCli().remoteControl("--verbose");
        assertTrue(result.getStdout().contains("remote-control"));
        assertTrue(result.getStdout().contains("--verbose"));
    }

    // ----------------------------------------------------------------
    // app / completion / update / doctor
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateApp() {
        CodexCliResult result = echoCli().app();
        assertTrue(result.getStdout().contains("app"));
    }

    @Test
    void shouldDelegateCompletion() {
        CodexCliResult result = echoCli().completion("zsh");
        assertTrue(result.getStdout().contains("completion"));
        assertTrue(result.getStdout().contains("zsh"));
    }

    @Test
    void shouldDelegateUpdate() {
        CodexCliResult result = echoCli().update();
        assertTrue(result.getStdout().contains("update"));
    }

    @Test
    void shouldDelegateDoctor() {
        CodexCliResult result = echoCli().doctor();
        assertTrue(result.getStdout().contains("doctor"));
    }

    @Test
    void shouldDelegateDoctorJson() {
        CodexCliResult result = echoCli().doctorJson();
        assertTrue(result.getStdout().contains("--json"));
    }

    @Test
    void shouldDelegateDoctorSummary() {
        CodexCliResult result = echoCli().doctorSummary();
        assertTrue(result.getStdout().contains("--summary"));
    }

    // ----------------------------------------------------------------
    // sandbox / debug / cloud / features / exec-server
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateSandbox() {
        CodexCliResult result = echoCli().sandbox(new String[]{"ls", "-la"});
        String out = result.getStdout();
        assertTrue(out.contains("sandbox"));
        assertTrue(out.contains("ls"));
    }

    @Test
    void shouldDelegateSandboxWithProfile() {
        CodexCliResult result = echoCli().sandbox("strict", new String[]{"ls"});
        String out = result.getStdout();
        assertTrue(out.contains("sandbox"));
        assertTrue(out.contains("--permissions-profile"));
        assertTrue(out.contains("strict"));
    }

    @Test
    void shouldDelegateDebug() {
        CodexCliResult result = echoCli().debug("--verbose");
        assertTrue(result.getStdout().contains("debug"));
        assertTrue(result.getStdout().contains("--verbose"));
    }

    @Test
    void shouldDelegateCloud() {
        CodexCliResult result = echoCli().cloud("status");
        assertTrue(result.getStdout().contains("cloud"));
        assertTrue(result.getStdout().contains("status"));
    }

    @Test
    void shouldDelegateFeatures() {
        CodexCliResult result = echoCli().features();
        assertTrue(result.getStdout().contains("features"));
    }

    @Test
    void shouldDelegateExecServer() {
        CodexCliResult result = echoCli().execServer("--port", "9090");
        String out = result.getStdout();
        assertTrue(out.contains("exec-server"));
        assertTrue(out.contains("--port"));
    }

    // ----------------------------------------------------------------
    // startInteractive
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateStartInteractiveNoArgs() {
        CodexCliResult result = echoCli().startInteractive();
        assertEquals(0, result.getExitCode());
    }

    @Test
    void shouldDelegateStartInteractiveWithPrompt() {
        CodexCliResult result = echoCli().startInteractive("hello");
        assertTrue(result.getStdout().contains("hello"));
    }

    @Test
    void shouldDelegateStartInteractiveWithGlobalOptions() {
        CodexCli.GlobalOptions opts = new CodexCli.GlobalOptions().model("gpt-5");
        CodexCliResult result = echoCli().startInteractive(opts, "hello");
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("gpt-5"));
        assertTrue(out.contains("hello"));
    }

    // ================================================================
    // ExecOptions builder
    // ================================================================

    @Test
    void shouldBuildMinimalExecOptions() {
        String[] args = new CodexCli.ExecOptions("prompt").toArgs();

        assertNotNull(args);
        assertTrue(args.length >= 2);
        assertEquals("exec", args[0]);
        assertEquals("prompt", args[args.length - 1]);
    }

    @Test
    void shouldBuildExecOptionsWithAllFlags() {
        String[] args = new CodexCli.ExecOptions("prompt")
                .model("gpt-5")
                .sandbox("read-only")
                .approvalPolicy("never")
                .profile("dev")
                .workingDir("/tmp")
                .addDir("/extra")
                .outputFile("/out.md")
                .outputSchema("/schema.json")
                .json(true)
                .ephemeral(true)
                .skipGitRepoCheck(true)
                .oss(true)
                .localProvider("ollama")
                .search(true)
                .image("/img.png")
                .configOverrides("k=v", "k2=v2")
                .dangerouslyBypassApprovalsAndSandbox(true)
                .dangerouslyBypassHookTrust(true)
                .strictConfig(true)
                .enable("feat-a")
                .disable("feat-b")
                .toArgs();

        String joined = String.join(" ", args);
        assertTrue(joined.contains("--model gpt-5"));
        assertTrue(joined.contains("--sandbox read-only"));
        assertTrue(joined.contains("--ask-for-approval never"));
        assertTrue(joined.contains("--profile dev"));
        assertTrue(joined.contains("-C /tmp"));
        assertTrue(joined.contains("--add-dir /extra"));
        assertTrue(joined.contains("-o /out.md"));
        assertTrue(joined.contains("--output-schema /schema.json"));
        assertTrue(joined.contains("--json"));
        assertTrue(joined.contains("--ephemeral"));
        assertTrue(joined.contains("--skip-git-repo-check"));
        assertTrue(joined.contains("--oss"));
        assertTrue(joined.contains("--local-provider ollama"));
        assertTrue(joined.contains("--search"));
        assertTrue(joined.contains("--image /img.png"));
        assertTrue(joined.contains("-c k=v"));
        assertTrue(joined.contains("-c k2=v2"));
        assertTrue(joined.contains("--dangerously-bypass-approvals-and-sandbox"));
        assertTrue(joined.contains("--dangerously-bypass-hook-trust"));
        assertTrue(joined.contains("--strict-config"));
        assertTrue(joined.contains("--enable feat-a"));
        assertTrue(joined.contains("--disable feat-b"));
        assertTrue(joined.contains("prompt"));
    }

    @Test
    void shouldOmitOptionalFlagsWhenNotSet() {
        String[] args = new CodexCli.ExecOptions("p").json(false).toArgs();
        String joined = String.join(" ", args);

        assertFalse(joined.contains("--model"));
        assertFalse(joined.contains("--sandbox"));
        assertFalse(joined.contains("--json"));
        assertFalse(joined.contains("--ephemeral"));
    }

    // ================================================================
    // GlobalOptions builder
    // ================================================================

    @Test
    void shouldBuildEmptyGlobalOptions() {
        String[] args = new CodexCli.GlobalOptions().toArgs();
        assertNotNull(args);
        assertEquals(0, args.length);
    }

    @Test
    void shouldBuildGlobalOptionsWithAllFlags() {
        String[] args = new CodexCli.GlobalOptions()
                .model("gpt-5")
                .sandbox("read-only")
                .approvalPolicy("never")
                .profile("dev")
                .workingDir("/tmp")
                .addDir("/extra")
                .oss(true)
                .localProvider("ollama")
                .search(true)
                .image("/img.png", "/img2.png")
                .configOverrides("k=v")
                .dangerouslyBypassApprovalsAndSandbox(true)
                .dangerouslyBypassHookTrust(true)
                .strictConfig(true)
                .enable("feat-a")
                .disable("feat-b")
                .noAltScreen(true)
                .toArgs();

        String joined = String.join(" ", args);
        assertTrue(joined.contains("--model gpt-5"));
        assertTrue(joined.contains("--sandbox read-only"));
        assertTrue(joined.contains("--ask-for-approval never"));
        assertTrue(joined.contains("--profile dev"));
        assertTrue(joined.contains("-C /tmp"));
        assertTrue(joined.contains("--add-dir /extra"));
        assertTrue(joined.contains("--oss"));
        assertTrue(joined.contains("--local-provider ollama"));
        assertTrue(joined.contains("--search"));
        assertTrue(joined.contains("--image /img.png"));
        assertTrue(joined.contains("--image /img2.png"));
        assertTrue(joined.contains("-c k=v"));
        assertTrue(joined.contains("--dangerously-bypass-approvals-and-sandbox"));
        assertTrue(joined.contains("--dangerously-bypass-hook-trust"));
        assertTrue(joined.contains("--strict-config"));
        assertTrue(joined.contains("--enable feat-a"));
        assertTrue(joined.contains("--disable feat-b"));
        assertTrue(joined.contains("--no-alt-screen"));
    }

    @Test
    void shouldOmitGlobalFlagsWhenNotSet() {
        String[] args = new CodexCli.GlobalOptions().toArgs();
        String joined = String.join(" ", args);

        assertFalse(joined.contains("--model"));
        assertFalse(joined.contains("--sandbox"));
        assertFalse(joined.contains("--json"));
        assertFalse(joined.contains("--no-alt-screen"));
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}
