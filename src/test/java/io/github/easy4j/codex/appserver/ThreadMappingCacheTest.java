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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the bounded LRU {@code sessionKey &rarr; threadId} cache,
 * including access-order eviction.
 *
 * @since 3.0.0
 */
class ThreadMappingCacheTest {

    @Test
    void shouldStoreAndReturnMappings() {
        ThreadMappingCache cache = new ThreadMappingCache(10);

        cache.put("s1", "th_1");
        cache.put("s2", "th_2");

        assertEquals("th_1", cache.get("s1"));
        assertEquals("th_2", cache.get("s2"));
        assertEquals(2, cache.size());
    }

    @Test
    void shouldReturnNullForUnknownKey() {
        ThreadMappingCache cache = new ThreadMappingCache(10);

        assertNull(cache.get("missing"));
    }

    @Test
    void shouldEvictLeastRecentlyUsedWhenOverBound() {
        ThreadMappingCache cache = new ThreadMappingCache(2);

        cache.put("s1", "th_1");
        cache.put("s2", "th_2");
        cache.put("s3", "th_3");

        assertEquals(2, cache.size());
        assertNull(cache.get("s1"));
        assertEquals("th_2", cache.get("s2"));
        assertEquals("th_3", cache.get("s3"));
    }

    @Test
    void shouldRefreshRecencyOnRead() {
        ThreadMappingCache cache = new ThreadMappingCache(2);

        cache.put("s1", "th_1");
        cache.put("s2", "th_2");
        cache.get("s1");
        cache.put("s3", "th_3");

        assertEquals("th_1", cache.get("s1"));
        assertNull(cache.get("s2"));
        assertEquals("th_3", cache.get("s3"));
    }

    @Test
    void shouldClampNonPositiveBoundsToOne() {
        ThreadMappingCache cache = new ThreadMappingCache(0);

        cache.put("s1", "th_1");
        cache.put("s2", "th_2");

        assertEquals(1, cache.size());
        assertNull(cache.get("s1"));
        assertEquals("th_2", cache.get("s2"));
    }

    @Test
    void shouldRejectNullKeysAndValues() {
        ThreadMappingCache cache = new ThreadMappingCache(10);

        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThrows(NullPointerException.class, () -> cache.put(null, "th_1"));
        assertThrows(NullPointerException.class, () -> cache.put("s1", null));
        assertNotNull(cache);
    }
}
