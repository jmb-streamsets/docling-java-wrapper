# Docling Java Wrapper

A modern, production-ready Java library for document conversion using [Docling](https://github.com/DS4SD/docling). Convert PDF, Office documents, and more to Markdown, JSON, HTML, and other formats with a clean, idiomatic Java API.

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-green.svg)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## ✨ Features

- 🚀 **Modern Async API** - Full `CompletableFuture` support for non-blocking operations
- 📄 **Multiple Output Formats** - Markdown, JSON, HTML, Plain Text, DocTags
- 🔄 **Synchronous & Asynchronous** - Choose the right approach for your use case
- 📦 **Streaming Support** - Memory-efficient processing for large files
- 🔗 **Framework Integration** - Works with Spring Boot, WebFlux, Vert.x, Quarkus
- 🔁 **Automatic Retry** - Resilient network error handling
- 🎯 **RAG Support** - Document chunking for vector search and embeddings
- 🛡️ **Production Ready** - Thread-safe, configurable, comprehensive error handling
- 📚 **Well Documented** - Extensive examples and guides
- ⚡ **Parallel Processing** - Convert multiple documents concurrently

## 📋 Requirements

- **Java 17** or higher
- **Gradle 8.x** (included via wrapper)
- **Docling Server** (local or remote)

## 🚀 Quick Start

### 1. Add Dependency

```gradle
dependencies {
    implementation 'com.docling:docling-java-wrapper:1.0.0'
}
```

### 2. Start Docling Server

```bash
docker run -p 5001:5001 docling/docling-serve:latest
```

See [SERVER_SETUP.md](SERVER_SETUP.md) for detailed server configuration.

### 3. Convert a Document

```java
import com.docling.client.DoclingClient;
import com.docling.model.OutputFormat;

// Initialize client (reads DOCLING_BASE_URL from env)
DoclingClient client = DoclingClient.fromEnv();

// Synchronous conversion
File pdf = new File("document.pdf");
ResponseProcessFileV1ConvertFilePost response = client.convertFile(pdf);

// Asynchronous with CompletableFuture (non-blocking)
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(pdf);

future.thenAccept(result ->
    System.out.println("Conversion complete!")
);
```

## 🎯 Core Features

### Synchronous Conversion

Perfect for scripts and simple use cases:

```java
DoclingClient client = DoclingClient.fromEnv();
File pdf = new File("document.pdf");

// Convert to Markdown
ResponseProcessFileV1ConvertFilePost response =
    client.convertFile(pdf, TargetName.INBODY, OutputFormat.MD);

// Save to file
ConversionResults.write(response,
    Path.of("output.md"),
    ConversionOutputType.MARKDOWN,
    client.getApiClient().getObjectMapper());
```

### Asynchronous with CompletableFuture

Modern, non-blocking API for production applications:

```java
// Non-blocking conversion
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file);

// Chain operations
future
    .thenApply(result -> ConversionResults.unwrap(result, ConversionOutputType.MARKDOWN))
    .thenAccept(doc -> saveToDatabase(doc))
    .exceptionally(ex -> {
        log.error("Conversion failed", ex);
        return null;
    });

// Parallel processing
List<File> files = Arrays.asList(file1, file2, file3);
CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
    AsyncDoclingClient.convertFilesParallel(client, files);
```

**Benefits:**
- ✅ Non-blocking - thread continues immediately
- ✅ Composable - chain with `thenApply()`, `thenAccept()`
- ✅ Parallel - convert multiple documents concurrently (3-5x faster!)
- ✅ Framework integration - works with Spring, WebFlux, Reactor, RxJava

### URL Conversion

Convert documents from URLs:

```java
String url = "https://example.com/document.pdf";

// Synchronous
ResponseProcessUrlV1ConvertSourcePost response = client.convertUrl(url);

// Asynchronous
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertUrlAsyncFuture(url);
```

### Streaming for Large Files

