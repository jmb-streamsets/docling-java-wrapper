package com.docling.client;

import com.docling.model.ConvertDocumentsRequestOptions;
import com.docling.model.OutputFormat;
import com.docling.model.ResponseTaskResultV1ResultTaskIdGet;
import com.docling.model.TargetName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced async utilities for CompletableFuture-based operations with DoclingClient.
 * Provides helper methods for common patterns like parallel processing, batch operations,
 * and integration with modern frameworks.
 * <p>
 * This class demonstrates best practices for using CompletableFuture with Docling operations
 * and provides convenience methods for complex async workflows.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Parallel File Conversion</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * List<File> files = Arrays.asList(file1, file2, file3);
 *
 * CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
 *     AsyncDoclingClient.convertFilesParallel(client, files);
 *
 * results.thenAccept(list -> {
 *     System.out.println("Converted " + list.size() + " files");
 * });
 * }</pre>
 *
 * <h3>URL Conversion with Different Formats</h3>
 * <pre>{@code
 * CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
 *     AsyncDoclingClient.convertUrlAllFormats(client, url);
 * }</pre>
 *
 * <h3>Custom Executor for Better Control</h3>
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(5);
 * client.setAsyncExecutor(executor);
 *
 * try {
 *     AsyncDoclingClient.convertFilesParallel(client, largeFileList).get();
 * } finally {
 *     executor.shutdown();
 * }
 * }</pre>
 *
 * @see DoclingClient
 * @see CompletableFuture
 */
public final class AsyncDoclingClient {

    private static final Logger log = LogManager.getLogger(AsyncDoclingClient.class);

    private AsyncDoclingClient() {
        // Utility class
    }

