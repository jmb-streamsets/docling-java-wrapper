# Modular Architecture (v2.0+)

## Overview

Starting with v2.0, Docling Java Wrapper provides a **modular architecture** with completely decoupled:

1. **HTTP Transport** - Choose any HTTP client (Native, Apache, OkHttp)
2. **JSON Serialization** - Choose any JSON library (Jackson, Gson, Moshi)
3. **Domain Models** - Pure POJOs with zero annotations

The v1.x API continues to work unchanged for backward compatibility.

## Architecture Diagram

```
┌─────────────────────────────────────────────────┐
│         Pure Domain Models (docling-api)        │
│  • No JSON annotations                          │
│  • No HTTP dependencies                         │
│  • Pure Java POJOs                              │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│   Service Provider Interfaces (docling-spi)     │
│  • HttpTransport interface                      │
│  • JsonSerializer interface                     │
└─────────────────────────────────────────────────┘
                      ↓
        ┌─────────────┴─────────────┐
        ↓                           ↓
┌──────────────────┐      ┌──────────────────┐
│ JSON Adapters    │      │ HTTP Adapters    │
├──────────────────┤      ├──────────────────┤
│ • Jackson        │      │ • Native         │
│ • Gson           │      │ • Apache         │
│ • Moshi          │      │ • OkHttp         │
└──────────────────┘      └──────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│      ModularDoclingClient (v2.0 API)            │
│  • Uses HttpTransport abstraction              │
│  • Uses JsonSerializer abstraction             │
│  • ServiceLoader auto-discovery                │
└─────────────────────────────────────────────────┘
```

## Quick Start

### Option 1: Legacy API (v1.x) - Still Works!

```gradle
dependencies {
    implementation 'com.docling:docling-java-wrapper:1.0.0'
}
```

```java
// Existing code continues to work unchanged
DoclingClient client = DoclingClient.fromEnv();
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file);
```

### Option 2: Modular API (v2.0) - New Architecture

```gradle
dependencies {
    // Core
    implementation 'com.docling:docling-client-modular:2.0.0'

    // Pick ONE JSON library
    implementation 'com.docling:docling-json-jackson:2.0.0'

    // Pick ONE HTTP transport
    implementation 'com.docling:docling-transport-native:2.0.0'
}
```

```java
// Auto-discovers Jackson + Native from classpath
ModularDoclingClient client = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .apiKey("my-key")
    .build();

System.out.println(client.getInfo());
// Output: ModularDoclingClient[transport=Native, serializer=Jackson, baseUrl=http://localhost:5001]

CompletableFuture<ConversionResponse> future =
    client.convertUrlAsync("https://example.com/doc.pdf", OutputFormat.MARKDOWN);
```

## Module Catalog

### Core Modules

| Module | Description | Dependencies |
|--------|-------------|--------------|
| **docling-api** | Pure domain models (POJOs) | Zero |
| **docling-spi** | Service Provider Interfaces | docling-api |
| **docling-client-modular** | Modular client implementation | docling-api, docling-spi |

### JSON Serialization Adapters

| Module | Library | Size | Best For |
|--------|---------|------|----------|
| **docling-json-jackson** | Jackson 2.17.1 | ~1 MB | General purpose, most popular |
| **docling-json-gson** | Gson 2.10.1 | ~500 KB | Android, lightweight |
| **docling-json-moshi** | Moshi | ~300 KB | Square ecosystem |

### HTTP Transport Adapters

| Module | Library | Size | Best For |
|--------|---------|------|----------|
| **docling-transport-native** | java.net.http | 0 KB | Java 11+, zero deps |
| **docling-transport-apache** | Apache HttpClient 5.3 | ~2 MB | Production, enterprise |
| **docling-transport-okhttp** | OkHttp 4.12 | ~400 KB | Modern, Android |

## Usage Examples

### Auto-Discovery (ServiceLoader)

```java
// Automatically discovers first implementation on classpath
ModularDoclingClient client = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .build();

// Check what was discovered
System.out.println(client.getInfo());
```

