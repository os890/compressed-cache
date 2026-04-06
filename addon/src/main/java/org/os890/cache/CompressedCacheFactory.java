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
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.os890.cache.internal.GuavaWrapper;

import javax.cache.Cache;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing {@link Cache} instances backed by Guava
 * with transparent value compression (GZIP via {@link CompressedEntry}).
 *
 * <p>On first use, optionally starts an Apache Ignite node (controlled by the
 * {@code org.os890.cache.START_IGNITE} system property, default {@code true})
 * to provide the binary marshaller used for value serialisation.</p>
 *
 * <p>Cache instances are stored by name and reused across calls with the same name.</p>
 */
public class CompressedCacheFactory {

    private static Map<String, Cache<?, ?>> cacheMap = new ConcurrentHashMap<>();

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

            // Ignite 2.17 removed IgniteConfiguration.setDaemon(); the node now runs
            // as a non-daemon server by default.  BinaryMarshaller is set explicitly so
            // that GuavaWrapper can retrieve it from the running configuration.
            BinaryMarshaller binaryMarshaller = new BinaryMarshaller();
            // Ignite deprecated setMarshaller/getMarshaller but no replacement
            // exists for explicitly configuring the marshaller.
            @SuppressWarnings("deprecation")
            IgniteConfiguration configuration = new IgniteConfiguration()
                    .setDiscoverySpi(nodeDiscovery)
                    .setMarshaller(binaryMarshaller);
            Ignition.start(configuration);
        }
    }

    /**
     * Creates or retrieves a cache with the given name and maximum size,
     * using {@link CompressedValueMode#FAST FAST} compression.
     *
     * @param <K>       key type
     * @param <V>       value type
     * @param cacheName unique cache name
     * @param maxSize   maximum number of entries
     * @param keyClass  key class (unused at runtime, for type inference)
     * @param valueClass value class (unused at runtime, for type inference)
     * @return the named cache
     */
    public static <K, V> Cache<K, V> getOrCreateSimpleCache(String cacheName, long maxSize, Class<K> keyClass, Class<V> valueClass) {
        return getOrCreateCache(cacheName, CacheBuilder.newBuilder().maximumSize(maxSize), keyClass, valueClass, CompressedValueMode.FAST);
    }

    /**
     * Creates or retrieves a cache with the given name, maximum size, and compression mode.
     *
     * @param <K>                key type
     * @param <V>                value type
     * @param cacheName          unique cache name
     * @param maxSize            maximum number of entries
     * @param keyClass           key class (unused at runtime, for type inference)
     * @param valueClass         value class (unused at runtime, for type inference)
     * @param compressedValueMode compression strategy to use
     * @return the named cache
     */
    public static <K, V> Cache<K, V> getOrCreateSimpleCache(String cacheName, long maxSize, Class<K> keyClass, Class<V> valueClass, CompressedValueMode compressedValueMode) {
        return getOrCreateCache(cacheName, CacheBuilder.newBuilder().maximumSize(maxSize), keyClass, valueClass, compressedValueMode);
    }

    /**
     * Creates or retrieves a cache with the given name and Guava cache builder,
     * using {@link CompressedValueMode#FAST FAST} compression.
     *
     * @param <K>                  key type
     * @param <V>                  value type
     * @param cacheName            unique cache name
     * @param providedCacheBuilder Guava cache builder controlling eviction and other settings
     * @param keyClass             key class (unused at runtime, for type inference)
     * @param valueClass           value class (unused at runtime, for type inference)
     * @return the named cache
     */
    public static <K, V> Cache<K, V> getOrCreateCache(String cacheName, CacheBuilder<Object, Object> providedCacheBuilder, Class<K> keyClass, Class<V> valueClass) {
        return getOrCreateCache(cacheName, providedCacheBuilder, keyClass, valueClass, CompressedValueMode.FAST);
    }

    /**
     * Creates or retrieves a cache with the given name, Guava cache builder, and compression mode.
     *
     * @param <K>                  key type
     * @param <V>                  value type
     * @param cacheName            unique cache name
     * @param providedCacheBuilder Guava cache builder controlling eviction and other settings
     * @param keyClass             key class (unused at runtime, for type inference)
     * @param valueClass           value class (unused at runtime, for type inference)
     * @param compressedValueMode  compression strategy to use
     * @return the named cache
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Cache<K, V> getOrCreateCache(String cacheName, CacheBuilder<Object, Object> providedCacheBuilder, Class<K> keyClass, Class<V> valueClass, CompressedValueMode compressedValueMode) {
        Cache<K, V> foundCache = (Cache<K, V>) cacheMap.get(cacheName);

        if (foundCache != null) {
            return foundCache;
        }
        return createCache(cacheName, providedCacheBuilder, compressedValueMode);
    }

    @SuppressWarnings("unchecked")
    private static synchronized <K, V> Cache<K, V> createCache(String cacheName, CacheBuilder<Object, Object> cacheBuilder, CompressedValueMode compressedValueMode) {
        Cache<K, V> foundCache = (Cache<K, V>) cacheMap.get(cacheName);

        if (foundCache != null) {
            return foundCache;
        }

        Cache<K, V> newCache = new GuavaWrapper<>(cacheName, cacheBuilder, compressedValueMode);
        cacheMap.put(cacheName, newCache);
        return newCache;
    }
}
