package com.docling.client;

import com.docling.model.OutputFormat;
import com.docling.model.ResponseProcessFileV1ConvertFilePost;
import com.docling.model.TargetName;
import com.docling.model.TaskStatusResponse;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Thin convenience façade that always uploads via buffered streaming multipart endpoints.
 * Uses the File overloads on DoclingClient which wrap a BufferedInputStream internally.
 * <p>
 * This client is ideal for large files where memory efficiency is critical, as it streams
 * the file content rather than loading it entirely into memory.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Usage</h3>
 * <pre>{@code
 * BufferedDoclingClient client = BufferedDoclingClient.fromEnv();
 * File pdfFile = new File("large-document.pdf");
 *
 * // Synchronous streaming conversion
 * ResponseProcessFileV1ConvertFilePost response = client.convert(pdfFile);
 * }</pre>
 *
 * <h3>Async Streaming</h3>
 * <pre>{@code
 * BufferedDoclingClient client = BufferedDoclingClient.fromEnv();
 *
 * // Start async conversion with streaming upload
 * TaskStatusResponse task = client.convertAsync(pdfFile);
 *
 * // Wait for completion
 * ResponseTaskResultV1ResultTaskIdGet result =
 *     client.delegate().waitForTaskResult(task.getTaskId());
 * }</pre>
 *
 * <h3>Chunking with Streaming</h3>
 * <pre>{@code
 * BufferedDoclingClient client = BufferedDoclingClient.fromEnv();
 *
 * TaskStatusResponse task = client.chunkHybridAsync(
 *     pdfFile,
 *     true  // Include converted document
 * );
 * }</pre>
 *
 * <h3>Custom Configuration</h3>
 * <pre>{@code
 * BufferedDoclingClient client = new BufferedDoclingClient(
 *     "http://localhost:5001",
 *     "api-key",
 *     Duration.ofMinutes(5)
 * );
 * }</pre>
 *
 * @see DoclingClient
 */
public record BufferedDoclingClient(DoclingClient delegate) {

    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException if delegate is null
     */
    public BufferedDoclingClient {
        Objects.requireNonNull(delegate, "delegate DoclingClient cannot be null");
    }

    public BufferedDoclingClient(String baseUrl,
                                 String apiKey,
                                 Duration timeout) {
        this(new DoclingClient(baseUrl, apiKey, timeout));
    }

    public BufferedDoclingClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, Duration.ofMinutes(10));
    }

    public static BufferedDoclingClient fromEnv() {
        return new BufferedDoclingClient(DoclingClient.fromEnv());
    }

    /**
     * Sync convert using buffered multipart upload.
     */
    public ResponseProcessFileV1ConvertFilePost convert(File file,
                                                        TargetName targetType,
                                                        OutputFormat outputFormat) throws IOException {
        return delegate.convertMultipart(file, targetType, outputFormat);
    }

    public ResponseProcessFileV1ConvertFilePost convert(File file) throws IOException {
        return convert(file, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Async convert using buffered multipart upload.
     */
    public TaskStatusResponse convertAsync(File file,
                                           TargetName targetType,
                                           OutputFormat outputFormat) throws IOException {
        return delegate.convertMultipartAsync(file, targetType, outputFormat);
    }

    public TaskStatusResponse convertAsync(File file) throws IOException {
        return convertAsync(file, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Hybrid chunking async using buffered multipart upload.
     */
    public TaskStatusResponse chunkHybridAsync(File file,
                                               boolean includeConvertedDoc) throws IOException {
        return delegate.chunkHybridMultipartAsync(file, includeConvertedDoc);
    }

    public TaskStatusResponse chunkHybridAsync(File file) throws IOException {
        return chunkHybridAsync(file, false);
    }
}
