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

/**
 * Represents a cache entry whose value is stored in compressed form.
 *
 * <p>Implementations keep the serialised and GZIP-compressed value and
 * decompress it on demand when {@link #getUncompressedValue()} is called.</p>
 *
 * @param <V> the type of the uncompressed value
 */
public interface CompressedEntry<V> {

    /**
     * Returns {@code true} if this entry can still produce a value.
     *
     * <p>An entry becomes invalid if compression or decompression failed.</p>
     *
     * @return {@code true} if the entry is valid
     */
    boolean isValid();

    /**
     * Returns the decompressed value stored in this entry.
     *
     * @return the original uncompressed value, or {@code null} if unavailable
     */
    V getUncompressedValue();
}
