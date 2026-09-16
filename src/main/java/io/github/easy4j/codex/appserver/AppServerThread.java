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
package io.github.easy4j.codex.appserver;

import lombok.Builder;
import lombok.Data;

/**
 * Tolerant summary of a Codex thread as returned by the app-server
 * {@code thread/list}, {@code thread/read} and {@code thread/fork} methods.
 *
 * <p>The SDK accepts both camelCase and snake_case field spellings when
 * populating this model, staying forward-compatible with protocol revisions.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient#listThreads(int)
 */
@Data
@Builder
public class AppServerThread {

    /** Stable opaque thread identifier used by resume/fork/interrupt operations. */
    private String id;

    /** Display name (often a prompt summary); may be {@code null}. */
    private String name;

    /** Working directory captured when the thread was created; may be {@code null}. */
    private String cwd;

    /** Creation timestamp as reported by the server; may be {@code null}. */
    private String createdAt;

    /** Last-mutation timestamp as reported by the server; may be {@code null}. */
    private String updatedAt;

    /** {@code true} when the thread has been archived. */
    private boolean archived;
}