    /**
     * Converts multiple files in parallel, returning a CompletableFuture that completes
     * when all conversions are done. Works with all output formats.
     * <p>
     * This method is more efficient than sequential processing for multiple files.
     * <p>
     * Example:
     * <pre>{@code
     * List<File> files = Arrays.asList(new File("doc1.pdf"), new File("doc2.pdf"));
     * CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
     *     AsyncDoclingClient.convertFilesParallel(client, files,
     *         TargetName.INBODY, OutputFormat.MD);
     *
     * results.thenAccept(resultList -> {
     *     for (ResponseTaskResultV1ResultTaskIdGet result : resultList) {
     *         System.out.println("Converted: " + result);
     *     }
     * });
     * }</pre>
     *
     * @param client       DoclingClient to use
     * @param files        Files to convert
     * @param targetType   Target type
     * @param outputFormat Output format
     * @return CompletableFuture with list of results
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertFilesParallel(
            DoclingClient client,
            List<File> files,
            TargetName targetType,
            OutputFormat outputFormat) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(files, "files");

        List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = files.stream()
                .map(file -> client.convertFileAsyncFuture(file, targetType, outputFormat))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts multiple files in parallel with default options (INBODY, Markdown).
     *
     * @param client DoclingClient to use
     * @param files  Files to convert
     * @return CompletableFuture with list of results
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertFilesParallel(
            DoclingClient client,
            List<File> files) {
        return convertFilesParallel(client, files, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Converts multiple URLs in parallel.
     * <p>
     * Example:
     * <pre>{@code
     * List<String> urls = Arrays.asList(
     *     "https://example.com/doc1.pdf",
     *     "https://example.com/doc2.pdf"
     * );
     * ConvertDocumentsRequestOptions options =
     *     DoclingClient.defaultConvertOptions(OutputFormat.JSON);
     *
     * CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> results =
     *     AsyncDoclingClient.convertUrlsParallel(client, urls, options);
     * }</pre>
     *
     * @param client  DoclingClient to use
     * @param urls    URLs to convert
     * @param options Conversion options
     * @return CompletableFuture with list of results
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertUrlsParallel(
            DoclingClient client,
            List<String> urls,
            ConvertDocumentsRequestOptions options) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(urls, "urls");

        List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = urls.stream()
                .map(url -> client.convertUrlAsyncFuture(url, options))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts multiple URLs in parallel with default Markdown options.
     *
     * @param client DoclingClient to use
     * @param urls   URLs to convert
     * @return CompletableFuture with list of results
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertUrlsParallel(
            DoclingClient client,
            List<String> urls) {
        return convertUrlsParallel(client, urls, DoclingClient.defaultConvertOptions(OutputFormat.MD));
    }

    /**
     * Converts a single URL to all available output formats in parallel.
     * Useful for generating multiple output formats from the same source efficiently.
     * <p>
     * Example:
     * <pre>{@code
     * String url = "https://example.com/document.pdf";
     * CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> allFormats =
     *     AsyncDoclingClient.convertUrlAllFormats(client, url);
     *
     * allFormats.thenAccept(results -> {
     *     System.out.println("Generated " + results.size() + " formats");
     *     // results[0] = Markdown, results[1] = JSON, etc.
     * });
     * }</pre>
     *
     * @param client DoclingClient to use
     * @param url    URL to convert
     * @return CompletableFuture with list of results, one per format
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertUrlAllFormats(
            DoclingClient client,
            String url) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(url, "url");

        List<OutputFormat> formats = Arrays.asList(OutputFormat.values());

        List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = formats.stream()
                .map(format -> {
                    ConvertDocumentsRequestOptions options = DoclingClient.defaultConvertOptions(format);
                    return client.convertUrlAsyncFuture(url, options);
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts a file to all available output formats in parallel.
     *
     * @param client DoclingClient to use
     * @param file   File to convert
     * @return CompletableFuture with list of results, one per format
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> convertFileAllFormats(
            DoclingClient client,
            File file) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");

        List<OutputFormat> formats = Arrays.asList(OutputFormat.values());

        List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = formats.stream()
                .map(format -> client.convertFileAsyncFuture(file, TargetName.INBODY, format))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Chunks multiple URLs in parallel.
     * Useful for RAG applications that need to process multiple documents.
     * <p>
     * Example for RAG pipeline:
     * <pre>{@code
     * List<String> documentUrls = Arrays.asList(
     *     "https://example.com/doc1.pdf",
     *     "https://example.com/doc2.pdf"
     * );
     *
     * CompletableFuture<List<ChunkDocumentResponse>> chunks =
     *     AsyncDoclingClient.chunkUrlsParallel(client, documentUrls)
     *         .thenApply(results -> results.stream()
     *             .map(result -> (ChunkDocumentResponse) result.getActualInstance())
     *             .toList());
     *
     * chunks.thenAccept(chunkList -> {
     *     chunkList.forEach(chunk -> {
     *         System.out.println("Doc has " + chunk.getChunks().size() + " chunks");
     *         // Store chunks in vector database for RAG
     *     });
     * });
     * }</pre>
     *
     * @param client DoclingClient to use
     * @param urls   URLs to chunk
     * @return CompletableFuture with list of chunk results
     */
    public static CompletableFuture<List<ResponseTaskResultV1ResultTaskIdGet>> chunkUrlsParallel(
            DoclingClient client,
            List<String> urls) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(urls, "urls");

