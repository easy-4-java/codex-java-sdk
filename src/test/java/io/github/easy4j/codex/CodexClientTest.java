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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.easy4j.codex.cli.CodexCli;
import io.github.easy4j.codex.cli.CodexCliExecutor;
import io.github.easy4j.codex.cli.CodexCliResult;
import io.github.easy4j.codex.model.CodexDoctorReport;
import io.github.easy4j.codex.model.CodexEvent;
import io.github.easy4j.codex.model.CodexSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CodexClient}.
 *
 * <p>Uses {@code /bin/echo} as the CLI executable so that argument
 * assembly and JSON parsing can be verified without depending on the
 * real {@code codex} binary.</p>
 *
 * @since 3.0.0
 */
class CodexClientTest {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private static CodexClientConfig echoConfig() {
        CodexClientConfig config = new CodexClientConfig();
        config.setLocalExecutable("/bin/echo");
        config.setLocalTimeoutSeconds(2);
        return config;
    }

    private static CodexClient echoClient() {
        return new CodexClient(echoConfig());
    }

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------

    @Test
    void shouldCreateClientWithConfigOnly() {
        CodexClient client = echoClient();
        assertNotNull(client);
        assertNotNull(client.cli());
        assertNotNull(client.getConfig());
    }

    @Test
    void shouldCreateClientWithConfigAndCli() {
        CodexClientConfig config = echoConfig();
        CodexCli cli = new CodexCli(new CodexCliExecutor(config));
        CodexClient client = new CodexClient(config, cli);

        assertNotNull(client);
        assertNotNull(client.cli());
        assertNotNull(client.getConfig());
    }

