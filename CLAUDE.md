# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java wrapper library for the Docling document conversion service. It provides a clean, idiomatic Java API around a Docling REST server, handling document conversion (PDF, Office docs, etc.) to various output formats (Markdown, JSON, HTML, text) and document chunking for RAG applications.

**Key characteristics:**
- OpenAPI-generated client code with hand-crafted wrapper layer
- Java 17 with Gradle build system
- Thread-safe client design for production use
- Support for both synchronous and asynchronous operations
- Streaming multipart upload for memory-efficient large file handling

## Build and Development Commands

### Core Build Commands
```bash
./gradlew clean build                    # Full clean build with code generation and tests
./gradlew openApiGenerate                # Regenerate API client from openapi-3.0.json
./gradlew compileJava                    # Compile only (depends on openApiGenerate)
./gradlew test                           # Run all tests
./gradlew test --tests ClassName         # Run specific test class
```

### Running Examples
```bash
# Run synchronous usage example
./gradlew run -PmainClass=com.docling.client.UsageSync

# Run async usage example
./gradlew run -PmainClass=com.docling.client.UsageAsync

# Run async streaming example
./gradlew run -PmainClass=com.docling.client.UsageAsyncStreaming

# Run OCR options example
./gradlew run -PmainClass=com.docling.client.UsageOcrOptions

# Run CompletableFuture examples (NEW)
./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture

# Run modular architecture benchmark (NEW)
./gradlew :docling-client-modular:run
```

