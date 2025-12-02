# Docling Modular Client

This module provides a modular, pluggable client architecture for Docling document conversion, built on the Service Provider Interface (SPI) pattern.

## Architecture

The modular client demonstrates clean separation of concerns through three key components:

### 1. API Layer (`docling-api`)
Pure domain models with no external dependencies:
- `ConversionRequest` / `ConversionResponse`
- `OutputFormat` enumeration
- `DocumentResult` POJO

### 2. SPI Layer (`docling-spi`)
Service Provider Interfaces for pluggable implementations:
- `JsonSerializer` - Pluggable JSON serialization (Jackson, Gson, Moshi, etc.)
- `HttpTransport` - Pluggable HTTP clients (Native, Apache, OkHttp, etc.)
- `HttpRequest` / `HttpResponse` - Transport-agnostic abstractions

### 3. Client Layer (`docling-client-modular`)
The `ModularDoclingClient` orchestrates the components using ServiceLoader for automatic discovery.

## Available Implementations

### JSON Serializers
- **Jackson** (`docling-json-jackson`) - Default, most feature-rich
- **Gson** (`docling-json-gson`) - Lightweight alternative [TO BE IMPLEMENTED]

### HTTP Transports
- **Native** (`docling-transport-native`) - Java 11+ HttpClient, zero dependencies
- **Apache** (`docling-transport-apache`) - Apache HttpClient 5 [TO BE IMPLEMENTED]
- **OkHttp** (`docling-transport-okhttp`) - Square OkHttp [TO BE IMPLEMENTED]

## Usage

### Basic Usage with Auto-Discovery

```java
// Automatically discovers first available implementations
ModularDoclingClient client = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .build();

// Synchronous conversion
ConversionResponse response = client.convertUrl(
    "https://example.com/doc.pdf",
    OutputFormat.MARKDOWN
);

// Asynchronous conversion
CompletableFuture<ConversionResponse> future = client.convertUrlAsync(
    "https://example.com/doc.pdf",
    OutputFormat.MARKDOWN
);
```

### Explicit Implementation Selection

```java
// Choose specific implementations
ModularDoclingClient client = ModularDoclingClient.builder()
    .baseUrl("http://localhost:5001")
    .httpTransport(new NativeHttpTransport())
    .jsonSerializer(new JacksonJsonSerializer())
    .apiKey("my-api-key")
    .build();
```

## Benchmark

The `ModularBenchmark` class provides comprehensive performance testing across all available implementation combinations.

### Running the Benchmark

```bash
# Ensure Docling server is running
docker run -p 5001:5001 docling/server

# Run benchmark with all available implementations
./gradlew :docling-client-modular:run

# Or with custom server URL
DOCLING_BASE_URL=http://localhost:5001 ./gradlew :docling-client-modular:run
```

### What the Benchmark Tests

The benchmark evaluates:
1. **All transport × serializer combinations** - Tests every possible pairing
2. **Synchronous operations** - Direct blocking calls
3. **Asynchronous operations** - Non-blocking CompletableFuture-based
4. **Warmup + measurement** - 2 warmup iterations, 5 measured iterations
5. **Comprehensive metrics** - Average, min, and max times
6. **Error handling** - Validates responses and reports failures

### Sample Output

