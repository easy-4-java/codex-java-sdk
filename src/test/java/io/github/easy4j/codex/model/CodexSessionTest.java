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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CodexSession}.
 *
 * @since 3.0.0
 */
class CodexSessionTest {

    private final ObjectMapper mapper = new JsonMapper();

    @Test
    void shouldProvideSensibleDefaults() {
        CodexSession session = new CodexSession();

        assertEquals(null, session.getId());
        assertEquals(null, session.getName());
        assertEquals(null, session.getCwd());
        assertEquals(null, session.getCreatedAt());
        assertEquals(null, session.getUpdatedAt());
        assertFalse(session.isArchived());
        assertFalse(session.isInteractive());
        assertEquals(null, session.getModel());
    }

    @Test
    void shouldRoundTripAllKnownFields() throws Exception {
        String json = "{\"id\":\"sess-1\",\"name\":\"hello\",\"cwd\":\"/tmp\","
                + "\"created_at\":\"2024-01-01T00:00:00Z\","
                + "\"updated_at\":\"2024-01-02T00:00:00Z\","
                + "\"is_archived\":true,\"is_interactive\":true,"
                + "\"model\":\"gpt-5-codex\"}";
        CodexSession parsed = mapper.readValue(json, CodexSession.class);

        assertEquals("sess-1", parsed.getId());
        assertEquals("hello", parsed.getName());
        assertEquals("/tmp", parsed.getCwd());
        assertEquals("2024-01-01T00:00:00Z", parsed.getCreatedAt());
        assertEquals("2024-01-02T00:00:00Z", parsed.getUpdatedAt());
        assertTrue(parsed.isArchived());
        assertTrue(parsed.isInteractive());
        assertEquals("gpt-5-codex", parsed.getModel());
    }

    @Test
    void shouldIgnoreUnknownProperties() throws Exception {
        String json = "{\"id\":\"sess-1\",\"unexpected\":\"value\",\"more\":42}";
        CodexSession parsed = mapper.readValue(json, CodexSession.class);

        assertEquals("sess-1", parsed.getId());
        assertFalse(parsed.isArchived());
    }
}
