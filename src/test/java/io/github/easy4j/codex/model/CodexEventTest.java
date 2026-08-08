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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CodexEvent}, including JSON round-trips and
 * tolerance of unknown fields.
 *
 * @since 3.0.0
 */
class CodexEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldProvideSensibleDefaults() {
        CodexEvent event = new CodexEvent();

        assertNull(event.getType());
        assertNull(event.getMessage());
        assertNull(event.getTaskId());
        assertNull(event.getSessionId());
        assertNull(event.getData());
        assertNull(event.getError());
    }

    @Test
    void shouldRoundTripAllKnownFields() throws Exception {
        CodexEvent event = new CodexEvent();
        event.setType("message");
        event.setMessage("hello");
        event.setTaskId("task-1");
        event.setSessionId("sess-1");
        event.setData(java.util.Map.of("k", "v"));

        String json = mapper.writeValueAsString(event);
        CodexEvent parsed = mapper.readValue(json, CodexEvent.class);

        assertEquals("message", parsed.getType());
        assertEquals("hello", parsed.getMessage());
        assertEquals("task-1", parsed.getTaskId());
        assertEquals("sess-1", parsed.getSessionId());
        assertNotNull(parsed.getData());
    }

    @Test
    void shouldIgnoreUnknownProperties() throws Exception {
        String json = "{\"type\":\"x\",\"foo\":\"bar\",\"nested\":{\"a\":1}}";
        CodexEvent parsed = mapper.readValue(json, CodexEvent.class);

        assertEquals("x", parsed.getType());
        assertNull(parsed.getMessage());
        assertNull(parsed.getTaskId());
        assertNull(parsed.getSessionId());
    }
}
