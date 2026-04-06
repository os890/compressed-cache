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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A compressed cache entry that stores only the GZIP-compressed bytes without
 * a soft reference to the uncompressed value ({@link org.os890.cache.CompressedValueMode#SMALL SMALL} mode).
 *
 * <p>Every read decompresses and deserialises the value, which is slower than
 * {@link FastCompressedEntry} but uses less memory.</p>
 *
 * @param <V> the type of the uncompressed value
 */
public class SmallCompressedEntry<V> extends AbstractCompressedEntry<V> {

    /**
     * Creates a new small entry by compressing the given value.
     *
     * @param value      the value to compress
     * @param marshaller the marshaller used to serialise the value before compression
     */
    SmallCompressedEntry(V value, Marshaller marshaller) {
        super(marshaller);
        compressToByteArray(value);
    }

    @Override
    public V getUncompressedValue() {
        if (compressedValue != null) {
            return restoreFromByteArray();
        }
        return null;
    }

    @Override
    public boolean isValid() {
        return !failureFound && compressedValue != null;
    }

    private void compressToByteArray(V value) {
        if (compressedValue != null || failureFound) {
            return;
        }

        try {
            if (value == null) {
                return;
            }

            byte[] valueAsByes = marshaller.marshal(value);
            byte[] compressedOutput;

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                 ByteArrayInputStream inputStream = new ByteArrayInputStream(valueAsByes);) {
                inputStream.transferTo(gzipOutputStream);
                gzipOutputStream.finish();
                compressedOutput = outputStream.toByteArray();
            }

            this.compressedValue = compressedOutput;
        } catch (Exception e) {
            //TODO logging
            failureFound = true;
        }
    }

    private V restoreFromByteArray() {
        if (compressedValue == null) {
            return null;
        }

        byte[] valueToDecompress = this.compressedValue;

        try {
            byte[] uncompressedValue;

            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(valueToDecompress))) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                gzipInputStream.transferTo(outputStream);
                uncompressedValue = outputStream.toByteArray();
            }

            if (uncompressedValue != null) {
                V result = marshaller.unmarshal(uncompressedValue, uncompressedValue.getClass().getClassLoader());
                return result;
            }
        } catch (Exception e) {
            //TODO logging
            failureFound = true;
        }
        return null;
    }
}
