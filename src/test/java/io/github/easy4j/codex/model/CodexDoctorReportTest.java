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
package io.github.easy4j.codex.model;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CodexDoctorReport} and its nested {@link
 * CodexDoctorReport.CheckItem}.
 *
 * @since 3.0.0
 */
class CodexDoctorReportTest {

    private final ObjectMapper mapper = new JsonMapper();

    @Test
    void shouldProvideSensibleDefaults() {
        CodexDoctorReport report = new CodexDoctorReport();
        CodexDoctorReport.CheckItem item = new CodexDoctorReport.CheckItem();

        assertNull(report.getVersion());
        assertNull(report.getPlatform());
        assertNull(report.getNodeVersion());
        assertNull(report.getChecks());
        assertNull(item.getName());
        assertNull(item.getStatus());
        assertNull(item.getMessage());
        assertEquals(false, item.isPassed());
    }

    @Test
    void shouldRoundTripFullReport() throws Exception {
        String json = "{"
                + "\"version\":\"0.1.0\","
                + "\"platform\":\"darwin-arm64\","
                + "\"nodeVersion\":\"v20.0.0\","
                + "\"checks\":["
                + "  {\"name\":\"Login\",\"status\":\"ok\",\"message\":\"authenticated\",\"passed\":true},"
                + "  {\"name\":\"MCP\",\"status\":\"warn\",\"message\":\"not configured\",\"passed\":false}"
                + "]}";
        CodexDoctorReport parsed = mapper.readValue(json, CodexDoctorReport.class);

        assertEquals("0.1.0", parsed.getVersion());
        assertEquals("darwin-arm64", parsed.getPlatform());
        assertEquals("v20.0.0", parsed.getNodeVersion());

        List<CodexDoctorReport.CheckItem> checks = parsed.getChecks();
        assertNotNull(checks);
        assertEquals(2, checks.size());

        CodexDoctorReport.CheckItem first = checks.get(0);
        assertEquals("Login", first.getName());
        assertEquals("ok", first.getStatus());
        assertEquals("authenticated", first.getMessage());
        assertEquals(true, first.isPassed());

        CodexDoctorReport.CheckItem second = checks.get(1);
        assertEquals("MCP", second.getName());
        assertEquals("warn", second.getStatus());
        assertEquals("not configured", second.getMessage());
        assertEquals(false, second.isPassed());
    }

    @Test
    void shouldIgnoreUnknownPropertiesOnReport() throws Exception {
        String json = "{\"version\":\"0.1.0\",\"extra\":\"ignored\"}";
        CodexDoctorReport parsed = mapper.readValue(json, CodexDoctorReport.class);

        assertEquals("0.1.0", parsed.getVersion());
        assertNull(parsed.getChecks());
    }

    @Test
    void shouldIgnoreUnknownPropertiesOnCheckItem() throws Exception {
        String json = "{\"name\":\"X\",\"status\":\"ok\",\"extra\":42,\"nested\":{}}";
        CodexDoctorReport.CheckItem item =
                new JsonMapper().readValue(json, CodexDoctorReport.CheckItem.class);

        assertEquals("X", item.getName());
        assertEquals("ok", item.getStatus());
        assertNull(item.getMessage());
        assertEquals(false, item.isPassed());
    }
}
