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
package org.os890.cache;

import com.google.common.cache.CacheBuilder;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.os890.cache.internal.GuavaWrapper;

import javax.cache.Cache;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompressedCacheFactory {
    private static Map<String, Cache> cacheMap = new ConcurrentHashMap<>();

    static {
        boolean startIgnite = "true".equalsIgnoreCase(System.getProperty("org.os890.cache.START_IGNITE", "true"));

        if (startIgnite) {
            System.setProperty("IGNITE_UPDATE_NOTIFIER", "false");
            System.setProperty("IGNITE_QUIET", "true");
            System.setProperty("IGNITE_NO_ASCII", "true");

            TcpDiscoverySpi nodeDiscovery = new TcpDiscoverySpi();
            TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
            ipFinder.setAddresses(Collections.singletonList("127.0.0.1"));
            nodeDiscovery.setIpFinder(ipFinder);

            IgniteConfiguration configuration = new IgniteConfiguration()
                    .setDiscoverySpi(nodeDiscovery)
                    .setDaemon(false);
            Ignition.start(configuration);
        }
    }

    public static <K, V> Cache<K, V> getOrCreateSimpleCache(String cacheName, long maxSize, Class<K> keyClass, Class<V> valueClass) {
        return getOrCreateCache(cacheName, CacheBuilder.newBuilder().maximumSize(maxSize), keyClass, valueClass, CompressedValueMode.FAST);
    }

    public static <K, V> Cache<K, V> getOrCreateSimpleCache(String cacheName, long maxSize, Class<K> keyClass, Class<V> valueClass, CompressedValueMode compressedValueMode) {
        return getOrCreateCache(cacheName, CacheBuilder.newBuilder().maximumSize(maxSize), keyClass, valueClass, compressedValueMode);
    }

    public static <K, V> Cache<K, V> getOrCreateCache(String cacheName, CacheBuilder providedCacheBuilder, Class<K> keyClass, Class<V> valueClass) {
        return getOrCreateCache(cacheName, providedCacheBuilder, keyClass, valueClass, CompressedValueMode.FAST);
    }

    public static <K, V> Cache<K, V> getOrCreateCache(String cacheName, CacheBuilder providedCacheBuilder, Class<K> keyClass, Class<V> valueClass, CompressedValueMode compressedValueMode) {
        Cache<K, V> foundCache = cacheMap.get(cacheName);

        if (foundCache != null) {
            return foundCache;
        }
        return createCache(cacheName, providedCacheBuilder, compressedValueMode);
    }

    private static synchronized <K, V> Cache<K, V> createCache(String cacheName, CacheBuilder cacheBuilder, CompressedValueMode compressedValueMode) {
        Cache<K, V> foundCache = cacheMap.get(cacheName);

        if (foundCache != null) {
            return foundCache;
        }

        Cache<K, V> newCache = new GuavaWrapper<>(cacheName, cacheBuilder, compressedValueMode);
        cacheMap.put(cacheName, newCache);
        return newCache;
    }
}
