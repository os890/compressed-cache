# Compressed Cache

A Java library providing transparent value compression for in-memory caches.
Values are serialised via the Apache Ignite binary marshaller and compressed with GZIP
before being stored in a Guava in-memory cache.  Decompression happens on-demand when a
value is read.

## Overview

The library wraps Guava's `Cache` with a JCache-compatible `Cache<K,V>` interface.
Two compression modes control the memory/speed trade-off:

- **FAST** — keeps a `SoftReference` to the uncompressed value alongside the compressed bytes.
  Repeated reads are fast; the JVM may discard the soft reference under memory pressure.
- **SMALL** — stores only the compressed bytes.  Every read decompresses the value,
  using less memory than FAST.

Internally an Apache Ignite node is started (unless the system property
`org.os890.cache.START_IGNITE=false` is set) to provide the binary marshaller used
for value serialisation.

## Requirements

- Java 25+
- Maven 3.6.3+
- Apache Ignite 2.17+ (provided scope — bring your own)
- Guava 33.x (provided scope — bring your own)
- Apache Commons Compress 1.28+ (provided scope — bring your own)
- JCache API 1.1+ (provided scope — bring your own)

## Usage

```java
// Simple cache with maximum 1000 entries, FAST mode
Cache<String, MyData> cache = CompressedCacheFactory
        .getOrCreateSimpleCache("my-cache", 1000, String.class, MyData.class);

cache.put("key", new MyData(...));
MyData value = cache.get("key");

// SMALL mode
Cache<String, MyData> smallCache = CompressedCacheFactory
        .getOrCreateSimpleCache("my-small-cache", 1000, String.class, MyData.class,
                CompressedValueMode.SMALL);

// Custom Guava cache builder
CacheBuilder builder = CacheBuilder.newBuilder().maximumSize(500).softValues();
Cache<String, MyData> custom = CompressedCacheFactory
        .getOrCreateCache("my-custom-cache", builder, String.class, MyData.class);
```

## Build

```bash
mvn clean verify
```

## Testing

Tests use JUnit Jupiter 6.x.  The Surefire plugin is configured with the `--add-opens`
flags required for Apache Ignite to run correctly on Java 25.

See [Dynamic CDI Test Bean Addon](https://github.com/os890/dynamic-cdi-test-bean-addon)
for the CDI SE testing approach used in related projects.

## Quality

The build enforces:
- Java 25, Maven 3.6.3+
- Checkstyle (0 violations)
- Apache RAT license headers (0 unapproved)
- Compilation (0 warnings with -Xlint:all)
- Dependency convergence
- JaCoCo coverage reporting

## License

Apache License 2.0 — see [LICENSE](LICENSE)
