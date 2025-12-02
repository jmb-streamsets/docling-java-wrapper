package com.docling.client;

import com.docling.model.ChunkDocumentResponse;
import com.docling.model.OutputFormat;
import com.docling.model.ResponseTaskResultV1ResultTaskIdGet;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive examples of using CompletableFuture with DoclingClient.
 * Demonstrates modern async patterns for framework integration and parallel processing.
 * <p>
 * This shows the power of CompletableFuture for:
 * - Non-blocking operations
 * - Parallel processing
 * - Error handling
 * - Operation chaining
 * - Framework integration (Spring, Vert.x, etc.)
 */
public final class UsageCompletableFuture {

    private static final File SAMPLE_FILE = new File("documents/2512.01970v1.pdf");
    private static final List<String> SAMPLE_URLS = List.of(
            "https://arxiv.org/pdf/2501.17887"
    );
    private static final Path OUTPUT_ROOT = Path.of("output", "benchmarks", "completable-future");
    private static final Path CONVERT_DIR = OUTPUT_ROOT.resolve("conversions");
    private static final Path CHUNK_DIR = OUTPUT_ROOT.resolve("chunks");
    private static final Path PARALLEL_DIR = OUTPUT_ROOT.resolve("parallel");

    private UsageCompletableFuture() {
        // Utility class
    }

