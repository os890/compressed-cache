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
import org.apache.ignite.marshaller.Marshaller;
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

/**
 * JCache {@link Cache} implementation backed by a Guava in-memory cache.
 *
 * <p>Values are serialised with the Ignite {@link Marshaller} and compressed with GZIP
 * before being stored.  Decompression happens on-demand when a value is read.
 * The compression strategy (FAST or SMALL) determines whether a soft reference to
 * the uncompressed value is kept between reads.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class GuavaWrapper<K, V> implements Cache<K, V> {

    private final String cacheName;
    private final CompressedValueMode compressedValueMode;
    private final Marshaller marshaller;

    private com.google.common.cache.Cache<K, CompressedEntry<V>> wrappedCache;
    private boolean closed;

    /**
     * Creates a new wrapper with the given name, Guava cache builder and compression mode.
     *
     * @param cacheName           unique name for this cache
     * @param cacheBuilder        Guava cache builder controlling eviction and size limits
     * @param compressedValueMode compression strategy
     */
    public GuavaWrapper(String cacheName, CacheBuilder<Object, Object> cacheBuilder, CompressedValueMode compressedValueMode) {
        this.cacheName = cacheName;
        wrappedCache = cacheBuilder.build();
        this.compressedValueMode = compressedValueMode;
        this.marshaller = createMarshaller(compressedValueMode);
    }

    /**
     * Returns the value associated with the given key, or {@code null} if no mapping exists.
     *
     * @param key the key whose associated value is to be returned
     * @return the value mapped to the key, or {@code null} if absent
     */
    @Override
    public V get(K key) {
        CompressedEntry<V> compressedEntry = wrappedCache.getIfPresent(key);

        if (compressedEntry != null) {
            return compressedEntry.getUncompressedValue();
        }
        return null;
    }

    /**
     * Returns a map of the values associated with the given keys.
     *
     * @param keys the keys whose associated values are to be returned
     * @return a map of keys to their values for each key that has a mapping
     */
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

    /**
     * Returns {@code true} if this cache contains a mapping for the given key.
     *
     * @param key the key to check
     * @return {@code true} if a mapping exists for the key
     */
    @Override
    public boolean containsKey(K key) {
        return wrappedCache.getIfPresent(key) != null;
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param keys                   the keys to load
     * @param replaceExistingValues  whether to replace existing values
     * @param completionListener     listener notified on completion
     * @throws UnsupportedOperationException always
     */
    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Stores the given key-value pair in the cache after compressing the value.
     *
     * @param key   the key to associate the value with
     * @param value the value to store
     * @throws IllegalStateException if the value cannot be compressed
     */
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

    /**
     * Associates the value with the key and returns the previously associated value, if any.
     *
     * @param key   the key
     * @param value the new value
     * @return the previous value, or {@code null} if there was no mapping
     */
    @Override
    public V getAndPut(K key, V value) {
        CompressedEntry<V> foundEntry = wrappedCache.getIfPresent(key);

        SmallCompressedEntry<V> newEntry = new SmallCompressedEntry<>(value, this.marshaller);
        if (newEntry.isValid()) {
            wrappedCache.put(key, newEntry);
        }

        if (foundEntry != null) {
            return foundEntry.getUncompressedValue();
        }
        return null;
    }

    /**
     * Stores all key-value pairs from the given map in the cache.
     *
     * @param map the key-value pairs to store
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> currentEntry : map.entrySet()) {
            put(currentEntry.getKey(), currentEntry.getValue());
        }
    }

    /**
     * Stores the value only if no mapping for the key already exists.
     *
     * @param key   the key
     * @param value the value to store if absent
     * @return {@code true} if the value was stored, {@code false} if a mapping already existed
     */
    @Override
    public boolean putIfAbsent(K key, V value) {
        if (!containsKey(key)) {
            put(key, value);
            return true;
        }
        return false;
    }

    /**
     * Removes the mapping for the given key if it exists.
     *
     * @param key the key to remove
     * @return {@code true} if the mapping was removed, {@code false} if no mapping existed
     */
    @Override
    public boolean remove(K key) {
        if (containsKey(key)) {
            wrappedCache.invalidate(key);
            return true;
        }
        return false;
    }

    /**
     * Removes the mapping for the key only if it is currently mapped to the given value.
     *
     * @param key      the key
     * @param oldValue the value that must match the current mapping
     * @return {@code true} if the mapping was removed
     */
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

    /**
     * Removes and returns the value associated with the given key.
     *
     * @param key the key to remove
     * @return the previously associated value, or {@code null} if no mapping existed
     */
    @Override
    public V getAndRemove(K key) {
        CompressedEntry<V> foundValue = wrappedCache.getIfPresent(key);

        if (foundValue != null) {
            wrappedCache.invalidate(key);
            return foundValue.getUncompressedValue();
        }
        return null;
    }

    /**
     * Replaces the value for the key only if it is currently mapped to the given old value.
     *
     * @param key      the key
     * @param oldValue the expected current value
     * @param newValue the new value to store
     * @return {@code true} if the value was replaced
     */
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

    /**
     * Replaces the value for the key if a mapping already exists.
     *
     * @param key   the key
     * @param value the new value to store
     * @return {@code true} if the value was replaced, {@code false} if no mapping existed
     */
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

    /**
     * Replaces the value for the key and returns the old value, if a mapping exists.
     *
     * @param key   the key
     * @param value the new value
     * @return the previously associated value, or {@code null} if no mapping existed
     */
    @Override
    public V getAndReplace(K key, V value) {
        CompressedEntry<V> foundEntry = wrappedCache.getIfPresent(key);

        if (foundEntry != null) {
            SmallCompressedEntry<V> newEntry = new SmallCompressedEntry<>(value, this.marshaller);
            if (newEntry.isValid()) {
                wrappedCache.put(key, newEntry);
                return foundEntry.getUncompressedValue();
            }
        }
        return null;
    }

    /**
     * Removes the mappings for the given keys.
     *
     * @param keys the keys to remove
     */
    @Override
    public void removeAll(Set<? extends K> keys) {
        wrappedCache.invalidateAll(keys);
    }

    /**
     * Removes all mappings from the cache.
     */
    @Override
    public void removeAll() {
        //TODO
        wrappedCache.invalidateAll();
        wrappedCache.cleanUp();
    }

    /**
     * Clears all entries from the cache.
     */
    @Override
    public void clear() {
        wrappedCache.invalidateAll();
        wrappedCache.cleanUp();
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param <C>  the configuration type
     * @param clazz the configuration class to return
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param <T>            the return type of the entry processor
     * @param key            the key to process
     * @param entryProcessor the processor to invoke
     * @param arguments      additional arguments for the processor
     * @return never returns normally
     * @throws EntryProcessorException never (operation is unsupported)
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param <T>            the return type of the entry processor
     * @param keys           the keys to process
     * @param entryProcessor the processor to invoke
     * @param arguments      additional arguments for the processor
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Returns the name of this cache.
     *
     * @return the cache name
     */
    @Override
    public String getName() {
        return this.cacheName;
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public CacheManager getCacheManager() {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Marks this cache as closed.
     */
    @Override
    public void close() {
        this.closed = true;
    }

    /**
     * Returns whether this cache has been closed.
     *
     * @return {@code true} if the cache is closed
     */
    @Override
    public boolean isClosed() {
        return this.closed;
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param <T>   the type to unwrap to
     * @param clazz the class to unwrap to
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param cacheEntryListenerConfiguration the listener configuration to register
     * @throws UnsupportedOperationException always
     */
    @Override
    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @param cacheEntryListenerConfiguration the listener configuration to deregister
     * @throws UnsupportedOperationException always
     */
    @Override
    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        throw new UnsupportedOperationException("currently not supported");
    }

    /**
     * Not supported. Always throws {@link UnsupportedOperationException}.
     *
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
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

    /**
     * Obtains the marshaller from the running Ignite instance.
     *
     * <p>Prior to Ignite 2.17, SMALL mode used {@code OptimizedMarshaller} for
     * smaller serialised output.  That class was moved to an internal package
     * ({@code org.apache.ignite.internal.marshaller.optimized}) in 2.17 and is
     * no longer part of the public API, so the configured marshaller
     * ({@code BinaryMarshaller}) is now used for all modes.</p>
     *
     * @param compressedValueMode the compression mode (retained for future use)
     * @return the marshaller configured on the Ignite node
     */
    // Ignite deprecated getMarshaller() but no replacement exists for
    // retrieving the configured marshaller from a running node.
    @SuppressWarnings("deprecation")
    private Marshaller createMarshaller(CompressedValueMode compressedValueMode) {
        Ignite ignite = Ignition.ignite();
        GridKernalContext context = ((IgniteKernal) ignite).context();
        return context.grid().configuration().getMarshaller();
    }
}