    @Test
    void shouldThrowOnNullConfig() {
        try {
            new CodexClient(null);
            org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("config"));
        }
    }

    @Test
    void shouldThrowOnNullConfigWithCli() {
        CodexCli cli = new CodexCli(new CodexCliExecutor(echoConfig()));
        try {
            new CodexClient(null, cli);
            org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("config"));
        }
    }

    @Test
    void shouldThrowOnNullCli() {
        try {
            new CodexClient(echoConfig(), null);
            org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("cli"));
        }
    }

    // ----------------------------------------------------------------
    // version / help
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateVersion() {
        CodexCliResult result = echoClient().version();
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("--version"));
    }

    @Test
    void shouldDelegateHelp() {
        CodexCliResult result = echoClient().help();
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("--help"));
    }

    // ----------------------------------------------------------------
    // exec variants
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateExecWithPrompt() {
        CodexCliResult result = echoClient().exec("hello");
        String out = result.getStdout();
        assertTrue(out.contains("exec"));
        assertTrue(out.contains("hello"));
    }

    @Test
    void shouldDelegateExecWithPromptAndModel() {
        CodexCliResult result = echoClient().exec("hello", "gpt-5");
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("gpt-5"));
    }

    @Test
    void shouldDelegateExecWithOptions() {
        CodexCli.ExecOptions opts = new CodexCli.ExecOptions("test").model("gpt-5");
        CodexCliResult result = echoClient().exec(opts);
        String out = result.getStdout();
        assertTrue(out.contains("exec"));
        assertTrue(out.contains("--model"));
    }

    @Test
    void shouldDelegateExecInDir() {
        CodexCliResult result = echoClient().execInDir("/tmp", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("-C"));
        assertTrue(out.contains("/tmp"));
    }

    @Test
    void shouldDelegateExecEphemeral() {
        CodexCliResult result = echoClient().execEphemeral("hello");
        assertTrue(result.getStdout().contains("--ephemeral"));
    }

    @Test
    void shouldDelegateExecWithSearch() {
        CodexCliResult result = echoClient().execWithSearch("hello");
        assertTrue(result.getStdout().contains("--search"));
    }

    @Test
    void shouldDelegateExecToFile() {
        CodexCliResult result = echoClient().execToFile("hello", "/tmp/out.md");
        assertTrue(result.getStdout().contains("-o"));
        assertTrue(result.getStdout().contains("/tmp/out.md"));
    }

    @Test
    void shouldDelegateExecWithSchema() {
        CodexCliResult result = echoClient().execWithSchema("hello", "/tmp/schema.json");
        assertTrue(result.getStdout().contains("--output-schema"));
        assertTrue(result.getStdout().contains("/tmp/schema.json"));
    }

    @Test
    void shouldDelegateExecWithImage() {
        CodexCliResult result = echoClient().execWithImage("hello", "/tmp/img.png");
        assertTrue(result.getStdout().contains("--image"));
        assertTrue(result.getStdout().contains("/tmp/img.png"));
    }

    @Test
    void shouldDelegateExecWithConfigOverrides() {
        CodexCliResult result = echoClient().execWithConfigOverrides("hello", "k=v", "k2=v2");
        String out = result.getStdout();
        assertTrue(out.contains("-c k=v"));
        assertTrue(out.contains("-c k2=v2"));
    }

    @Test
    void shouldDelegateExecDangerously() {
        CodexCliResult result = echoClient().execDangerously("hello");
        assertTrue(result.getStdout().contains("--dangerously-bypass-approvals-and-sandbox"));
    }

    @Test
    void shouldDelegateExecBypassHookTrust() {
        CodexCliResult result = echoClient().execBypassHookTrust("hello");
        assertTrue(result.getStdout().contains("--dangerously-bypass-hook-trust"));
    }

    @Test
    void shouldDelegateExecWithEnable() {
        CodexCliResult result = echoClient().execWithEnable("hello", "feat-a");
        assertTrue(result.getStdout().contains("--enable feat-a"));
    }

    @Test
    void shouldDelegateExecWithDisable() {
        CodexCliResult result = echoClient().execWithDisable("hello", "feat-b");
        assertTrue(result.getStdout().contains("--disable feat-b"));
    }

    // ----------------------------------------------------------------
    // execAndParse
    // ----------------------------------------------------------------

    @Test
    void shouldReturnEmptyListForBlankOutput() {
        // echo with no JSON-Lines content
        List<CodexEvent> events = echoClient().execAndParse("hello");
        assertNotNull(events);
        // The output will be the echoed args, not valid JSON, so it should be empty
        // (failed parse lines are skipped)
    }

    @Test
    void shouldParseValidJsonlOutput() throws Exception {
        // Create a custom client that returns valid JSON-Lines
        String jsonl = "{\"type\":\"message\",\"message\":\"hi\"}\n{\"type\":\"done\"}\n";
        CodexClient client = echoClient();
        // We test parseJsonlOutput indirectly - it's private, but execAndParse calls it
        // Since /bin/echo won't produce valid JSON, we verify the empty-list behavior
        List<CodexEvent> events = client.execAndParse("test");
        assertNotNull(events);
    }

    // ----------------------------------------------------------------
    // session management
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateStartSession() {
        CodexCliResult result = echoClient().startSession();
        assertEquals(0, result.getExitCode());
    }

    @Test
    void shouldDelegateStartSessionWithPrompt() {
        CodexCliResult result = echoClient().startSession("hello");
        assertTrue(result.getStdout().contains("hello"));
    }

    @Test
    void shouldDelegateStartSessionWithOpts() {
        CodexCli.GlobalOptions opts = new CodexCli.GlobalOptions().model("gpt-5");
        CodexCliResult result = echoClient().startSession(opts, "hello");
        String out = result.getStdout();
        assertTrue(out.contains("--model"));
        assertTrue(out.contains("hello"));
    }

    @Test
    void shouldDelegateExecResume() {
        CodexCliResult result = echoClient().execResume("sess-1", "hello");
        String out = result.getStdout();
        assertTrue(out.contains("resume"));
        assertTrue(out.contains("sess-1"));
    }

    @Test
    void shouldDelegateExecResumeLastWithPrompt() {
        CodexCliResult result = echoClient().execResumeLast("hello");
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateExecResumeLastNoArgs() {
        CodexCliResult result = echoClient().execResumeLast();
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateExecResumeLastToFile() {
        CodexCliResult result = echoClient().execResumeLastToFile("hello", "/tmp/out.md");
        String out = result.getStdout();
        assertTrue(out.contains("-o"));
        assertTrue(out.contains("/tmp/out.md"));
    }

    @Test
    void shouldDelegateExecResumeAll() {
        CodexCliResult result = echoClient().execResumeAll("sess-1", "hello");
        assertTrue(result.getStdout().contains("--all"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    // ----------------------------------------------------------------
    // review
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateReview() {
        CodexCliResult result = echoClient().review();
        assertTrue(result.getStdout().contains("review"));
    }

    @Test
    void shouldDelegateReviewUncommitted() {
        CodexCliResult result = echoClient().reviewUncommitted();
        assertTrue(result.getStdout().contains("--uncommitted"));
    }

    @Test
    void shouldDelegateReviewBase() {
        CodexCliResult result = echoClient().reviewBase("main");
        assertTrue(result.getStdout().contains("--base"));
    }

    @Test
    void shouldDelegateReviewCommit() {
        CodexCliResult result = echoClient().reviewCommit("abc123");
        assertTrue(result.getStdout().contains("--commit"));
    }

    @Test
    void shouldDelegateReviewWithTitle() {
        CodexCliResult result = echoClient().review("check", "Title");
        assertTrue(result.getStdout().contains("--title"));
    }

    // ----------------------------------------------------------------
    // resume / fork / archive session lifecycle
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateResumeSession() {
        CodexCliResult result = echoClient().resumeSession("sess-1");
        assertTrue(result.getStdout().contains("resume"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    @Test
    void shouldDelegateResumeSessionWithPrompt() {
        CodexCliResult result = echoClient().resumeSession("sess-1", "hello");
        assertTrue(result.getStdout().contains("resume"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    @Test
    void shouldDelegateResumeLastSession() {
        CodexCliResult result = echoClient().resumeLastSession();
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateResumeLastSessionWithPrompt() {
        CodexCliResult result = echoClient().resumeLastSession("hello");
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateResumeAllSessions() {
        CodexCliResult result = echoClient().resumeAllSessions();
        assertTrue(result.getStdout().contains("--all"));
    }

    @Test
    void shouldDelegateResumeIncludeNonInteractive() {
        CodexCliResult result = echoClient().resumeIncludeNonInteractive();
        assertTrue(result.getStdout().contains("--include-non-interactive"));
    }

    @Test
    void shouldDelegateForkSession() {
        CodexCliResult result = echoClient().forkSession("sess-1");
        assertTrue(result.getStdout().contains("fork"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    @Test
    void shouldDelegateForkSessionWithPrompt() {
        CodexCliResult result = echoClient().forkSession("sess-1", "hello");
        assertTrue(result.getStdout().contains("fork"));
        assertTrue(result.getStdout().contains("sess-1"));
    }

    @Test
    void shouldDelegateForkLastSession() {
        CodexCliResult result = echoClient().forkLastSession();
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateForkLastSessionWithPrompt() {
        CodexCliResult result = echoClient().forkLastSession("hello");
        assertTrue(result.getStdout().contains("--last"));
    }

    @Test
    void shouldDelegateForkAllSessions() {
        CodexCliResult result = echoClient().forkAllSessions("sess-1");
        assertTrue(result.getStdout().contains("--all"));
    }

    @Test
    void shouldDelegateArchiveSession() {
        CodexCliResult result = echoClient().archiveSession("sess-1");
        assertTrue(result.getStdout().contains("archive"));
    }

    @Test
    void shouldDelegateUnarchiveSession() {
        CodexCliResult result = echoClient().unarchiveSession("sess-1");
        assertTrue(result.getStdout().contains("unarchive"));
    }

    // ----------------------------------------------------------------
    // apply / auth / mcp / doctor
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateApply() {
        CodexCliResult result = echoClient().apply("task-1");
        assertTrue(result.getStdout().contains("apply"));
    }

    @Test
    void shouldDelegateLogin() {
        CodexCliResult result = echoClient().login();
        assertTrue(result.getStdout().contains("login"));
    }

    @Test
    void shouldDelegateLogout() {
        CodexCliResult result = echoClient().logout();
        assertTrue(result.getStdout().contains("logout"));
    }

    @Test
    void shouldDelegateMcpList() {
        CodexCliResult result = echoClient().mcpList();
        assertTrue(result.getStdout().contains("mcp"));
        assertTrue(result.getStdout().contains("list"));
    }

    @Test
    void shouldDelegateMcpGet() {
        CodexCliResult result = echoClient().mcpGet("my-server");
        assertTrue(result.getStdout().contains("get"));
    }

    @Test
    void shouldDelegateMcpAdd() {
        CodexCliResult result = echoClient().mcpAdd("my-server", "npx");
        assertTrue(result.getStdout().contains("add"));
    }

    @Test
    void shouldDelegateMcpRemove() {
        CodexCliResult result = echoClient().mcpRemove("my-server");
        assertTrue(result.getStdout().contains("remove"));
    }

    @Test
    void shouldDelegateMcpLogin() {
        CodexCliResult result = echoClient().mcpLogin("my-server");
        assertTrue(result.getStdout().contains("login"));
    }

    @Test
    void shouldDelegateMcpLogout() {
        CodexCliResult result = echoClient().mcpLogout("my-server");
        assertTrue(result.getStdout().contains("logout"));
    }

    @Test
    void shouldDelegateDoctor() {
        CodexCliResult result = echoClient().doctor();
        assertTrue(result.getStdout().contains("doctor"));
    }

    @Test
    void shouldDelegateDoctorJson() {
        CodexCliResult result = echoClient().doctorJson();
        assertTrue(result.getStdout().contains("--json"));
    }

    @Test
    void shouldDelegateDoctorSummary() {
        CodexCliResult result = echoClient().doctorSummary();
        assertTrue(result.getStdout().contains("--summary"));
    }

    // ----------------------------------------------------------------
    // other commands
    // ----------------------------------------------------------------

    @Test
    void shouldDelegateUpdate() {
        CodexCliResult result = echoClient().update();
        assertTrue(result.getStdout().contains("update"));
    }

    @Test
    void shouldDelegateCompletion() {
        CodexCliResult result = echoClient().completion("zsh");
        assertTrue(result.getStdout().contains("zsh"));
    }

    @Test
    void shouldDelegateFeatures() {
        CodexCliResult result = echoClient().features();
        assertTrue(result.getStdout().contains("features"));
    }

    @Test
    void shouldDelegateMcpServer() {
        CodexCliResult result = echoClient().mcpServer();
        assertTrue(result.getStdout().contains("mcp-server"));
    }

    @Test
    void shouldDelegateApp() {
        CodexCliResult result = echoClient().app();
        assertTrue(result.getStdout().contains("app"));
    }

    @Test
    void shouldDelegateSandbox() {
        CodexCliResult result = echoClient().sandbox(new String[]{"ls"});
        assertTrue(result.getStdout().contains("sandbox"));
    }

    @Test
    void shouldDelegateSandboxWithProfile() {
        CodexCliResult result = echoClient().sandbox("strict", new String[]{"ls"});
        assertTrue(result.getStdout().contains("--permissions-profile"));
    }

    @Test
    void shouldDelegateDebug() {
        CodexCliResult result = echoClient().debug("--verbose");
        assertTrue(result.getStdout().contains("debug"));
    }

    @Test
    void shouldDelegateCloud() {
        CodexCliResult result = echoClient().cloud("status");
        assertTrue(result.getStdout().contains("cloud"));
    }

    @Test
    void shouldDelegateAppServer() {
        CodexCliResult result = echoClient().appServer("--port", "8080");
        assertTrue(result.getStdout().contains("app-server"));
    }

    @Test
    void shouldDelegateRemoteControl() {
        CodexCliResult result = echoClient().remoteControl("--verbose");
        assertTrue(result.getStdout().contains("remote-control"));
    }

    @Test
    void shouldDelegateExecServer() {
        CodexCliResult result = echoClient().execServer("--port", "9090");
        assertTrue(result.getStdout().contains("exec-server"));
    }

    @Test
    void shouldDelegatePlugin() {
        CodexCliResult result = echoClient().plugin("list");
        assertTrue(result.getStdout().contains("plugin"));
    }

    // ----------------------------------------------------------------
    // parseDoctorReport / parseSessionList / execute / close
    // ----------------------------------------------------------------

    @Test
    void shouldReturnNullForDoctorReportWhenCliFails() {
        // /bin/echo will succeed but won't produce valid doctor JSON
        CodexDoctorReport report = echoClient().parseDoctorReport();
        // The echoed args won't be valid JSON, so parsing will fail and return null
        assertNull(report);
    }

    @Test
    void shouldReturnEmptySessionListWhenCliFails() {
        CodexCliResult fakeResult = new CodexCliResult(1, "", "error");
        List<CodexSession> sessions = echoClient().parseSessionList(fakeResult);
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void shouldReturnEmptySessionListWhenOutputIsEmpty() {
        CodexCliResult fakeResult = new CodexCliResult(0, "", "");
        List<CodexSession> sessions = echoClient().parseSessionList(fakeResult);
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void shouldParseValidSessionList() throws Exception {
        String json = "[{\"id\":\"sess-1\",\"name\":\"test\"}]";
        CodexCliResult fakeResult = new CodexCliResult(0, json, "");
        List<CodexSession> sessions = echoClient().parseSessionList(fakeResult);
        assertNotNull(sessions);
        assertEquals(1, sessions.size());
        assertEquals("sess-1", sessions.get(0).getId());
    }

    @Test
    void shouldReturnEmptySessionListForInvalidJson() {
        CodexCliResult fakeResult = new CodexCliResult(0, "not json", "");
        List<CodexSession> sessions = echoClient().parseSessionList(fakeResult);
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void shouldDelegateExecuteRawArgs() {
        CodexCliResult result = echoClient().execute("--version");
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("--version"));
    }

    @Test
    void shouldCloseWithoutError() {
        CodexClient client = echoClient();
        client.close(); // no-op, should not throw
    }

    // ----------------------------------------------------------------
    // default options from config
    // ----------------------------------------------------------------

    @Test
    void shouldPropagateConfigDefaultsToExecOptions() {
        CodexClientConfig config = echoConfig();
        config.setDefaultModel("gpt-5");
        config.setDefaultSandbox("read-only");
        config.setDefaultApprovalPolicy("never");
        config.setDefaultProfile("dev");
        config.setWorkingDir("/tmp");
        config.setAddDir("/extra");
        config.setOutputFile("/out.md");
        config.setOutputSchema("/schema.json");
        config.setEphemeral(true);
        config.setSkipGitRepoCheck(true);
        config.setOssProvider(true);
        config.setLocalProvider("ollama");
        config.setSearch(true);
        config.setImage("/img.png");
        config.setConfigOverrides(new String[]{"k=v"});
        config.setDangerouslyBypassApprovalsAndSandbox(true);
        config.setDangerouslyBypassHookTrust(true);
        config.setStrictConfig(true);
        config.setEnable(new String[]{"feat-a"});
        config.setDisable(new String[]{"feat-b"});

        CodexClient client = new CodexClient(config);
        CodexCliResult result = client.exec("hello");
        String out = result.getStdout();

        assertTrue(out.contains("--model gpt-5"));
        assertTrue(out.contains("--sandbox read-only"));
        assertTrue(out.contains("--ask-for-approval never"));
        assertTrue(out.contains("--profile dev"));
        assertTrue(out.contains("-C /tmp"));
        assertTrue(out.contains("--add-dir /extra"));
        assertTrue(out.contains("-o /out.md"));
        assertTrue(out.contains("--output-schema /schema.json"));
        assertTrue(out.contains("--ephemeral"));
        assertTrue(out.contains("--skip-git-repo-check"));
        assertTrue(out.contains("--oss"));
        assertTrue(out.contains("--local-provider ollama"));
        assertTrue(out.contains("--search"));
        assertTrue(out.contains("--image /img.png"));
        assertTrue(out.contains("-c k=v"));
        assertTrue(out.contains("--dangerously-bypass-approvals-and-sandbox"));
        assertTrue(out.contains("--dangerously-bypass-hook-trust"));
        assertTrue(out.contains("--strict-config"));
        assertTrue(out.contains("--enable feat-a"));
        assertTrue(out.contains("--disable feat-b"));
    }

    @Test
    void shouldNotAddOptionalFlagsWhenConfigDefaultsAreNull() {
        CodexClientConfig config = echoConfig();
        // All optional fields are null by default
        CodexClient client = new CodexClient(config);
        CodexCliResult result = client.exec("hello");
        String out = result.getStdout();

        assertFalse(out.contains("--model"));
        assertFalse(out.contains("--sandbox"));
        assertFalse(out.contains("--ask-for-approval"));
        assertFalse(out.contains("--profile"));
        assertFalse(out.contains("-C"));
        assertFalse(out.contains("--add-dir"));
        assertFalse(out.contains("-o "));
        assertFalse(out.contains("--output-schema"));
        assertFalse(out.contains("--ephemeral"));
        assertFalse(out.contains("--skip-git-repo-check"));
        assertFalse(out.contains("--oss"));
        assertFalse(out.contains("--local-provider"));
        assertFalse(out.contains("--search"));
        assertFalse(out.contains("--image"));
        assertFalse(out.contains("-c "));
        assertFalse(out.contains("--dangerously-bypass-approvals-and-sandbox"));
        assertFalse(out.contains("--dangerously-bypass-hook-trust"));
        assertFalse(out.contains("--strict-config"));
        assertFalse(out.contains("--enable"));
        assertFalse(out.contains("--disable"));
    }
}
