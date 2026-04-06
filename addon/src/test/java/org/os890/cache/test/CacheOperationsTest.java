/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.os890.cache.test;

import org.junit.jupiter.api.Test;
import org.os890.cache.CompressedCacheFactory;

import javax.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JCache {@link Cache} operations beyond basic put/get,
 * exercised through the compressed-cache Guava wrapper in FAST mode.
 */
class CacheOperationsTest {

    /**
     * Verifies that {@link Cache#containsKey(Object)} returns {@code true}
     * for an existing key and {@code false} for a missing key.
     */
    @Test
    void containsKey() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "containsKey-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("present", 1));

        assertTrue(cache.containsKey("k1"));
        assertFalse(cache.containsKey("missing"));
    }

    /**
     * Verifies that {@link Cache#remove(Object)} removes the entry so that
     * a subsequent {@link Cache#get(Object)} returns {@code null}.
     */
    @Test
    void remove() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "remove-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("toRemove", 2));
        assertTrue(cache.remove("k1"));
        assertNull(cache.get("k1"));
    }

    /**
     * Verifies that {@link Cache#putIfAbsent(Object, Object)} returns
     * {@code false} when the key already exists and {@code true} when
     * inserting a new key.
     */
    @Test
    void putIfAbsent() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "putIfAbsent-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("original", 3));

        assertFalse(cache.putIfAbsent("k1", new MyValue("duplicate", 99)));
        assertEquals("original", cache.get("k1").getLabel());

        assertTrue(cache.putIfAbsent("k2", new MyValue("new", 4)));
        assertEquals("new", cache.get("k2").getLabel());
    }

    /**
     * Verifies that {@link Cache#replace(Object, Object)} overwrites an
     * existing entry and returns {@code true}, and returns {@code false}
     * for a missing key.
     */
    @Test
    void replace() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "replace-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("old", 5));
        assertTrue(cache.replace("k1", new MyValue("new", 6)));
        assertEquals("new", cache.get("k1").getLabel());
        assertEquals(6, cache.get("k1").getValue());

        assertFalse(cache.replace("missing", new MyValue("none", 0)));
    }

    /**
     * Verifies that {@link Cache#getAndPut(Object, Object)} returns the
     * previous value and stores the new value.
     */
    @Test
    void getAndPut() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "getAndPut-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("first", 7));

        MyValue old = cache.getAndPut("k1", new MyValue("second", 8));
        assertEquals("first", old.getLabel());
        assertEquals(7, old.getValue());

        assertEquals("second", cache.get("k1").getLabel());
        assertEquals(8, cache.get("k1").getValue());
    }

    /**
     * Verifies that {@link Cache#getAndRemove(Object)} returns the previous
     * value and removes the entry from the cache.
     */
    @Test
    void getAndRemove() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "getAndRemove-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("gone", 9));

        MyValue old = cache.getAndRemove("k1");
        assertEquals("gone", old.getLabel());
        assertEquals(9, old.getValue());

        assertNull(cache.get("k1"));
    }

    /**
     * Verifies that {@link Cache#getAndReplace(Object, Object)} returns the
     * previous value and stores the replacement.
     */
    @Test
    void getAndReplace() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "getAndReplace-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("before", 10));

        MyValue old = cache.getAndReplace("k1", new MyValue("after", 11));
        assertEquals("before", old.getLabel());
        assertEquals(10, old.getValue());

        assertEquals("after", cache.get("k1").getLabel());
        assertEquals(11, cache.get("k1").getValue());
    }

    /**
     * Verifies that {@link Cache#clear()} removes all entries from the cache.
     */
    @Test
    void clear() {
        Cache<String, MyValue> cache = CompressedCacheFactory.getOrCreateSimpleCache(
                "clear-cache", 10, String.class, MyValue.class);

        cache.put("k1", new MyValue("a", 12));
        cache.put("k2", new MyValue("b", 13));

        cache.clear();

        assertNull(cache.get("k1"));
        assertNull(cache.get("k2"));
    }
}