### Explicit Configuration

```java
// Explicitly specify implementations
HttpTransport transport = new ApacheHttpTransport();
JsonSerializer serializer = new GsonJsonSerializer();

ModularDoclingClient client = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .httpTransport(transport)
    .jsonSerializer(serializer)
    .apiKey("my-key")
    .build();
```

### Custom Implementations

```java
// Bring your own implementation
public class MyCustomHttpTransport implements HttpTransport {
    @Override
    public HttpResponse execute(HttpRequest request) {
        // Your implementation (e.g., wrapped OkHttp with custom interceptors)
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request) {
        // Your async implementation
    }

    @Override
    public String getName() {
        return "MyCustom";
    }

    @Override
    public void close() {
        // Cleanup
    }
}

ModularDoclingClient client = ModularDoclingClient.builder()
    .httpTransport(new MyCustomHttpTransport())
    .jsonSerializer(new JacksonJsonSerializer())
    .build();
```

## Mixing Both APIs

You can use both the legacy and modular APIs in the same project:

```gradle
dependencies {
    // Legacy API (v1.x)
    implementation 'com.docling:docling-java-wrapper:1.0.0'

    // Modular API (v2.0)
    implementation 'com.docling:docling-client-modular:2.0.0'
    implementation 'com.docling:docling-json-jackson:2.0.0'
    implementation 'com.docling:docling-transport-native:2.0.0'
}
```

```java
// Use legacy client for production code (stable, battle-tested)
DoclingClient legacyClient = DoclingClient.fromEnv();
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future1 =
    legacyClient.convertFileAsyncFuture(file);

// Use modular client for new features (clean, decoupled)
ModularDoclingClient modularClient = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .build();
CompletableFuture<ConversionResponse> future2 =
    modularClient.convertUrlAsync(url, OutputFormat.MARKDOWN);
```

## Migration Guide

### When to Use Which API?

**Use Legacy API (v1.x) when:**
- ✅ Existing code that works
- ✅ Need full feature parity (chunking, all output formats, etc.)
- ✅ Production systems (mature, tested)
- ✅ Don't need swappable HTTP/JSON

**Use Modular API (v2.0) when:**
- ✅ New greenfield projects
- ✅ Need to control HTTP client (corporate proxy, custom auth, etc.)
- ✅ Need to control JSON library (avoid Jackson conflicts, etc.)
- ✅ Want pure domain models (no annotations)
- ✅ Testing (easy to mock HttpTransport/JsonSerializer)

### Gradual Migration Path

```java
// Phase 1: Keep using legacy API
DoclingClient client = DoclingClient.fromEnv();

// Phase 2: Experiment with modular API alongside
ModularDoclingClient modular = ModularDoclingClient.builder()
    .baseUrl(client.getBaseUrl())
    .build();

// Phase 3: Migrate one feature at a time
// Keep legacy for critical paths, use modular for new features

// Phase 4: Full migration (when ready)
// Remove legacy dependency
```

## Dependency Matrix

Choose any combination:

| JSON | HTTP | Total Size | Use Case |
|------|------|-----------|----------|
| Jackson | Native | ~1 MB | **Recommended**: Modern Java, lightweight |
| Jackson | Apache | ~3 MB | Production-grade, mature |
| Jackson | OkHttp | ~1.5 MB | Modern, clean API |
| Gson | Native | ~500 KB | **Lightweight**: Minimal deps |
| Gson | Apache | ~2.5 MB | Gson preference + production HTTP |
| Gson | OkHttp | ~1 MB | **Android**: Native Android combo |

## Benefits of Modular Architecture

### 1. **Zero Vendor Lock-in**
```java
// Switch from Jackson to Gson without code changes
// Old: implementation 'docling-json-jackson'
// New: implementation 'docling-json-gson'

// Client code unchanged!
ModularDoclingClient client = ModularDoclingClient.builder().build();
```

