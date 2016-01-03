package org.os890.cache.internal;

import org.apache.ignite.marshaller.Marshaller;
import org.os890.cache.CompressedEntry;

public abstract class AbstractCompressedEntry<V> implements CompressedEntry<V> {
    protected final Marshaller marshaller;
    protected byte[] compressedValue;
    protected boolean failureFound; //false per default

    public AbstractCompressedEntry(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