```
╔═══════════════════════════════════════════════════════════════╗
║  Docling Modular Architecture Benchmark                      ║
╚═══════════════════════════════════════════════════════════════╝

Server URL: http://127.0.0.1:5001
Test Document: https://arxiv.org/pdf/2206.01062
Iterations: 5 (after 2 warmup)

Discovered Implementations:
  HTTP Transports: [Native]
  JSON Serializers: [Jackson]

═══════════════════════════════════════════════════════════════
Testing: Native + Jackson
═══════════════════════════════════════════════════════════════
Client: ModularDoclingClient[transport=Native, serializer=Jackson, baseUrl=http://127.0.0.1:5001]

Running SYNCHRONOUS benchmark...
  Warmup: .. done
  Benchmark: ..... done
  ✓ avg=1234ms, min=1180ms, max=1290ms

Running ASYNCHRONOUS benchmark...
  Warmup: .. done
  Benchmark: ..... done
  ✓ avg=1215ms, min=1170ms, max=1260ms

╔═══════════════════════════════════════════════════════════════╗
║  BENCHMARK SUMMARY                                            ║
╚═══════════════════════════════════════════════════════════════╝

Configuration                            Mode         Avg (ms)    Min (ms)    Max (ms) Status
──────────────────────────────────────────────────────────────────────────────────────────────
Native+Jackson                           SYNC             1234        1180        1290 ✓
Native+Jackson                           ASYNC            1215        1170        1260 ✓

Fastest Configuration:
  transport=Native, serializer=Jackson
  Mode: ASYNC
  Avg: 1215ms
```

## Benefits of the Modular Architecture

### 1. Zero Lock-in
Switch HTTP clients or JSON libraries without changing application code:
```java
// Start with Native HTTP
.httpTransport(new NativeHttpTransport())

// Switch to Apache for advanced features
.httpTransport(new ApacheHttpTransport())
```

### 2. Minimal Dependencies
Only include what you need:
```gradle
dependencies {
    implementation 'com.docling:docling-client-modular'
    runtimeOnly 'com.docling:docling-json-jackson'
    runtimeOnly 'com.docling:docling-transport-native'
}
```

### 3. Easy Testing
Mock SPIs for unit testing:
```java
class MockHttpTransport implements HttpTransport {
    @Override
    public HttpResponse execute(HttpRequest request) {
        return new HttpResponse(200, Map.of(), "{}".getBytes());
    }
}
```

### 4. Performance Optimization
Benchmark shows which combination works best for your workload, then lock it in.

## Creating Custom Implementations

### Custom JSON Serializer

```java
public class CustomJsonSerializer implements JsonSerializer {
    @Override
    public <T> String toJson(T object) { /* ... */ }

    @Override
    public <T> T fromJson(String json, Class<T> type) { /* ... */ }

    @Override
    public String getName() { return "Custom"; }
}
```

Register via `META-INF/services/com.docling.spi.JsonSerializer`:
```
com.example.CustomJsonSerializer
```

### Custom HTTP Transport

```java
public class CustomHttpTransport implements HttpTransport {
    @Override
    public HttpResponse execute(HttpRequest request) { /* ... */ }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request) { /* ... */ }

    @Override
    public String getName() { return "Custom"; }

    @Override
    public void close() { /* ... */ }
}
```

Register via `META-INF/services/com.docling.spi.HttpTransport`:
```
com.example.CustomHttpTransport
```

## Comparison with Main Client

| Feature | Main Client | Modular Client |
|---------|-------------|----------------|
| HTTP Library | Java native (fixed) | Pluggable via SPI |
| JSON Library | Jackson (fixed) | Pluggable via SPI |
| OpenAPI Generated | Yes | No (hand-crafted) |
| Async Support | Yes | Yes |
| Streaming | Yes | Planned |
| Retry Logic | Built-in | TBD |
| Dependencies | Heavy | Minimal |
| Use Case | Production-ready | Experimental/flexible |

## Development

### Building

```bash
./gradlew :docling-client-modular:build
```

### Running Tests

```bash
./gradlew :docling-client-modular:test
```

### Creating Distribution

```bash
./gradlew :docling-client-modular:distZip
# Creates: docling-client-modular/build/distributions/docling-client-modular-2.0.0-SNAPSHOT.zip
```

## Future Enhancements

- [ ] Implement Gson JSON serializer
- [ ] Implement Apache HttpClient transport
- [ ] Implement OkHttp transport
- [ ] Add streaming file upload support
- [ ] Add retry policy SPI
- [ ] Add metrics/tracing SPI
- [ ] Add caching layer
- [ ] Connection pooling configuration
- [ ] Circuit breaker pattern

## License

Same as parent project.
