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

import org.apache.ignite.marshaller.Marshaller;
import org.os890.cache.CompressedEntry;

/**
 * Base class for compressed cache entries.
 *
 * <p>Holds the marshaller used for value serialisation and the GZIP-compressed
 * byte array produced by subclasses.  A {@code failureFound} flag short-circuits
 * further compression or decompression attempts after an error.</p>
 *
 * @param <V> the type of the uncompressed value
 */
public abstract class AbstractCompressedEntry<V> implements CompressedEntry<V> {

    /** Marshaller used to serialise and deserialise values before GZIP compression. */
    protected final Marshaller marshaller;

    /** GZIP-compressed representation of the value, set by subclasses. */
    protected byte[] compressedValue;

    /** Set to {@code true} if a compression or decompression error occurred. */
    protected boolean failureFound; //false per default

    /**
     * Creates a new entry with the given marshaller.
     *
     * @param marshaller the marshaller to use for value serialisation
     */
    public AbstractCompressedEntry(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
