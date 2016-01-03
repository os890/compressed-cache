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
import org.junit.Assert;
import org.junit.Test;
import org.os890.cache.CompressedCacheFactory;
import org.os890.cache.CompressedValueMode;

import javax.cache.Cache;

public class UsageTest {
    @Test
    public void fastPutAndGet() {
        CompressedCacheFactory.getOrCreateSimpleCache("fast-test-simple", 10, String.class, MyValue.class)
                .put("v1", new MyValue("test", 42));

        Cache<String, MyValue> myValueCache = CompressedCacheFactory.getOrCreateSimpleCache("fast-test-simple", 10, String.class, MyValue.class);
        Assert.assertEquals("test", myValueCache.get("v1").getLabel());
        Assert.assertEquals(42, myValueCache.get("v1").getValue());
    }

    @Test
    public void smallPutAndGet() {
        CompressedCacheFactory.getOrCreateSimpleCache("small-test-simple", 10, String.class, MyValue.class, CompressedValueMode.SMALL)
                .put("v1", new MyValue("test", 42));

        Cache<String, MyValue> myValueCache = CompressedCacheFactory.getOrCreateSimpleCache("small-test-simple", 20, String.class, MyValue.class, CompressedValueMode.SMALL /*optional*/);
        Assert.assertEquals("test", myValueCache.get("v1").getLabel());
        Assert.assertEquals(42, myValueCache.get("v1").getValue());
    }

    @Test
    public void fastPutAndGetWithCustomGuavaCache() {
        CacheBuilder manuallyCreatedCacheBuilder = CacheBuilder.newBuilder().maximumSize(30).softValues();
        Cache<String, MyValue> compressedCache = CompressedCacheFactory.getOrCreateCache("fast-test-manual", manuallyCreatedCacheBuilder, String.class, MyValue.class);

        compressedCache.put("v1", new MyValue("test", 42));

        Assert.assertEquals("test", compressedCache.get("v1").getLabel());
        Assert.assertEquals(42, compressedCache.get("v1").getValue());
    }

    @Test
    public void smallPutAndGetWithCustomGuavaCache() {
        CacheBuilder manuallyCreatedCacheBuilder = CacheBuilder.newBuilder().maximumSize(40).softValues();
        Cache<String, MyValue> compressedCache = CompressedCacheFactory.getOrCreateCache("small-test-manual", manuallyCreatedCacheBuilder, String.class, MyValue.class, CompressedValueMode.SMALL);

        compressedCache.put("v1", new MyValue("test", 42));

        Assert.assertEquals("test", compressedCache.get("v1").getLabel());
        Assert.assertEquals(42, compressedCache.get("v1").getValue());
    }
}