Memory-efficient processing:

```java
BufferedDoclingClient buffered = BufferedDoclingClient.fromEnv();

// Streaming upload (doesn't load file into memory)
File largeFile = new File("large-document.pdf");
ResponseProcessFileV1ConvertFilePost response = buffered.convert(largeFile);

// Or with CompletableFuture
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    buffered.convertAsyncFuture(largeFile);
```

### Document Chunking (RAG)

Prepare documents for vector search and embeddings:

```java
// Chunk documents for RAG applications
List<String> urls = List.of("https://example.com/doc.pdf");
ChunkDocumentResponse chunks = client.chunkHybridSources(urls);

// Asynchronous chunking
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.chunkHybridSourcesAsyncFuture(urls);

// Parallel chunking for multiple documents
CompletableFuture<List<ChunkDocumentResponse>> allChunks =
    AsyncDoclingClient.chunkUrlsParallel(client, documentUrls);
```

## 📊 Supported Output Formats

All formats work with both sync and async APIs:

| Format | Enum | Description | File Extension |
|--------|------|-------------|----------------|
| **Markdown** | `OutputFormat.MD` | GitHub-flavored Markdown | `.md` |
| **JSON** | `OutputFormat.JSON` | Structured JSON with metadata | `.json` |
| **HTML** | `OutputFormat.HTML` | Clean HTML output | `.html` |
| **Plain Text** | `OutputFormat.TEXT` | Plain text extraction | `.txt` |
| **DocTags** | `OutputFormat.DOCTAGS` | Tagged document structure | `.doctags` |
| **Chunks** | (via chunking API) | For RAG/vector search | `.json` |

```java
// Convert to all formats in parallel
CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> allFormats =
    AsyncDoclingClient.convertFileAllFormats(client, file);
```

## 🔧 Configuration

### Environment Variables

```bash
# Server URL (default: http://127.0.0.1:5001)
export DOCLING_BASE_URL=http://localhost:5001

# API Key (if server requires authentication)
export DOCLING_API_KEY=your-secret-key

# Logging
export DOCLING_LOG_LEVEL=INFO        # DEBUG, INFO, WARN, ERROR
export DOCLING_TRACE_HTTP=true       # Enable HTTP tracing

# Async task polling
export POLL_MAX_SECONDS=900          # Task timeout (default: 15 min)
export POLL_WAIT_SECONDS=10          # Poll interval (default: 10 sec)

# Metrics
export DOCLING_METRICS=true          # Enable metrics collection
```

### Custom Configuration

```java
// Custom client
DoclingClient client = new DoclingClient(
    "https://docling.example.com",
    "api-key",
    Duration.ofMinutes(15)
);

// Custom executor for async operations
ExecutorService executor = Executors.newFixedThreadPool(20);
client.setAsyncExecutor(executor);

// Custom retry policy
client.setRetryPolicy(RetryPolicy.builder()
    .maxRetries(5)
    .initialDelayMs(1000)
    .maxDelayMs(30000)
    .build());

// Enable diagnostics
client.setTraceHttp(true);
client.setMetricsEnabled(true);
client.setLogLevel("DEBUG");
```

## 🌟 Framework Integration

### Spring Boot

```java
@Configuration
public class DoclingConfig {
    @Bean
    public DoclingClient doclingClient() {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        DoclingClient client = DoclingClient.fromEnv();
        client.setAsyncExecutor(executor);
        return client;
    }
}

@Service
public class DocumentService {
    @Autowired
    private DoclingClient doclingClient;

    public CompletableFuture<ConversionResult> convertDocument(File file) {
        return doclingClient.convertFileAsyncFuture(file)
            .thenApply(this::processResult);
    }
}
```

### Spring WebFlux

