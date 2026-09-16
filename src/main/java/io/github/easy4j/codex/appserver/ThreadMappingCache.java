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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, access-ordered LRU cache of {@code sessionKey &rarr; threadId}
 * mappings used to resume Codex threads across turns.
 *
 * <p>When an entry is evicted the corresponding session silently degrades to
 * starting a fresh thread on the next turn &mdash; no error is raised. The
 * default bound is 1000 entries (see
 * {@link CodexAppServerConfig#getMaxSessionMappings()}). All access refreshes
 * recency, so hot sessions survive while cold ones are evicted first.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see CodexAppServerClient
 */
public class ThreadMappingCache {

    private final Map<String, String> mappings;

    /**
     * Creates a cache bounded to the given number of entries.
     *
     * @param maxEntries maximum number of retained mappings; values below one
     *                   are clamped to one so a cache never becomes unbounded.
     */
    public ThreadMappingCache(int maxEntries) {
        int bound = Math.max(1, maxEntries);
        this.mappings = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > bound;
            }
        };
    }

    /**
     * Returns the thread id previously stored for {@code key}, or {@code null}.
     *
     * @param key session key; must not be {@code null}.
     * @return the mapped thread id, or {@code null} when absent.
     */
    public synchronized String get(String key) {
        Objects.requireNonNull(key, "key");
        return mappings.get(key);
    }

    /**
     * Stores a mapping, evicting the least-recently-used entry when over bound.
     *
     * @param key      session key; must not be {@code null}.
     * @param threadId Codex thread id; must not be {@code null}.
     */
    public synchronized void put(String key, String threadId) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(threadId, "threadId");
        mappings.put(key, threadId);
    }

    /**
     * Returns the current number of cached mappings.
     *
     * @return the number of retained entries.
     */
    public synchronized int size() {
        return mappings.size();
    }
}
