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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CodexCliResult}.
 *
 * @since 3.0.0
 */
class CodexCliResultTest {

    @Test
    void shouldExposeConstructorArguments() {
        CodexCliResult result = new CodexCliResult(0, "out", "err");

        assertEquals(0, result.getExitCode());
        assertEquals("out", result.getStdout());
        assertEquals("err", result.getStderr());
    }

    @Test
    void shouldReportSuccessWhenExitCodeIsZero() {
        CodexCliResult result = new CodexCliResult(0, "ok", "");

        assertTrue(result.isSuccess());
        assertFalse(result.isTimeout());
    }

    @Test
    void shouldReportFailureWhenExitCodeIsNonZero() {
        CodexCliResult result = new CodexCliResult(2, "", "boom");

        assertFalse(result.isSuccess());
        assertFalse(result.isTimeout());
    }

    @Test
    void shouldDetectTimeoutMarkerInStderr() {
        CodexCliResult result = new CodexCliResult(-1, "", "codex CLI timed out after 1000 ms");

        assertFalse(result.isSuccess());
        assertTrue(result.isTimeout());
    }

    @Test
    void shouldNotDetectTimeoutWithoutMarker() {
        CodexCliResult result = new CodexCliResult(-1, "", "something else");

        assertFalse(result.isTimeout());
    }

    @Test
    void shouldHandleNullStderrForTimeoutCheck() {
        CodexCliResult result = new CodexCliResult(-1, "out", null);

        assertFalse(result.isSuccess());
        assertFalse(result.isTimeout());
    }
}