```java
@RestController
public class DocumentController {
    @Autowired
    private DoclingClient doclingClient;

    @GetMapping("/convert")
    public Mono<ConversionResult> convert(@RequestParam String url) {
        return Mono.fromFuture(
            doclingClient.convertUrlAsyncFuture(url)
                .thenApply(this::processResult)
        );
    }

    @PostMapping("/convert-batch")
    public Flux<ConversionResult> convertBatch(@RequestBody List<String> urls) {
        return Flux.fromIterable(urls)
            .flatMap(url -> Mono.fromFuture(
                doclingClient.convertUrlAsyncFuture(url)
                    .thenApply(this::processResult)
            ));
    }
}
```

### Vert.x

```java
vertx.executeBlocking(promise -> {
    client.convertFileAsyncFuture(file)
        .thenAccept(result -> promise.complete(result))
        .exceptionally(ex -> {
            promise.fail(ex);
            return null;
        });
});
```

### Quarkus

```java
@ApplicationScoped
public class DocumentService {
    @Inject
    DoclingClient doclingClient;

    public Uni<ConversionResult> convert(File file) {
        return Uni.createFrom()
            .completionStage(() -> doclingClient.convertFileAsyncFuture(file))
            .map(this::processResult);
    }
}
```

## 🛡️ Error Handling

### Automatic Retry

Network errors are automatically retried (3 attempts, 2s backoff):

```java
// Retries happen transparently
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    client.convertFileAsyncFuture(file);

// You only see the error if all retries fail
future.exceptionally(ex -> {
    log.error("Conversion failed after retries", ex);
    return null;
});
```

### Exception Hierarchy

```
DoclingClientException (base)
  ├── DoclingHttpException          # HTTP 4xx/5xx errors
  ├── DoclingNetworkException       # Network failures (retryable)
  ├── DoclingTimeoutException       # Operation timeouts
  ├── DoclingTaskFailureException   # Task processing failed
  └── ConversionMaterializationException  # Presigned URL issues
```

### Proper Error Handling

```java
try {
    // Synchronous errors (file not found, etc.)
    CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
        client.convertFileAsyncFuture(file);

    // Asynchronous errors (network, conversion failures)
    future.exceptionally(ex -> {
        if (ex.getCause() instanceof DoclingTimeoutException) {
            log.warn("Conversion timed out");
        } else if (ex.getCause() instanceof DoclingNetworkException) {
            log.error("Network error", ex);
        }
        return null;
    }).get();

} catch (Exception ex) {
    // Handle synchronous exceptions
    log.error("Failed to start conversion", ex);
}
```

See [ERROR_HANDLING_GUIDE.md](ERROR_HANDLING_GUIDE.md) for comprehensive error handling patterns.

## 📚 Examples

Run examples with:

```bash
# CompletableFuture patterns (10 examples)
./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture

# Synchronous operations
./gradlew run -PmainClass=com.docling.client.UsageSync

# Asynchronous operations
./gradlew run -PmainClass=com.docling.client.UsageAsync

# Streaming for large files
./gradlew run -PmainClass=com.docling.client.UsageAsyncStreaming

# OCR options
./gradlew run -PmainClass=com.docling.client.UsageOcrOptions
```

### Example: Parallel Processing

```java
// Convert 10 files in parallel (10x faster than sequential!)
List<File> files = getDocuments();

CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
    AsyncDoclingClient.convertFilesParallel(client, files);

results.thenAccept(resultList -> {
    System.out.println("Converted " + resultList.size() + " files");
});
```

### Example: RAG Pipeline

```java
// Chunk multiple documents for vector search
List<String> documentUrls = Arrays.asList(
    "https://example.com/doc1.pdf",
    "https://example.com/doc2.pdf"
);

CompletableFuture<List<ChunkDocumentResponse>> chunks =
    AsyncDoclingClient.chunkUrlsParallel(client, documentUrls)
        .thenApply(results -> results.stream()
            .map(result -> (ChunkDocumentResponse) result.getActualInstance())
            .collect(Collectors.toList()));

chunks.thenAccept(chunkList -> {
    // Store in vector database
    vectorDb.storeChunks(chunkList);
});
```

