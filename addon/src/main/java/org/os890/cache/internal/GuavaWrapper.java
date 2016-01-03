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
package org.os890.cache.internal;

import com.google.common.cache.CacheBuilder;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteKernal;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.marshaller.Marshaller;
import org.apache.ignite.marshaller.MarshallerContext;
import org.apache.ignite.marshaller.optimized.OptimizedMarshaller;
import org.os890.cache.CompressedEntry;
import org.os890.cache.CompressedValueMode;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class GuavaWrapper<K, V> implements Cache<K, V> {
    private final String cacheName;
    private final CompressedValueMode compressedValueMode;
    private final Marshaller marshaller;

    private com.google.common.cache.Cache<K, CompressedEntry<V>> wrappedCache;
    private boolean closed;

    public GuavaWrapper(String cacheName, CacheBuilder cacheBuilder, CompressedValueMode compressedValueMode) {
        this.cacheName = cacheName;
        wrappedCache = cacheBuilder.build();
        this.compressedValueMode = compressedValueMode;
        this.marshaller = createMarshaller(compressedValueMode);
    }

    @Override
    public V get(K key) {
        CompressedEntry<V> compressedEntry = wrappedCache.getIfPresent(key);

        if (compressedEntry != null) {
            return compressedEntry.getUncompressedValue();
        }
        return null;
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        Map<K, CompressedEntry<V>> foundEntries = wrappedCache.getAllPresent(keys);

        Map<K, V> result = new HashMap<>();
        if (foundEntries != null) {
            for (Map.Entry<K, CompressedEntry<V>> currentEntry : foundEntries.entrySet()) {
                if (currentEntry.getKey() != null && currentEntry.getValue() != null) {
                    result.put(currentEntry.getKey(), currentEntry.getValue().getUncompressedValue());
                }
            }
        }
        return result;
    }

    @Override
    public boolean containsKey(K key) {
        return wrappedCache.getIfPresent(key) != null;
    }

    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public void put(K key, V value) {
        if (key != null && value != null) {
            CompressedEntry<V> entry = createCompressedEntry(value);
            if (entry.isValid()) {
                wrappedCache.put(key, entry);
            } else {
                throw new IllegalStateException("it wasn't possible to compress and store the given value for " + key);
            }
        }
    }

    @Override
    public V getAndPut(K key, V value) {
        CompressedEntry<V> foundEntry = wrappedCache.getIfPresent(key);

        SmallCompressedEntry<V> newEntry = new SmallCompressedEntry<V>(value, this.marshaller);
        if (newEntry.isValid()) {
            wrappedCache.put(key, newEntry);
        }

        if (foundEntry != null) {
            return foundEntry.getUncompressedValue();
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> currentEntry : map.entrySet()) {
            put(currentEntry.getKey(), currentEntry.getValue());
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (!containsKey(key)) {
            put(key, value);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(K key) {
        if (containsKey(key)) {
            wrappedCache.invalidate(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        CompressedEntry<V> foundValue = wrappedCache.getIfPresent(key);

        if (foundValue != null) {
            V value = foundValue.getUncompressedValue();
            if (oldValue.equals(value)) {
                wrappedCache.invalidate(key);
                return true;
            }
        }
        return false;
    }

    @Override
    public V getAndRemove(K key) {
        CompressedEntry<V> foundValue = wrappedCache.getIfPresent(key);

        if (foundValue != null) {
            wrappedCache.invalidate(key);
            return foundValue.getUncompressedValue();
        }
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        CompressedEntry<V> foundValue = wrappedCache.getIfPresent(key);

        if (foundValue != null) {
            V value = foundValue.getUncompressedValue();
            if (oldValue.equals(value)) {
                SmallCompressedEntry<V> newEntry = new SmallCompressedEntry<>(newValue, this.marshaller);
                wrappedCache.put(key, newEntry);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean replace(K key, V value) {
        if (containsKey(key)) {
            try {
                put(key, value);
            } catch (IllegalStateException e) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public V getAndReplace(K key, V value) {
        CompressedEntry<V> foundEntry = wrappedCache.getIfPresent(key);

        if (foundEntry != null) {
            SmallCompressedEntry<V> newEntry = new SmallCompressedEntry<V>(value, this.marshaller);
            if (newEntry.isValid()) {
                wrappedCache.put(key, newEntry);
                return foundEntry.getUncompressedValue();
            }
        }
        return null;
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        wrappedCache.invalidateAll(keys);
    }

    @Override
    public void removeAll() {
        //TODO
        wrappedCache.invalidateAll();
        wrappedCache.cleanUp();
    }

    @Override
    public void clear() {
        wrappedCache.invalidateAll();
        wrappedCache.cleanUp();
    }

    @Override
    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public String getName() {
        return this.cacheName;
    }

    @Override
    public CacheManager getCacheManager() {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        throw new UnsupportedOperationException("currently not supported");
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        throw new UnsupportedOperationException("currently not supported");
    }

    private CompressedEntry<V> createCompressedEntry(V value) {
        switch (compressedValueMode) {
            case FAST:
                return new FastCompressedEntry<>(value, this.marshaller);
            case SMALL:
                return new SmallCompressedEntry<>(value, this.marshaller);
            default:
                throw new IllegalStateException(compressedValueMode.name() + " isn't supported");
        }
    }

    private Marshaller createMarshaller(CompressedValueMode compressedValueMode) {
        Ignite ignite = Ignition.ignite();
        GridKernalContext context = ((IgniteKernal) ignite).context();
        Marshaller exposedMarshaller = context.grid().configuration().getMarshaller();

        if (exposedMarshaller instanceof BinaryMarshaller && compressedValueMode == CompressedValueMode.SMALL) {
            BinaryMarshaller binaryMarshaller = (BinaryMarshaller) exposedMarshaller;
            OptimizedMarshaller optimizedMarshaller = new OptimizedMarshaller(false);
            MarshallerContext marshallerContext = binaryMarshaller.getContext();
            optimizedMarshaller.setContext(marshallerContext);
            return optimizedMarshaller;
        } else {
            return exposedMarshaller;
        }
    }
}