        List<CompletableFuture<ResponseTaskResultV1ResultTaskIdGet>> futures = urls.stream()
                .map(url -> client.chunkHybridSourcesAsyncFuture(List.of(url)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts and then chunks a file in a pipeline, returning both results.
     * <p>
     * Example:
     * <pre>{@code
     * CompletableFuture<ConversionAndChunkResult> result =
     *     AsyncDoclingClient.convertAndChunk(client, file, OutputFormat.MD);
     *
     * result.thenAccept(res -> {
     *     System.out.println("Conversion: " + res.conversionResult());
     *     System.out.println("Chunks: " + res.chunkResult());
     * });
     * }</pre>
     *
     * @param client       DoclingClient to use
     * @param file         File to process
     * @param outputFormat Output format for conversion
     * @return CompletableFuture with both results
     */
    public static CompletableFuture<ConversionAndChunkResult> convertAndChunk(
            DoclingClient client,
            File file,
            OutputFormat outputFormat) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");

        CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> conversion =
                client.convertFileAsyncFuture(file, TargetName.INBODY, outputFormat);

        CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunking =
                client.chunkHybridFilesAsyncFuture(file);

        return conversion.thenCombine(chunking, ConversionAndChunkResult::new);
    }

    /**
     * Processes multiple files with both conversion and chunking in parallel.
     * Efficient for batch document processing pipelines.
     *
     * @param client DoclingClient to use
     * @param files  Files to process
     * @return CompletableFuture with list of results
     */
    public static CompletableFuture<List<ConversionAndChunkResult>> convertAndChunkParallel(
            DoclingClient client,
            List<File> files) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(files, "files");

        List<CompletableFuture<ConversionAndChunkResult>> futures = files.stream()
                .map(file -> convertAndChunk(client, file, OutputFormat.MD))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts and saves multiple files in parallel with proper error handling.
     * Each file is converted to the specified format and saved to the destination directory.
     * Failures are logged but don't stop other conversions.
     * <p>
     * Example:
     * <pre>{@code
     * List<File> files = Arrays.asList(file1, file2, file3);
     * Path outputDir = Path.of("output");
     *
     * CompletableFuture<List<ConversionResult>> results =
     *     AsyncDoclingClient.convertAndSaveParallel(
     *         client, files, outputDir, ConversionOutputType.MARKDOWN
     *     );
     *
     * results.thenAccept(list -> {
     *     long successful = list.stream().filter(ConversionResult::isSuccess).count();
     *     System.out.println(successful + " of " + list.size() + " conversions succeeded");
     * });
     * }</pre>
     *
     * @param client     DoclingClient to use
     * @param files      Files to convert
     * @param outputDir  Directory to save converted files
     * @param outputType Output type (determines format and extension)
     * @return CompletableFuture with list of results (includes successes and failures)
     */
    public static CompletableFuture<List<ConversionResult>> convertAndSaveParallel(
            DoclingClient client,
            List<File> files,
            Path outputDir,
            ConversionOutputType outputType) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(outputType, "outputType");

        List<CompletableFuture<ConversionResult>> futures = files.stream()
                .map(file -> convertAndSave(client, file, outputDir, outputType))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Converts and saves a single file with error handling.
     * Handles both synchronous errors (file not found) and async errors (conversion failures).
     */
    private static CompletableFuture<ConversionResult> convertAndSave(
            DoclingClient client,
            File file,
            Path outputDir,
            ConversionOutputType outputType) {

        try {
            // Try to start the conversion
            return client.convertFileAsyncFuture(file, TargetName.INBODY, outputType.getOutputFormat())
                    .thenApply(result -> {
                        try {
                            Path outputPath = outputDir.resolve(file.getName() + outputType.getFileExtension());
                            ConversionResults.write(result, outputPath, outputType,
                                    client.getApiClient().getObjectMapper());
                            log.info("Successfully converted and saved: {} -> {}", file.getName(), outputPath);
                            return new ConversionResult(file, outputPath, null);
                        } catch (IOException e) {
                            log.error("Failed to save conversion result for {}: {}", file.getName(), e.getMessage());
                            return new ConversionResult(file, null, e);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to convert {}: {}", file.getName(), ex.getMessage());
                        return new ConversionResult(file, null, ex);
                    });
        } catch (Exception e) {
            // Handle synchronous errors (e.g., file not found during multipart creation)
            log.error("Failed to start conversion for {}: {}", file.getName(), e.getMessage());
            return CompletableFuture.completedFuture(new ConversionResult(file, null, e));
        }
    }

    /**
     * Result of a combined conversion and chunking operation.
     *
     * @param conversionResult Result from conversion
     * @param chunkResult      Result from chunking
     */
    public record ConversionAndChunkResult(
            ResponseTaskResultV1ResultTaskIdGet conversionResult,
            ResponseTaskResultV1ResultTaskIdGet chunkResult) {
    }

    /**
     * Result of a conversion operation with error handling.
     *
     * @param sourceFile The source file that was converted
     * @param outputPath Path where the converted file was saved (null if failed)
     * @param error      Exception if conversion failed (null if successful)
     */
    public record ConversionResult(
            File sourceFile,
            Path outputPath,
            Throwable error) {

        /**
         * Returns true if the conversion was successful.
         */
        public boolean isSuccess() {
            return error == null && outputPath != null;
        }

        /**
         * Returns true if the conversion failed.
         */
        public boolean isFailure() {
            return !isSuccess();
        }
    }
}