### Example: Batch Processing with Error Recovery

```java
// Process files even if some fail
List<File> files = Arrays.asList(file1, file2, file3);

CompletableFuture<List<AsyncDoclingClient.ConversionResult>> results =
    AsyncDoclingClient.convertAndSaveParallel(
        client, files, outputDir, ConversionOutputType.MARKDOWN
    );

results.thenAccept(resultList -> {
    long successes = resultList.stream()
        .filter(AsyncDoclingClient.ConversionResult::isSuccess)
        .count();
    long failures = resultList.stream()
        .filter(AsyncDoclingClient.ConversionResult::isFailure)
        .count();

    System.out.println("Successes: " + successes + ", Failures: " + failures);
});
```

## 📖 Documentation

- **[CLAUDE.md](CLAUDE.md)** - Complete development guide with architecture and patterns
- **[COMPLETABLE_FUTURE_UPGRADE.md](COMPLETABLE_FUTURE_UPGRADE.md)** - Modern async API overview
- **[ERROR_HANDLING_GUIDE.md](ERROR_HANDLING_GUIDE.md)** - Comprehensive error handling patterns
- **[SERVER_SETUP.md](SERVER_SETUP.md)** - Docling server setup and deployment
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Complete feature summary

## 🏗️ Building

```bash
# Clean build
./gradlew clean build

# Compile only
./gradlew compileJava

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests RetryPolicyTest

# Regenerate OpenAPI client
./gradlew openApiGenerate
```

## 🔍 Architecture

```
com.docling.client/          # Hand-crafted wrapper layer
  ├── DoclingClient          # Main client (sync + async)
  ├── BufferedDoclingClient  # Streaming-optimized wrapper
  ├── AsyncDoclingClient     # Helper utilities for async patterns
  ├── ConversionResults      # Result materialization
  ├── ConversionOutputType   # Output format handling
  ├── RetryPolicy            # Configurable retry logic
  └── Docling*Exception      # Exception hierarchy

build/generated/             # OpenAPI-generated code (DO NOT EDIT)
  ├── com.docling.api/       # Generated API classes
  ├── com.docling.model/     # Generated model classes
  └── com.docling.invoker/   # Generated HTTP client
```

**Key Design:**
- Generated API client from OpenAPI spec
- Hand-crafted wrapper for idiomatic Java
- Thread-safe, production-ready
- Comprehensive error handling
- Full async support

## ⚡ Performance

### Parallel vs Sequential

| Operation | Sequential | Parallel (CompletableFuture) | Speedup |
|-----------|-----------|------------------------------|---------|
| 3 files × 10s | 30 seconds | 10 seconds | **3x faster** |
| 5 URLs × 15s | 75 seconds | 15 seconds | **5x faster** |
| 5 formats | 50 seconds | 10 seconds | **5x faster** |

### Memory Efficiency

- **Streaming API** - Doesn't load entire file into memory
- **BufferedDoclingClient** - Uses buffered streams for large files
- **Configurable thread pools** - Control resource usage

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Follow existing code style
4. Add tests for new features
5. Update documentation
6. Submit a pull request

## 📝 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built on top of [Docling](https://github.com/DS4SD/docling) by IBM Research
- OpenAPI client generation via [OpenAPI Generator](https://openapi-generator.tech/)

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/docling-java-wrapper/issues)
- **Docling Server**: [Docling Documentation](https://github.com/DS4SD/docling)
- **Examples**: See `src/main/java/com/docling/client/Usage*.java`

## 🚀 What's Next?

1. **Start the Docling server** - See [SERVER_SETUP.md](SERVER_SETUP.md)
2. **Run the examples** - `./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture`
3. **Read the docs** - Start with [CLAUDE.md](CLAUDE.md)
4. **Integrate into your app** - Use the modern async API for best performance

---

Made with ❤️ for modern Java applications