    public static void main(String[] args) throws Exception {
        DoclingClient client = DoclingClient.fromEnv();
        Files.createDirectories(CONVERT_DIR);
        Files.createDirectories(CHUNK_DIR);
        Files.createDirectories(PARALLEL_DIR);
        ensureSampleFile();

        // Check if server is reachable
        if (!checkServerHealth(client)) {
            System.err.println("ERROR: Cannot connect to Docling server at " + client.getBaseUrl());
            System.err.println("\nTo run these examples, you need a Docling server running.");
            System.err.println("\nOptions:");
            System.err.println("  1. Start local server: docker run -p 5001:5001 docling/server");
            System.err.println("  2. Set DOCLING_BASE_URL to point to a running server");
            System.err.println("  3. See https://github.com/DS4SD/docling for server setup");
            System.err.println("\nThese examples demonstrate CompletableFuture patterns - the code is");
            System.err.println("production-ready, you just need a server to test against.");
            System.exit(1);
        }

        System.out.println("✓ Connected to Docling server at " + client.getBaseUrl() + "\n");

        // Configure custom executor for better control
        ExecutorService executor = Executors.newFixedThreadPool(10,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("docling-async-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });
        client.setAsyncExecutor(executor);

        try {
            System.out.println("=== CompletableFuture Examples ===\n");

            // Example 1: Basic non-blocking conversion
            example1BasicNonBlocking(client);

            // Example 2: Chaining operations
            example2ChainingOperations(client);

            // Example 3: Parallel file conversions
            example3ParallelFiles(client);

            // Example 4: Parallel URL conversions
            example4ParallelUrls(client);

            // Example 5: All formats in parallel
            example5AllFormats(client);

            // Example 6: Error handling
            example6ErrorHandling(client);

            // Example 7: Timeout handling
            example7TimeoutHandling(client);

            // Example 8: Combined operations
            example8CombinedOperations(client);

            // Example 9: Batch processing with error recovery
            example9BatchProcessing(client);

            // Example 10: RAG pipeline with chunking
            example10RagPipeline(client);

            System.out.println("\n=== All examples completed ===");
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Example 1: Basic non-blocking async conversion.
     * Shows how the thread is not blocked during conversion.
     */
    private static void example1BasicNonBlocking(DoclingClient client) throws Exception {
        System.out.println("Example 1: Basic non-blocking conversion");
        Instant start = Instant.now();

        CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
                client.convertFileAsyncFuture(SAMPLE_FILE);

        System.out.println("  Conversion started, doing other work...");
        System.out.println("  Current thread is not blocked!");
        Thread.sleep(1000); // Simulate other work

        ResponseTaskResultV1ResultTaskIdGet result = future.get();
        long elapsed = Duration.between(start, Instant.now()).toMillis();
        System.out.println("  ✓ Conversion completed in " + elapsed + "ms\n");
    }

    /**
     * Example 2: Chaining operations with thenApply/thenAccept.
     * Shows composition of async operations.
     */
    private static void example2ChainingOperations(DoclingClient client) throws Exception {
        System.out.println("Example 2: Chaining operations");
        Instant start = Instant.now();

        client.convertFileAsyncFuture(SAMPLE_FILE)
                .thenApply(result -> {
                    System.out.println("  ✓ Conversion completed, extracting content...");
                    return ConversionResults.unwrap(result, ConversionOutputType.MARKDOWN);
                })
                .thenAccept(doc -> {
                    String filename = doc.getDocument() != null
                            ? doc.getDocument().getFilename()
                            : "unknown";
                    System.out.println("  ✓ Extracted document: " + filename);
                })
                .exceptionally(ex -> {
                    System.err.println("  ✗ Error: " + ex.getMessage());
                    return null;
                })
                .get(); // Wait for completion

        long elapsed = Duration.between(start, Instant.now()).toMillis();
        System.out.println("  ✓ Pipeline completed in " + elapsed + "ms\n");
    }

    /**
     * Example 3: Convert multiple files in parallel.
     * Much faster than sequential processing.
     */
    private static void example3ParallelFiles(DoclingClient client) throws Exception {
        System.out.println("Example 3: Parallel file conversions");

        if (!SAMPLE_FILE.exists()) {
            System.out.println("  Skipping (sample file not found)\n");
            return;
        }

        List<File> files = Arrays.asList(SAMPLE_FILE, SAMPLE_FILE, SAMPLE_FILE);
        Instant start = Instant.now();

        CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
                AsyncDoclingClient.convertFilesParallel(client, files);

        results.thenAccept(list -> {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  ✓ Converted " + list.size() + " files in parallel");
            System.out.println("  ✓ Total time: " + elapsed + "ms");
        }).get();

        System.out.println();
    }

    /**
     * Example 4: Convert multiple URLs in parallel.
     */
    private static void example4ParallelUrls(DoclingClient client) throws Exception {
        System.out.println("Example 4: Parallel URL conversions");
        Instant start = Instant.now();

        CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
                AsyncDoclingClient.convertUrlsParallel(client, SAMPLE_URLS);

        results.thenAccept(list -> {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  ✓ Converted " + list.size() + " URLs in parallel");
            System.out.println("  ✓ Total time: " + elapsed + "ms");
        }).get();

        System.out.println();
    }

    /**
     * Example 5: Convert to all formats in parallel.
     * More efficient than sequential format conversion.
     */
    private static void example5AllFormats(DoclingClient client) throws Exception {
        System.out.println("Example 5: All formats in parallel");

        if (SAMPLE_URLS.isEmpty()) {
            System.out.println("  Skipping (no sample URLs)\n");
            return;
        }

        Instant start = Instant.now();

        CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
                AsyncDoclingClient.convertUrlAllFormats(client, SAMPLE_URLS.get(0));

        results.thenAccept(list -> {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  ✓ Generated " + list.size() + " formats in parallel");
            System.out.println("  ✓ Total time: " + elapsed + "ms");
        }).get();

        System.out.println();
    }

    /**
     * Example 6: Proper error handling with exceptionally.
     */
    private static void example6ErrorHandling(DoclingClient client) throws Exception {
        System.out.println("Example 6: Error handling");

        // Demonstrate different error scenarios

        // Scenario 1: File doesn't exist - caught synchronously
        File nonExistent = new File("nonexistent.pdf");
        try {
            client.convertFileAsyncFuture(nonExistent).get();
            System.out.println("  ✗ Should have thrown exception");
        } catch (Exception ex) {
            System.out.println("  ✓ Caught file not found error: " + ex.getCause().getClass().getSimpleName());
        }

        // Scenario 2: Invalid URL - handled asynchronously in CompletableFuture
        String invalidUrl = "https://invalid-domain-that-does-not-exist-12345.com/doc.pdf";
        client.convertUrlAsyncFuture(invalidUrl)
                .exceptionally(ex -> {
                    System.out.println("  ✓ Caught async error gracefully: " +
                        (ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : ex.getClass().getSimpleName()));
                    return null;
                })
                .get();

        // Scenario 3: Chaining with error recovery
        if (SAMPLE_FILE.exists()) {
            client.convertFileAsyncFuture(SAMPLE_FILE)
                    .thenApply(result -> {
                        // Simulate an error in processing
                        if (result != null) {
                            throw new RuntimeException("Simulated processing error");
                        }
                        return result;
                    })
                    .exceptionally(ex -> {
                        System.out.println("  ✓ Recovered from processing error: " + ex.getMessage());
                        return null; // Return fallback value
                    })
                    .thenAccept(result -> {
                        System.out.println("  ✓ Pipeline completed with error recovery");
                    })
                    .get();
        }

        System.out.println();
    }

    /**
     * Example 7: Timeout handling (Java 9+).
     */
    private static void example7TimeoutHandling(DoclingClient client) throws Exception {
        System.out.println("Example 7: Timeout handling");

        if (!SAMPLE_FILE.exists()) {
            System.out.println("  Skipping (sample file not found)\n");
            return;
        }

        // Demonstrate timeout with graceful fallback
        Instant start = Instant.now();

        CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
                client.convertFileAsyncFuture(SAMPLE_FILE)
                        .orTimeout(1, TimeUnit.MILLISECONDS) // Very short timeout for demo
                        .exceptionally(ex -> {
                            long elapsed = Duration.between(start, Instant.now()).toMillis();
                            if (ex.getCause() != null && ex.getCause().getClass().getSimpleName().contains("Timeout")) {
                                System.out.println("  ✓ Timeout after " + elapsed + "ms - handling gracefully");
                            } else {
                                System.out.println("  ✓ Other error: " +
                                    (ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : ex.getClass().getSimpleName()));
                            }
                            return null; // Fallback value
                        });

        ResponseTaskResultV1ResultTaskIdGet result = future.get();
        if (result == null) {
            System.out.println("  ✓ Returned fallback value after timeout");
        } else {
            System.out.println("  Completed within timeout (unexpected with 1ms timeout)");
        }

        System.out.println();
    }

    /**
     * Example 8: Combining multiple async operations.
     */
    private static void example8CombinedOperations(DoclingClient client) throws Exception {
        System.out.println("Example 8: Combined conversion + chunking");
        Instant start = Instant.now();

        if (!SAMPLE_FILE.exists()) {
            System.out.println("  Skipping (sample file not found)\n");
            return;
        }

        AsyncDoclingClient.convertAndChunk(client, SAMPLE_FILE, OutputFormat.MD)
                .thenAccept(result -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    System.out.println("  ✓ Both operations completed in parallel");
                    System.out.println("  ✓ Conversion: " + result.conversionResult());
                    System.out.println("  ✓ Chunks: " + result.chunkResult());
                    System.out.println("  ✓ Total time: " + elapsed + "ms");
                })
                .exceptionally(ex -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    System.out.println("  ✗ Operation failed after " + elapsed + "ms");
                    System.out.println("  ✗ Error: " +
                        (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    if (ex.getMessage() != null && ex.getMessage().contains("HTTP")) {
                        System.out.println("  ℹ Network error - this is transient, retry would likely succeed");
                    }
                    return null;
                })
                .get();

        System.out.println();
    }

    /**
     * Example 9: Batch processing with error recovery.
     * Shows how failures don't stop other operations.
     */
    private static void example9BatchProcessing(DoclingClient client) throws Exception {
        System.out.println("Example 9: Batch processing with error recovery");

        List<File> files = Arrays.asList(
                SAMPLE_FILE,
                new File("nonexistent1.pdf"),
                SAMPLE_FILE,
                new File("nonexistent2.pdf")
        );

        Instant start = Instant.now();

        CompletableFuture<List<AsyncDoclingClient.ConversionResult>> results =
                AsyncDoclingClient.convertAndSaveParallel(
                        client,
                        files,
                        PARALLEL_DIR,
                        ConversionOutputType.MARKDOWN
                );

        results.thenAccept(list -> {
            long successes = list.stream().filter(AsyncDoclingClient.ConversionResult::isSuccess).count();
            long failures = list.stream().filter(AsyncDoclingClient.ConversionResult::isFailure).count();
            long elapsed = Duration.between(start, Instant.now()).toMillis();

            System.out.println("  ✓ Processed " + list.size() + " files");
            System.out.println("  ✓ Successes: " + successes);
            System.out.println("  ✓ Failures: " + failures + " (handled gracefully)");
            System.out.println("  ✓ Total time: " + elapsed + "ms");
        }).get();

        System.out.println();
    }

    /**
     * Example 10: RAG pipeline with parallel chunking.
     * Shows real-world use case for vector search preparation.
     */
    private static void example10RagPipeline(DoclingClient client) throws Exception {
        System.out.println("Example 10: RAG pipeline with parallel chunking");

        if (SAMPLE_URLS.isEmpty()) {
            System.out.println("  Skipping (no sample URLs)\n");
            return;
        }

        Instant start = Instant.now();

        CompletableFuture<List<ChunkDocumentResponse>> chunks =
                AsyncDoclingClient.chunkUrlsParallel(client, SAMPLE_URLS)
                        .thenApply(results -> results.stream()
                                .map(result -> (ChunkDocumentResponse) result.getActualInstance())
                                .collect(Collectors.toList()));

        chunks.thenAccept(chunkList -> {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            int totalChunks = chunkList.stream()
                    .mapToInt(chunk -> chunk.getChunks() != null ? chunk.getChunks().size() : 0)
                    .sum();

            System.out.println("  ✓ Processed " + chunkList.size() + " documents");
            System.out.println("  ✓ Generated " + totalChunks + " chunks total");
            System.out.println("  ✓ Ready for vector database ingestion");
            System.out.println("  ✓ Total time: " + elapsed + "ms");
        }).get();

        System.out.println();
    }

    private static void ensureSampleFile() {
        if (!SAMPLE_FILE.exists()) {
            System.out.println("Note: Sample file " + SAMPLE_FILE + " not found. Some examples will be skipped.");
        }
    }

    /**
     * Check if the Docling server is reachable.
     */
    private static boolean checkServerHealth(DoclingClient client) {
        try {
            client.health();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