### 2. **Easy Testing**
```java
// Mock HTTP transport for unit tests
HttpTransport mockTransport = new HttpTransport() {
    @Override
    public HttpResponse execute(HttpRequest req) {
        return new HttpResponse(200, Map.of(), "{\"taskId\":\"123\"}".getBytes());
    }
    // ... other methods
};

ModularDoclingClient client = ModularDoclingClient.builder()
    .httpTransport(mockTransport)
    .jsonSerializer(new JacksonJsonSerializer())
    .build();

// No real HTTP calls, fully testable!
```

### 3. **Corporate Requirements**
```java
// Corporate requires Apache HttpClient with custom config
CloseableHttpClient customHttpClient = HttpClients.custom()
    .setProxy(new HttpHost("proxy.corp.com", 8080))
    .setDefaultCredentialsProvider(credentialsProvider)
    .build();

HttpTransport transport = new ApacheHttpTransport(customHttpClient);

ModularDoclingClient client = ModularDoclingClient.builder()
    .httpTransport(transport)
    .build();
```

### 4. **Conflict Resolution**
```gradle
dependencies {
    // Your app uses Gson
    implementation 'com.google.code.gson:gson:2.10.1'

    // Docling uses Gson too (no Jackson conflict!)
    implementation 'com.docling:docling-client-modular:2.0.0'
    implementation 'com.docling:docling-json-gson:2.0.0'
    implementation 'com.docling:docling-transport-native:2.0.0'
}
```

## Building from Source

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :docling-client-modular:build

# Publish to local Maven
./gradlew publishToMavenLocal

# Test modular client
./gradlew :docling-client-modular:test
```

## Troubleshooting

### "No HttpTransport implementation found"

**Problem:**
```
IllegalStateException: No HttpTransport implementation found on classpath.
```

**Solution:**
Add an HTTP transport dependency:
```gradle
implementation 'com.docling:docling-transport-native:2.0.0'
```

### "No JsonSerializer implementation found"

**Problem:**
```
IllegalStateException: No JsonSerializer implementation found on classpath.
```

**Solution:**
Add a JSON serializer dependency:
```gradle
implementation 'com.docling:docling-json-jackson:2.0.0'
```

### Multiple Implementations Found

If you have multiple implementations, specify explicitly:

```java
ModularDoclingClient client = ModularDoclingClient.builder()
    .httpTransport(new NativeHttpTransport()) // Explicitly choose
    .jsonSerializer(new JacksonJsonSerializer()) // Explicitly choose
    .build();
```

## Future Roadmap

**v2.0 (Current)**
- ✅ Core modular architecture
- ✅ Jackson and Gson adapters
- ✅ Native, Apache, OkHttp transports
- ✅ Basic conversion API

**v2.1 (Planned)**
- 🔜 Full feature parity with v1.x
- 🔜 Chunking API in modular client
- 🔜 Moshi adapter
- 🔜 Streaming support

**v3.0 (Future)**
- 🔮 Deprecate legacy API
- 🔮 Modular API becomes default

## FAQ

**Q: Should I migrate to the modular API now?**
A: No rush! The legacy API (v1.x) continues to work and is fully supported. Migrate when you need pluggable HTTP/JSON.

**Q: Can I use both APIs together?**
A: Yes! They're completely independent. Use legacy for stability, modular for flexibility.

**Q: Which is faster?**
A: Performance is identical. The abstraction layer has zero overhead (simple method calls).

**Q: Does modular support all features?**
A: Not yet. v2.0 covers basic conversions. v2.1 will add chunking, streaming, etc.

**Q: How do I create a custom adapter?**
A: Implement `HttpTransport` or `JsonSerializer` interface and register via ServiceLoader.

## Support

- **Legacy API**: See [README.md](README.md) and [CLAUDE.md](CLAUDE.md)
- **Modular API**: See this document
- **Issues**: [GitHub Issues](https://github.com/yourusername/docling-java-wrapper/issues)

---

**Bottom Line**: Use **legacy API (v1.x)** for production today, explore **modular API (v2.0)** for future flexibility.
