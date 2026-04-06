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
 * Controls the trade-off between access speed and memory usage for compressed cache entries.
 */
public enum CompressedValueMode {

    /**
     * Keeps a {@link java.lang.ref.SoftReference} to the uncompressed value alongside
     * the compressed bytes, allowing faster repeated reads at the cost of higher memory usage.
     * The JVM may discard the soft reference under memory pressure, falling back to
     * decompression.
     */
    FAST,

    /**
     * Stores only the compressed bytes without a soft reference to the uncompressed value.
     * Every read decompresses the value, using less memory than {@link #FAST}.
     */
    SMALL
}
