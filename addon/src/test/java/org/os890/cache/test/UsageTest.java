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

import com.google.common.cache.CacheBuilder;
import org.junit.jupiter.api.Test;
import org.os890.cache.CompressedCacheFactory;
import org.os890.cache.CompressedValueMode;

import javax.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link CompressedCacheFactory} verifying that values
 * survive a full compress-store-retrieve-decompress round-trip in both
 * {@link CompressedValueMode#FAST FAST} and {@link CompressedValueMode#SMALL SMALL} modes
 * with simple and custom Guava cache builders.
 */
class UsageTest {

    /**
     * Verifies FAST-mode put and get via the simple cache API.
     */
    @Test
    void fastPutAndGet() {
        CompressedCacheFactory.getOrCreateSimpleCache("fast-test-simple", 10, String.class, MyValue.class)
                .put("v1", new MyValue("test", 42));

        Cache<String, MyValue> myValueCache = CompressedCacheFactory.getOrCreateSimpleCache("fast-test-simple", 10, String.class, MyValue.class);
        assertEquals("test", myValueCache.get("v1").getLabel());
        assertEquals(42, myValueCache.get("v1").getValue());
    }

    /**
     * Verifies SMALL-mode put and get via the simple cache API.
     */
    @Test
    void smallPutAndGet() {
        CompressedCacheFactory.getOrCreateSimpleCache("small-test-simple", 10, String.class, MyValue.class, CompressedValueMode.SMALL)
                .put("v1", new MyValue("test", 42));

        Cache<String, MyValue> myValueCache = CompressedCacheFactory.getOrCreateSimpleCache("small-test-simple", 20, String.class, MyValue.class, CompressedValueMode.SMALL /*optional*/);
        assertEquals("test", myValueCache.get("v1").getLabel());
        assertEquals(42, myValueCache.get("v1").getValue());
    }

    /**
     * Verifies FAST-mode put and get with a manually configured Guava cache builder.
     */
    @Test
    void fastPutAndGetWithCustomGuavaCache() {
        CacheBuilder<Object, Object> manuallyCreatedCacheBuilder = CacheBuilder.newBuilder().maximumSize(30).softValues();
        Cache<String, MyValue> compressedCache = CompressedCacheFactory.getOrCreateCache("fast-test-manual", manuallyCreatedCacheBuilder, String.class, MyValue.class);

        compressedCache.put("v1", new MyValue("test", 42));

        assertEquals("test", compressedCache.get("v1").getLabel());
        assertEquals(42, compressedCache.get("v1").getValue());
    }

    /**
     * Verifies SMALL-mode put and get with a manually configured Guava cache builder.
     */
    @Test
    void smallPutAndGetWithCustomGuavaCache() {
        CacheBuilder<Object, Object> manuallyCreatedCacheBuilder = CacheBuilder.newBuilder().maximumSize(40).softValues();
        Cache<String, MyValue> compressedCache = CompressedCacheFactory.getOrCreateCache("small-test-manual", manuallyCreatedCacheBuilder, String.class, MyValue.class, CompressedValueMode.SMALL);

        compressedCache.put("v1", new MyValue("test", 42));

        assertEquals("test", compressedCache.get("v1").getLabel());
        assertEquals(42, compressedCache.get("v1").getValue());
    }
}