### Environment Variables
- `DOCLING_BASE_URL` - Docling server URL (default: http://127.0.0.1:5001)
- `DOCLING_API_KEY` - API key for authentication (optional)
- `DOCLING_LOG_LEVEL` - Log level: DEBUG, INFO, WARN, ERROR
- `DOCLING_TRACE_HTTP` - Enable HTTP tracing: true/false
- `DOCLING_METRICS` - Enable metrics collection: true/false
- `POLL_MAX_SECONDS` - Async task timeout (default: 900)
- `POLL_WAIT_SECONDS` - Async poll interval (default: 10)

## Architecture

### Modular Architecture (NEW)

**NEW in version 2.0**: The project now includes a fully modular, pluggable client architecture alongside the main OpenAPI-generated client.

#### Components

1. **docling-api** - Pure domain models (no dependencies)
   - `ConversionRequest`, `ConversionResponse`, `DocumentResult`
   - `OutputFormat` enumeration
   - Clean POJOs for serialization

2. **docling-spi** - Service Provider Interfaces
   - `JsonSerializer` - Pluggable JSON libraries (Jackson, Gson, etc.)
   - `HttpTransport` - Pluggable HTTP clients (Native, Apache, OkHttp, etc.)
   - `HttpRequest`/`HttpResponse` - Transport-agnostic abstractions

3. **docling-client-modular** - Orchestrating client
   - `ModularDoclingClient` - Uses ServiceLoader for auto-discovery
   - Supports both sync and async operations
   - Zero lock-in to specific implementations

4. **Implementation Modules**
   - `docling-json-jackson` - Jackson JSON serializer
   - `docling-json-gson` - Gson JSON serializer (planned)
   - `docling-transport-native` - Java 11+ HttpClient
   - `docling-transport-apache` - Apache HttpClient 5 (planned)
   - `docling-transport-okhttp` - OkHttp (planned)

#### Modular Benchmark

The `ModularBenchmark` class comprehensively tests all implementation combinations:

```bash
# Run benchmark
./gradlew :docling-client-modular:run

# Output includes:
# - Discovery of available implementations
# - Performance testing for each transport × serializer combination
# - Sync and async operation metrics
# - Average, min, max times
# - Identification of fastest configuration
```

See `docling-client-modular/README.md` for detailed documentation.

### Code Generation Flow
1. `openapi-3.0.json` (source of truth) → OpenAPI Generator
2. Generated code → `build/generated/src/main/java/com/docling/{api,model,invoker}`
3. Post-generation fixes applied via Gradle task (see build.gradle:68-95)
4. Generated code is automatically compiled into sourceSets

**Critical:** Always run `./gradlew openApiGenerate` after modifying `openapi-3.0.json`

### Package Structure
```
com.docling.client/          # Hand-crafted wrapper layer (edit these)
  ├── DoclingClient          # Main synchronous/async client facade
  ├── BufferedDoclingClient  # Streaming-optimized wrapper
  ├── ConversionResults      # Result materialization helpers
  ├── ConversionOutputType   # Output format enumeration
  ├── RetryPolicy            # Configurable retry logic
  ├── AsyncWorkflowHelper    # Async operation utilities
  ├── OcrScenarioRunner      # OCR benchmarking framework
  └── Docling*Exception      # Exception hierarchy

com.docling.model/           # Hand-crafted enums/extensions
  ├── TargetName             # Custom target enum
  ├── ConvertOcrLang         # OCR language enum
  └── ChunkingMaxTokens      # Chunking config enum

build/generated/             # OpenAPI-generated code (DO NOT EDIT)
  ├── com.docling.api/       # Generated API classes
  ├── com.docling.model/     # Generated model classes
  └── com.docling.invoker/   # Generated HTTP client
```

### Two-Tier Client Design

**DoclingClient** - Main client with full API surface:
- Thread-safe singleton suitable for injection
- Handles authentication, correlation IDs, retries, metrics
- Supports both file-based and stream-based operations
- Factory method: `DoclingClient.fromEnv()`

**BufferedDoclingClient** - Streaming-optimized facade:
- Thin record wrapper around DoclingClient
- Always uses streaming multipart uploads (memory-efficient)
- Ideal for large files or memory-constrained environments
- Factory method: `BufferedDoclingClient.fromEnv()`

### Exception Hierarchy
```
DoclingClientException (base)
  ├── DoclingHttpException          # HTTP errors (4xx, 5xx)
  ├── DoclingNetworkException       # Network failures (retryable)
  ├── DoclingTimeoutException       # Operation timeouts
  ├── DoclingTaskFailureException   # Async task failures
  └── ConversionMaterializationException  # Output extraction failures
```

### Retry Logic
The `RetryPolicy` class provides configurable retry behavior:
- Default: 3 retries with exponential backoff
- Retries on network failures and 5xx errors
- No retry on 4xx client errors
- Customizable via `RetryPolicy.builder()`

### ObjectMapper Configuration
**IMPORTANT:** The client requires custom Jackson deserialization for `ConvertDocumentResponse`. The `DoclingClient` constructor automatically configures its internal ObjectMapper via `DoclingClient.configureObjectMapper()`. If you need a separate ObjectMapper for working with Docling responses, you MUST configure it through this method.

## Common Development Patterns

### Modern Async API with CompletableFuture

**NEW in version 1.1+**: The library now provides comprehensive `CompletableFuture` support for modern async frameworks and non-blocking operations.

#### Why CompletableFuture?

The original async API (`convertFileAsync()` + `waitForTaskResult()`) blocks the calling thread with polling. The new CompletableFuture API provides:

**Benefits:**
- **Non-blocking** - Returns immediately, polls in background thread
- **Composable** - Chain operations with `thenApply()`, `thenAccept()`, etc.
- **Framework integration** - Works with Spring WebFlux, Reactor, RxJava, Vert.x
- **Parallel processing** - Easy with `CompletableFuture.allOf()`
- **Better error handling** - Rich functional API for exceptions
- **Built-in timeouts** - Java 9+ `.orTimeout()` support

#### API Comparison

```java
// OLD: Blocking async API
TaskStatusResponse task = client.convertFileAsync(file);
ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
// ^ Thread blocks here until completion

// NEW: Non-blocking CompletableFuture API
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file);
// Thread continues immediately, result available when ready
ResponseTaskResultV1ResultTaskIdGet result = future.get(); // Or use callbacks
```

#### Available CompletableFuture Methods

**All async methods now have `*AsyncFuture()` variants:**
- File conversion: `convertFileAsyncFuture()`, `convertMultipartAsyncFuture()`
- URL conversion: `convertUrlAsyncFuture()`
- Stream conversion: `convertStreamAsyncFuture()`
- Chunking: `chunkHybridSourcesAsyncFuture()`, `chunkHybridFilesAsyncFuture()`
- Task waiting: `waitForTaskResultAsync()`

**All work with every output format:** MD, JSON, HTML, TEXT, DOCTAGS

#### Basic CompletableFuture Usage

```java
// Non-blocking conversion
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file, TargetName.INBODY, OutputFormat.MD);

// Do other work while conversion happens
System.out.println("Doing other work...");

// Get result when ready
ResponseTaskResultV1ResultTaskIdGet result = future.get();
```

#### Chaining Operations

```java
// Chain transformations
client.convertFileAsyncFuture(file)
    .thenApply(result -> ConversionResults.unwrap(result, ConversionOutputType.MARKDOWN))
    .thenAccept(doc -> {
        System.out.println("Converted: " + doc.getDocument().getFilename());
        saveToDatabase(doc);
    })
    .exceptionally(ex -> {
        log.error("Conversion failed", ex);
        return null;
    });
```

#### Parallel Processing

```java
// Convert multiple files in parallel
List<File> files = Arrays.asList(file1, file2, file3);

List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = files.stream()
    .map(client::convertFileAsyncFuture)
    .toList();

// Wait for all to complete
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> System.out.println("All conversions complete!"));

// Or use helper method
CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
    AsyncDoclingClient.convertFilesParallel(client, files);
```

#### Timeout Handling (Java 9+)

```java
// Built-in timeout support
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> result =
    client.convertFileAsyncFuture(file)
        .orTimeout(5, TimeUnit.MINUTES)
        .exceptionally(ex -> {
            if (ex instanceof TimeoutException) {
                log.warn("Conversion timed out");
            }
            return null;
        });
```

#### Spring Framework Integration

```java
@Service
public class DocumentService {

    @Autowired
    private DoclingClient doclingClient;

    // Spring @Async support
    public CompletableFuture<ConversionResult> convertDocument(MultipartFile file) {
        return doclingClient.convertFileAsyncFuture(file.getFile())
            .thenApply(this::processResult);
    }

    // Spring WebFlux reactive support
    @GetMapping("/convert")
    public Mono<ConversionResult> convert(@RequestParam String url) {
        return Mono.fromFuture(
            doclingClient.convertUrlAsyncFuture(url)
                .thenApply(this::processResult)
        );
    }

    // Spring WebFlux with Flux for streaming
    @GetMapping("/convert-batch")
    public Flux<ConversionResult> convertBatch(@RequestParam List<String> urls) {
        return Flux.fromIterable(urls)
            .flatMap(url -> Mono.fromFuture(
                doclingClient.convertUrlAsyncFuture(url)
                    .thenApply(this::processResult)
            ));
    }
}
```

#### Advanced Patterns with AsyncDoclingClient

The `AsyncDoclingClient` utility class provides common patterns:

```java
// Convert to all formats in parallel
CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> allFormats =
    AsyncDoclingClient.convertUrlAllFormats(client, url);

// Batch processing with error recovery
CompletableFuture<List<AsyncDoclingClient.ConversionResult>> results =
    AsyncDoclingClient.convertAndSaveParallel(
        client, files, outputDir, ConversionOutputType.MARKDOWN
    );

// RAG pipeline: parallel chunking for vector search
CompletableFuture<List<ChunkDocumentResponse>> chunks =
    AsyncDoclingClient.chunkUrlsParallel(client, documentUrls)
        .thenApply(results -> results.stream()
            .map(result -> (ChunkDocumentResponse) result.getActualInstance())
            .collect(Collectors.toList()));

// Combined conversion + chunking
AsyncDoclingClient.convertAndChunk(client, file, OutputFormat.MD)
    .thenAccept(result -> {
        System.out.println("Conversion: " + result.conversionResult());
        System.out.println("Chunks: " + result.chunkResult());
    });
```

#### Custom Executor Configuration

For production use, configure a custom `ExecutorService`:

```java
ExecutorService executor = Executors.newFixedThreadPool(10,
    new ThreadFactoryBuilder()
        .setNameFormat("docling-async-%d")
        .setDaemon(true)
        .build());

client.setAsyncExecutor(executor);

// Use the client...

// Clean shutdown
executor.shutdown();
executor.awaitTermination(30, TimeUnit.SECONDS);
```

#### BufferedDoclingClient CompletableFuture Support

The streaming-optimized `BufferedDoclingClient` also has CompletableFuture methods:

```java
BufferedDoclingClient buffered = BufferedDoclingClient.fromEnv();

// Non-blocking streaming upload
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    buffered.convertAsyncFuture(largeFile);

// Chunking with streaming
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunks =
    buffered.chunkHybridAsyncFuture(largeFile, true);
```

#### Migration Guide

**If you have existing blocking code:**

```java
// Before
TaskStatusResponse task = client.convertFileAsync(file);
ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());

// After (backward compatible - no changes needed)
TaskStatusResponse task = client.convertFileAsync(file);
ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());

// Or migrate to non-blocking
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file);
```

**Both APIs coexist** - choose based on your needs:
- Use `*Async()` + `waitForTaskResult()` for simple scripts and blocking workflows
- Use `*AsyncFuture()` for framework integration, parallel processing, and modern async patterns

#### Examples

Run the comprehensive example:
```bash
./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture
```

This demonstrates 10+ patterns including parallel processing, error handling, timeouts, and RAG pipelines.

### Converting Documents (Traditional API)
```java
// Synchronous file conversion
DoclingClient client = DoclingClient.fromEnv();
File pdf = new File("document.pdf");
ResponseProcessFileV1ConvertFilePost response = client.convertFile(pdf);
ConversionResults.write(response, Path.of("output.md"),
    ConversionOutputType.MARKDOWN, client.getApiClient().getObjectMapper());

// Asynchronous conversion (for large files)
TaskStatusResponse task = client.convertFileAsync(pdf);
ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
```

### Streaming Large Files
```java
// Memory-efficient streaming upload
BufferedDoclingClient buffered = BufferedDoclingClient.fromEnv();
File largeFile = new File("large-document.pdf");
ResponseProcessFileV1ConvertFilePost response = buffered.convert(largeFile);
```

### Document Chunking (for RAG)
```java
// Chunk documents for vector search
List<String> urls = List.of("https://example.com/doc.pdf");
ChunkDocumentResponse chunks = client.chunkHybridSources(urls);
```

### Custom Configuration
```java
// Configure retry behavior
RetryPolicy customRetry = RetryPolicy.builder()
    .maxRetries(5)
    .initialDelayMs(500)
    .maxDelayMs(10000)
    .build();
client.setRetryPolicy(customRetry);

// Enable diagnostics
client.setTraceHttp(true);
client.setMetricsEnabled(true);
client.setLogLevel("DEBUG");
```

## Testing Strategy

### Test Types
- **Unit tests** (`*Test.java`) - JUnit Jupiter, run on every build
- **Integration tests** - Require live Docling server, use `Assumptions` guard
- **Usage examples** - Executable main classes demonstrating API patterns

### Running Tests
```bash
# All tests
./gradlew test

# Single test class
./gradlew test --tests RetryPolicyTest

# With specific log level
DOCLING_LOG_LEVEL=DEBUG ./gradlew test
```

### Test Patterns
- Use `@TempDir` for file I/O tests
- Mock HTTP responses with lightweight `HttpServer` (see Groovy tests)
- Guard integration tests with `Assumptions.assumeTrue(serverAvailable)`
- Test both success and failure paths in exception tests

## Code Style

### Java Version and Syntax
- Target: Java 17 (configured via Gradle toolchain)
- Use modern features: records, switch expressions, text blocks
- Prefer `final` for local variables and parameters where clarity improves

### Formatting
- 4-space indentation
- Opening braces on same line for methods, new line for classes
- Line length: ~120 characters
- Use try-with-resources for all `Closeable` types

### Naming Conventions
- Client classes: `*Client`, `*Helper`, `*Runner`
- Exceptions: `Docling*Exception`
- Test classes: `*Test` (picked up automatically by Gradle)
- Usage examples: `Usage*` (runnable main classes)

### Documentation
- Public APIs require comprehensive Javadoc with `@param` and `@return`
- Include code examples in class-level Javadoc for user-facing classes
- Reference line numbers when discussing specific implementations: `DoclingClient.java:712`

## OpenAPI Code Generation

### Workflow
1. Edit `openapi-3.0.json` in repository root
2. Run `./gradlew openApiGenerate`
3. Review generated code in `build/generated/`
4. Build should apply post-generation fixes automatically (see build.gradle)
5. Commit both spec and any wrapper changes together

### Post-Generation Fixes
The build applies string replacements to work around generator quirks:
- Fixes default initializers (arrays, Target objects)
- Corrects generic type references (`List<String>.class` → `List.class`)
- Renames problematic enum values (`C#`, `C++`)

See `build.gradle` lines 68-95 for full list of fixes.

### Generator Configuration
- Generator: `java`
- Library: `native` (Java 11+ HTTP client)
- Date library: `java8` (uses `java.time` types)
- Serialization: `jackson`

## Integration with Docling Server

### Server Setup
The wrapper expects a Docling server running at `DOCLING_BASE_URL`. Typical development setup:
```bash
# Start Docling server (example, adjust to your setup)
docker run -p 5001:5001 docling/server

# Run client against local server
DOCLING_BASE_URL=http://localhost:5001 ./gradlew run -PmainClass=com.docling.client.UsageSync
```

### Authentication
If the Docling server requires authentication:
```bash
export DOCLING_API_KEY=your-api-key-here
./gradlew run -PmainClass=com.docling.client.UsageSync
```

The client automatically adds `X-Api-Key` header when `DOCLING_API_KEY` is set.

## Key Implementation Notes

### Thread Safety
- `DoclingClient` is thread-safe and reusable across threads
- Uses ThreadLocal for correlation ID tracking (automatically cleaned up)
- Safe to inject as singleton in dependency injection frameworks

### Memory Management
- Streaming APIs (`convertMultipart*`, `chunkHybridMultipart*`) don't load files into memory
- Use `BufferedDoclingClient` for large file processing
- Binary responses are handled as streaming downloads

### Async Task Polling
- Long-polling supported via server-side wait parameter
- Client polls with exponential backoff (configurable)
- Tasks fail-fast on terminal error states
- Correlation IDs propagate through async operations

### HTTP Client
- Uses Java 11+ native HTTP client (HTTP/1.1)
- Configurable timeouts (connect and read)
- Request/response interceptors for auth, tracing, metrics
- Retries handled at wrapper layer, not HTTP client layer

## Troubleshooting

### Common Issues

**"Failed to parse response"** - Usually indicates ObjectMapper not configured with `ConvertDocumentResponseModule`. Ensure you use the client's ObjectMapper or configure your own via `DoclingClient.configureObjectMapper()`.

**"Task did not complete within timeout"** - Increase `POLL_MAX_SECONDS` environment variable or adjust polling parameters when calling `waitForTaskResult()`.

**Network timeouts on large files** - Use async APIs (`*Async` methods) or increase timeout in client constructor.

**Post-generation compilation errors** - Check if new OpenAPI spec requires additional fixes in build.gradle's `openApiGenerate.doLast` block.

### Debug Logging
```bash
# Maximum verbosity
DOCLING_LOG_LEVEL=DEBUG DOCLING_TRACE_HTTP=true DOCLING_METRICS=true ./gradlew test
```

## Related Files
- `AGENTS.md` - Previous guidance document (can reference for historical context)
- `openapi-3.0.json` - API specification (source of truth)
- `build.gradle` - Build configuration including code generation
- `src/main/resources/log4j2.xml` - Logging configuration
