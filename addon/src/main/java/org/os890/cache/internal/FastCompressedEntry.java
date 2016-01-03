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

import org.apache.commons.compress.utils.IOUtils;
import org.apache.ignite.marshaller.Marshaller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.SoftReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FastCompressedEntry<V> extends AbstractCompressedEntry<V> {
    private transient SoftReference<V> transientValueRef; //stores the uncompressed value for a faster access - will be dropped if there isn't enough memory or the value gets replicated

    FastCompressedEntry(V value, Marshaller marshaller) {
        super(marshaller);
        this.transientValueRef = new SoftReference<V>(value);
        compressToByteArray();
    }

    public V getUncompressedValue() {
        V currentValue = null;

        SoftReference<V> currentValueRef = this.transientValueRef;
        if (!isTransientValueRefEmpty(currentValueRef)) {
            currentValue = currentValueRef.get();
        }

        if (currentValue == null & compressedValue != null) {
            currentValue = restoreFromByteArray();
        }

        return currentValue;
    }

    private void createNewTransientValueRef(V value) {
        this.transientValueRef = new SoftReference<V>(value);
    }

    public boolean isValid() {
        if (failureFound) {
            return false;
        }
        SoftReference<V> currentValueRef = this.transientValueRef;
        return !isTransientValueRefEmpty(currentValueRef) || compressedValue != null;
    }

    private void compressToByteArray() {
        if (compressedValue != null || failureFound) {
            return;
        }

        try {
            SoftReference<V> currentValueRef = this.transientValueRef;

            if (isTransientValueRefEmpty(currentValueRef)) {
                return;
            }

            V valueToArchive = currentValueRef.get();

            if (valueToArchive == null) {
                return;
            }

            byte[] valueAsByes = marshaller.marshal(valueToArchive);
            byte[] compressedOutput;

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                 ByteArrayInputStream inputStream = new ByteArrayInputStream(valueAsByes);) {
                IOUtils.copy(inputStream, gzipOutputStream);
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
        if (compressedValue == null || failureFound) {
            return null;
        }

        SoftReference<V> currentValueRef = this.transientValueRef;
        if (currentValueRef != null) {
            V currentValue = currentValueRef.get();

            if (currentValue != null) {
                return currentValue;
            }
        }

        byte[] valueToDecompress = this.compressedValue;

        try {
            byte[] uncompressedValue;

            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(valueToDecompress))) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                IOUtils.copy(gzipInputStream, outputStream);
                uncompressedValue = outputStream.toByteArray();
            }

            if (uncompressedValue != null) {
                V result = marshaller.unmarshal(uncompressedValue, uncompressedValue.getClass().getClassLoader());
                createNewTransientValueRef(result);
                return result;
            }
        } catch (Exception e) {
            //TODO logging
            failureFound = true;
        }
        return null;
    }

    private boolean isTransientValueRefEmpty(SoftReference<V> valueRef) {
        return valueRef == null || valueRef.get() == null;
    }
}
