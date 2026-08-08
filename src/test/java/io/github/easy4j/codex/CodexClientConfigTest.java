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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link CodexClientConfig} POJO.
 *
 * <p>These tests exercise the Lombok-generated accessors and the documented
 * default values, ensuring the configuration stays backwards-compatible
 * across releases.</p>
 *
 * @since 3.0.0
 */
class CodexClientConfigTest {

    @Test
    void shouldExposeSecureDefaults() {
        CodexClientConfig config = new CodexClientConfig();

        assertEquals("codex", config.getLocalExecutable());
        assertEquals(600, config.getLocalTimeoutSeconds());
        assertEquals(5, config.getLocalProbeTimeoutSeconds());
        assertTrue(config.isJsonOutput());
        assertFalse(config.isOssProvider());
        assertFalse(config.isSkipGitRepoCheck());
        assertFalse(config.isEphemeral());
        assertFalse(config.isSearch());
        assertFalse(config.isDangerouslyBypassApprovalsAndSandbox());
        assertFalse(config.isDangerouslyBypassHookTrust());
        assertFalse(config.isStrictConfig());
        assertFalse(config.isNoAltScreen());
    }

    @Test
    void shouldDefaultOptionalStringFieldsToNull() {
        CodexClientConfig config = new CodexClientConfig();

        assertNotNull(config);
        assertEquals(null, config.getDefaultModel());
        assertEquals(null, config.getDefaultSandbox());
        assertEquals(null, config.getDefaultApprovalPolicy());
        assertEquals(null, config.getDefaultProfile());
        assertEquals(null, config.getLocalProvider());
        assertEquals(null, config.getOutputSchema());
        assertEquals(null, config.getImage());
        assertEquals(null, config.getConfigOverrides());
        assertEquals(null, config.getOutputFile());
        assertEquals(null, config.getAddDir());
        assertEquals(null, config.getWorkingDir());
        assertEquals(null, config.getEnable());
        assertEquals(null, config.getDisable());
    }

    @Test
    void shouldRoundTripAllScalarFields() {
        CodexClientConfig config = new CodexClientConfig();

        config.setLocalExecutable("/usr/local/bin/codex");
        config.setLocalTimeoutSeconds(120);
        config.setLocalProbeTimeoutSeconds(15);
        config.setDefaultModel("gpt-5-codex");
        config.setDefaultSandbox("workspace-write");
        config.setDefaultApprovalPolicy("on-request");
        config.setDefaultProfile("dev");
        config.setOssProvider(true);
        config.setLocalProvider("ollama");
        config.setSkipGitRepoCheck(true);
        config.setEphemeral(true);
        config.setJsonOutput(false);
        config.setOutputSchema("/tmp/schema.json");
        config.setSearch(true);
        config.setImage("/tmp/pic.png");
        config.setConfigOverrides(new String[]{"foo=bar", "baz=qux"});
        config.setOutputFile("/tmp/out.md");
        config.setAddDir("/tmp/extra");
        config.setWorkingDir("/tmp/work");
        config.setDangerouslyBypassApprovalsAndSandbox(true);
        config.setDangerouslyBypassHookTrust(true);
        config.setStrictConfig(true);
        config.setEnable(new String[]{"feat-a"});
        config.setDisable(new String[]{"feat-b"});
        config.setNoAltScreen(true);

        assertEquals("/usr/local/bin/codex", config.getLocalExecutable());
        assertEquals(120, config.getLocalTimeoutSeconds());
        assertEquals(15, config.getLocalProbeTimeoutSeconds());
        assertEquals("gpt-5-codex", config.getDefaultModel());
        assertEquals("workspace-write", config.getDefaultSandbox());
        assertEquals("on-request", config.getDefaultApprovalPolicy());
        assertEquals("dev", config.getDefaultProfile());
        assertTrue(config.isOssProvider());
        assertEquals("ollama", config.getLocalProvider());
        assertTrue(config.isSkipGitRepoCheck());
        assertTrue(config.isEphemeral());
        assertFalse(config.isJsonOutput());
        assertEquals("/tmp/schema.json", config.getOutputSchema());
        assertTrue(config.isSearch());
        assertEquals("/tmp/pic.png", config.getImage());
        assertArrayEquals(new String[]{"foo=bar", "baz=qux"}, config.getConfigOverrides());
        assertEquals("/tmp/out.md", config.getOutputFile());
        assertEquals("/tmp/extra", config.getAddDir());
        assertEquals("/tmp/work", config.getWorkingDir());
        assertTrue(config.isDangerouslyBypassApprovalsAndSandbox());
        assertTrue(config.isDangerouslyBypassHookTrust());
        assertTrue(config.isStrictConfig());
        assertArrayEquals(new String[]{"feat-a"}, config.getEnable());
        assertArrayEquals(new String[]{"feat-b"}, config.getDisable());
        assertTrue(config.isNoAltScreen());
    }
}
