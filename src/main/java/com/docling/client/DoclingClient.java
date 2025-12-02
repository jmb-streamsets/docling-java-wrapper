package com.docling.client;

import com.docling.api.ChunkApi;
import com.docling.api.ClearApi;
import com.docling.api.ConvertApi;
import com.docling.api.HealthApi;
import com.docling.api.TasksApi;
import com.docling.invoker.ApiClient;
import com.docling.model.ChunkDocumentResponse;
import com.docling.model.ClearResponse;
import com.docling.model.ConversionStatus;
import com.docling.model.ConvertDocumentResponse;
import com.docling.model.ConvertDocumentsRequest;
import com.docling.model.ConvertDocumentsRequestOptions;
import com.docling.model.ExportDocumentResponse;
import com.docling.model.HealthCheckResponse;
import com.docling.model.HybridChunkerOptions;
import com.docling.model.HybridChunkerOptionsDocumentsRequest;
import com.docling.model.ImageRefMode;
import com.docling.model.OcrEnginesEnum;
import com.docling.model.OutputFormat;
import com.docling.model.ProcessingPipeline;
import com.docling.model.ResponseProcessFileV1ConvertFilePost;
import com.docling.model.ResponseProcessUrlV1ConvertSourcePost;
import com.docling.model.ResponseTaskResultV1ResultTaskIdGet;
import com.docling.model.FileSourceRequest;
import com.docling.model.HttpSourceRequest;
import com.docling.model.InBodyTarget;
import com.docling.model.InputFormat;
import com.docling.model.MaxTokens;
import com.docling.model.PdfBackend;
import com.docling.model.PresignedUrlConvertDocumentResponse;
import com.docling.model.SourcesInner;
import com.docling.model.TableFormerMode;
import com.docling.model.Target;
import com.docling.model.Target1;
import com.docling.model.TargetName;
import com.docling.model.TaskStatusResponse;
import com.docling.model.ZipTarget;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Lightweight façade around the generated Docling REST client.
 * Keeps construction, auth header wiring, and common calls succinct for library consumers.
 * <p>
 * This client is thread-safe and can be shared across multiple threads.
 *
 * <h2>Quick Start Examples</h2>
 *
 * <h3>Basic Synchronous Conversion</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * File pdfFile = new File("document.pdf");
 * ResponseProcessFileV1ConvertFilePost response = client.convertFile(pdfFile);
 * }</pre>
 *
 * <h3>Asynchronous Conversion (for large files)</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * File pdfFile = new File("large-document.pdf");
 *
 * // Start async conversion
 * TaskStatusResponse task = client.convertFileAsync(pdfFile);
 *
 * // Wait for completion
 * ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
 * }</pre>
 *
 * <h3>URL Conversion</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * String url = "https://arxiv.org/pdf/2501.17887";
 * ResponseProcessUrlV1ConvertSourcePost response = client.convertUrl(url);
 * }</pre>
 *
 * <h3>Streaming for Memory Efficiency</h3>
 * <pre>{@code
 * try (InputStream stream = new FileInputStream("large.pdf")) {
 *     ResponseProcessUrlV1ConvertSourcePost response = client.convertStream(
 *         stream,
 *         "large.pdf",
 *         OutputFormat.MD
 *     );
 * }
 * }</pre>
 *
 * <h3>Document Chunking (for RAG applications)</h3>
 * <pre>{@code
 * List<String> urls = Arrays.asList("https://example.com/doc1.pdf");
 * ChunkDocumentResponse chunks = client.chunkHybridSources(urls);
 * }</pre>
 *
 * <h3>Saving Results to File</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * ResponseProcessFileV1ConvertFilePost response = client.convertFile(pdfFile);
 *
 * ConversionResults.write(
 *     response,
 *     Path.of("output.md"),
 *     ConversionOutputType.MARKDOWN,
 *     client.getApiClient().getObjectMapper()
 * );
 * }</pre>
 *
 * <h3>Custom Configuration</h3>
 * <pre>{@code
 * DoclingClient client = new DoclingClient(
 *     "http://localhost:5001",
 *     "api-key",
 *     Duration.ofMinutes(10)
 * );
 *
 * // Enable tracing
 * client.setTraceHttp(true);
 * client.setMetricsEnabled(true);
 * }</pre>
 *
 * <p>For comprehensive examples, see USAGE_EXAMPLES.md in the project root.
 *
 * @see BufferedDoclingClient
 * @see ConversionResults
 * @see ConversionOutputType
 */
public class DoclingClient implements AutoCloseable {

    public static final String DEFAULT_BASE_URL = System.getenv("DOCLING_BASE_URL") != null
            ? System.getenv("DOCLING_BASE_URL")
            : "http://127.0.0.1:5001";

    // Polling configuration constants
    private static final long DEFAULT_POLL_MAX_SECONDS = 900;
    private static final long DEFAULT_POLL_WAIT_SECONDS = 10;

    // Retry delay constants
    private static final long RETRY_DELAY_MS = 2000;

    // Shutdown timeout constants
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    // Default HTTP request timeout when no timeout is configured
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private static final Logger log = LogManager.getLogger(DoclingClient.class);
    private static final ThreadLocal<Deque<String>> CORRELATION_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final String CORRELATION_HEADER = "X-Docling-Correlation-Id";
    private static final ThreadLocal<Long> REQUEST_START = new ThreadLocal<>();
    private static final String LOG_LEVEL_ENV = System.getenv("DOCLING_LOG_LEVEL");

    static {
        configureLogLevel();
        logVersionInfo();
    }

    private final ApiClient apiClient;
    private final ConvertApi convertApi;
    private final ChunkApi chunkApi;
    private final TasksApi tasksApi;
    private final ClearApi clearApi;
    private final HealthApi healthApi;
    private final MetricsCollector metricsCollector;
    private final AtomicLong correlationSequence = new AtomicLong();
    private boolean traceHttp;
    private boolean metricsEnabled;
    private String apiKey;
    private RetryPolicy retryPolicy;
    private ExecutorService asyncExecutor;

    public DoclingClient() {
        this(DEFAULT_BASE_URL, System.getenv("DOCLING_API_KEY"), Duration.ofMinutes(10));
    }

    /**
     * Constructs a new DoclingClient.
     *
     * @param baseUrl Base URL of the Docling service (e.g., "http://localhost:5001")
     * @param apiKey  Optional API key for authentication (can be null)
     * @param timeout Connection and read timeout duration
     * @throws NullPointerException     if baseUrl or timeout is null
     * @throws IllegalArgumentException if timeout is negative or zero
     */
    public DoclingClient(String baseUrl, String apiKey, Duration timeout) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
        }

        this.traceHttp = flagEnabled("DOCLING_TRACE_HTTP");
        this.metricsEnabled = flagEnabled("DOCLING_METRICS");
        this.metricsCollector = new MetricsCollector(metricsEnabled);
        this.apiKey = apiKey;
        this.retryPolicy = RetryPolicy.defaultPolicy();
        this.apiClient = new ApiClient();
        var mapper = apiClient.getObjectMapper();
        configureObjectMapper(mapper);
        apiClient.setObjectMapper(mapper);
        apiClient.updateBaseUri(baseUrl);
        apiClient.setHttpClientBuilder(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout));
        apiClient.setConnectTimeout(timeout);
        apiClient.setReadTimeout(timeout);
        rebuildInterceptors();
        log.info("DoclingClient initialized baseUrl={} readTimeout={} connectTimeout={}", baseUrl, timeout, timeout);
        this.convertApi = new ConvertApi(apiClient);
        this.chunkApi = new ChunkApi(apiClient);
        this.tasksApi = new TasksApi(apiClient);
        this.clearApi = new ClearApi(apiClient);
        this.healthApi = new HealthApi(apiClient);
    }

    /**
     * Creates a DoclingClient from environment variables.
     * Uses DOCLING_BASE_URL (default: http://127.0.0.1:5001) and DOCLING_API_KEY.
     *
     * @return A new DoclingClient configured from environment
     */
    public static DoclingClient fromEnv() {
        return new DoclingClient(DEFAULT_BASE_URL, System.getenv("DOCLING_API_KEY"), Duration.ofMinutes(10));
    }

    /**
     * Checks if the task status indicates success.
     *
     * @param status TaskStatusResponse to check
     * @return true if status is done/success/succeeded/completed
     */
    static boolean isSuccessStatus(TaskStatusResponse status) {
        return isSuccessStatus(status != null ? status.getTaskStatus() : null);
    }

    /**
     * Checks if the task status indicates failure.
     *
     * @param status TaskStatusResponse to check
     * @return true if status is error/failed/failure
     */
    static boolean isFailureStatus(TaskStatusResponse status) {
        return isFailureStatus(status != null ? status.getTaskStatus() : null);
    }

    /**
     * Checks if the status string indicates success.
     * Case-insensitive matching of: done, success, succeeded, completed.
     *
     * @param status Status string to check
     * @return true if status indicates success
     */
    static boolean isSuccessStatus(String status) {
        if (status == null) return false;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "done", "success", "succeeded", "completed" -> true;
            default -> false;
        };
    }

    /**
     * Checks if the status string indicates failure.
     * Case-insensitive matching of: error, failed, failure.
     *
     * @param status Status string to check
     * @return true if status indicates failure
     */
    static boolean isFailureStatus(String status) {
        if (status == null) return false;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "error", "failed", "failure" -> true;
            default -> false;
        };
    }

    public static ConvertDocumentsRequest defaultConvertRequest(OutputFormat format) {
        return new ConvertDocumentsRequest()
                .options(defaultConvertOptions(format))
                .target(new Target(new InBodyTarget()));
    }

    public static ConvertDocumentsRequest defaultConvertRequest() {
        return defaultConvertRequest(OutputFormat.MD);
    }

    public static ConvertDocumentsRequest convertRequestFromFile(File file,
                                                                 OutputFormat format) throws IOException {
        return convertRequestFromBytes(Files.readAllBytes(file.toPath()), file.getName(), defaultConvertOptions(format));
    }

    public static ConvertDocumentsRequest convertRequestFromFile(File file,
                                                                 ConvertDocumentsRequestOptions options) throws IOException {
        return convertRequestFromBytes(Files.readAllBytes(file.toPath()), file.getName(), options);
    }

    public static ConvertDocumentsRequest convertRequestFromFile(File file) throws IOException {
        return convertRequestFromFile(file, OutputFormat.MD);
    }

    public static ConvertDocumentsRequest convertRequestFromStream(InputStream stream,
                                                                   String filename,
                                                                   OutputFormat format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        stream.transferTo(baos);
        return convertRequestFromBytes(baos.toByteArray(), filename, defaultConvertOptions(format));
    }

    public static ConvertDocumentsRequest convertRequestFromStream(InputStream stream,
                                                                   String filename,
                                                                   ConvertDocumentsRequestOptions options) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        stream.transferTo(baos);
        return convertRequestFromBytes(baos.toByteArray(), filename, options);
    }

    public static ConvertDocumentsRequest convertRequestFromStream(InputStream stream,
                                                                   String filename) throws IOException {
        return convertRequestFromStream(stream, filename, OutputFormat.MD);
    }

    public static ConvertDocumentsRequest convertRequestFromBytes(byte[] data,
                                                                  String filename,
                                                                  OutputFormat format) {
        return convertRequestFromBytes(data, filename, defaultConvertOptions(format));
    }

    public static ConvertDocumentsRequest convertRequestFromBytes(byte[] data,
                                                                  String filename,
                                                                  ConvertDocumentsRequestOptions options) {
        String base64 = Base64.getEncoder().encodeToString(data);
        SourcesInner src = new SourcesInner(new FileSourceRequest()
                .base64String(base64)
                .filename(filename)
                .kind("file"));
        ConvertDocumentsRequestOptions effective = options != null ? options : defaultConvertOptions();
        return new ConvertDocumentsRequest()
                .addSourcesItem(src)
                .target(new Target(new InBodyTarget()))
                .options(effective);
    }

    public static ConvertDocumentsRequest convertRequestFromBytes(byte[] data, String filename) {
        return convertRequestFromBytes(data, filename, OutputFormat.MD);
    }

    public static ConvertDocumentsRequestOptions defaultConvertOptions(OutputFormat format) {
        return new ConvertDocumentsRequestOptions()
                .fromFormats(Arrays.asList(InputFormat.values()))
                .toFormats(Collections.singletonList(format))
                .imageExportMode(ImageRefMode.EMBEDDED)
                .doOcr(true)
                .forceOcr(false)
                .ocrEngine(OcrEnginesEnum.EASYOCR)
                .pdfBackend(PdfBackend.DLPARSE_V4)
                .tableMode(TableFormerMode.ACCURATE)
                .tableCellMatching(true)
                .pipeline(ProcessingPipeline.STANDARD)
                .documentTimeout(new BigDecimal("604800"))
                .abortOnError(false)
                .doTableStructure(true)
                .includeImages(true)
                .imagesScale(new BigDecimal("2.0"))
                .mdPageBreakPlaceholder("")
                .doCodeEnrichment(false)
                .doFormulaEnrichment(false)
                .doPictureClassification(false)
                .doPictureDescription(false)
                .pictureDescriptionAreaThreshold(new BigDecimal("0.05"));
    }

    public static ConvertDocumentsRequestOptions defaultConvertOptions() {
        return defaultConvertOptions(OutputFormat.MD);
    }

    public static HybridChunkerOptions defaultChunkOptions() {
        return new HybridChunkerOptions()
                .useMarkdownTables(true)
                .includeRawText(false)
                .maxTokens(new MaxTokens(256))
                .tokenizer("sentence-transformers/all-MiniLM-L6-v2")
                .mergePeers(true);
    }

    private static Map<String, Object> convertFormFields(TargetName targetType, OutputFormat outputFormat) {
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("target_type", targetType != null ? targetType.toString() : null);
        if (outputFormat != null) {
            fields.put("to_formats", Collections.singletonList(outputFormat.toString()));
        }
        return fields;
    }

    private static Target1 resolveChunkTarget(TargetName targetType) {
        TargetName resolved = targetType != null ? targetType : TargetName.INBODY;
        return switch (resolved) {
            case ZIP -> new Target1(new ZipTarget());
            case INBODY -> new Target1(new InBodyTarget());
        };
    }

    private static List<String> expandFieldValues(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                results.add(Objects.toString(item, ""));
            }
            return results;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                results.add(Objects.toString(java.lang.reflect.Array.get(value, i), ""));
            }
            return results;
        }
        results.add(Objects.toString(value, ""));
        return results;
    }

    /**
     * Configures an ObjectMapper with the custom ConvertDocumentResponse deserializer.
     * This is required for proper handling of Docling API responses.
     * <p>
     * Important: Any custom ObjectMapper used with this client must be configured via this method.
     *
     * @param mapper ObjectMapper to configure (no-op if null)
     */
    static void configureObjectMapper(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (mapper == null) {
            return;
        }
        var fallback = mapper.copy();
        mapper.registerModule(new ConvertDocumentResponseModule(fallback));
    }

    private static long durationMillis(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return Math.max(0L, millis);
    }

    private static boolean flagEnabled(String name) {
        String raw = System.getenv(name);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            default:
                return false;
        }
    }

    private static void configureLogLevel() {
        String level = LOG_LEVEL_ENV;
        if (level != null) {
            setGlobalLogLevel(level);
        }
    }

    private static void logVersionInfo() {
        try {
            Package pkg = DoclingClient.class.getPackage();
            String version = pkg != null ? pkg.getImplementationVersion() : null;
            String buildTime = null;
            var source = DoclingClient.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                var uri = source.getLocation().toURI();
                Path path = Paths.get(uri);
                if (Files.exists(path)) {
                    buildTime = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).toString();
                }
            }
            log.info("DoclingClient build version={} compiledAt={}",
                    version != null ? version : "unknown",
                    buildTime != null ? buildTime : "unknown");
        } catch (Exception e) {
            log.info("DoclingClient build metadata unavailable: {}", e.toString());
        }
    }

    /**
     * Sets the global Log4j log level from a string (e.g., "DEBUG", "INFO").
     *
     * @param levelName Log level name (case-insensitive)
     */
    public static void setGlobalLogLevel(String levelName) {
        if (levelName == null || levelName.isEmpty()) {
            return;
        }
        try {
            setGlobalLogLevel(Level.valueOf(levelName.trim().toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            log.warn("Invalid log level '{}': {}", levelName, e.getMessage());
        }
    }

    /**
     * Sets the global Log4j log level.
     *
     * @param level Log4j Level to set
     */
    public static void setGlobalLogLevel(Level level) {
        if (level == null) {
            return;
        }
        Configurator.setRootLevel(level);
        Configurator.setLevel("com.docling.client", level);
        log.info("Log level set to {}", level);
    }

    private static String describeFile(File file) {
        if (file == null) {
            return "file=<null>";
        }
        String path = file.getAbsolutePath();
        String size = file.exists() ? file.length() + "B" : "missing";
        return "file=" + path + " size=" + size;
    }

    private static String describeUrls(List<String> urls) {
        if (urls == null) {
            return "urls=<null>";
        }
        int count = urls.size();
        List<String> preview = urls.subList(0, Math.min(count, 3));
        return "urls(count=" + count + ", preview=" + preview + ")";
    }

    private static String describeConvertOptions(ConvertDocumentsRequestOptions options) {
        if (options == null) {
            return "convertOptions=<default>";
        }
        return "convertOptions[toFormats=" + options.getToFormats() +
                ", doOcr=" + options.getDoOcr() +
                ", pipeline=" + options.getPipeline() +
                ", documentTimeout=" + options.getDocumentTimeout() + "]";
    }

    private static String describeChunkOptions(HybridChunkerOptions options) {
        if (options == null) {
            return "chunkOptions=<default>";
        }
        return "chunkOptions[maxTokens=" + options.getMaxTokens() +
                ", includeRawText=" + options.getIncludeRawText() +
                ", mergePeers=" + options.getMergePeers() +
                ", tokenizer=" + options.getTokenizer() + "]";
    }

    private static String describeTask(TaskStatusResponse task) {
        if (task == null) {
            return "task=<null>";
        }
        return "taskId=" + task.getTaskId() +
                " status=" + task.getTaskStatus() +
                " type=" + task.getTaskType() +
                " position=" + task.getTaskPosition();
    }

    private static String describeChunkResponse(ChunkDocumentResponse response) {
        if (response == null) {
            return "chunkResponse=<null>";
        }
        int chunkCount = response.getChunks() != null ? response.getChunks().size() : 0;
        int docCount = response.getDocuments() != null ? response.getDocuments().size() : 0;
        return "chunkResponse[chunks=" + chunkCount +
                ", documents=" + docCount +
                ", processingTime=" + response.getProcessingTime() + "]";
    }

    public static OcrScenarioRunner.InputSource ocrSourceFromUrl(String url) {
        return OcrScenarioRunner.InputSource.url(url);
    }

    public static OcrScenarioRunner.InputSource ocrSourceFromFile(Path path) {
        return OcrScenarioRunner.InputSource.file(path);
    }

    public static OcrScenarioRunner.OcrMatrixConfig defaultOcrScenarioMatrix() {
        return OcrScenarioRunner.defaultMatrixConfig();
    }

    public static List<OcrScenarioRunner.OcrScenario> buildOcrScenarioMatrix(OcrScenarioRunner.OcrMatrixConfig config) {
        OcrScenarioRunner.OcrMatrixConfig effective =
                config != null ? config : OcrScenarioRunner.defaultMatrixConfig();
        return OcrScenarioRunner.buildScenarioMatrix(effective);
    }

    private static String describeRawPayload(String payload) {
        if (payload == null) {
            return "payload=<null>";
        }
        return "payloadLength=" + payload.length();
    }

    private static String describePayload(TaskResultPayload payload) {
        if (payload == null) {
            return "payload=<null>";
        }
        return "payloadBytes=" + payload.body().length +
                " contentType=" + payload.contentType() +
                " isZip=" + payload.isZip();
    }

    private static String describeHealthResponse(HealthCheckResponse response) {
        if (response == null) {
            return "health=<null>";
        }
        return "healthStatus=" + response.getStatus();
    }

    private static String describeClearResponse(ClearResponse response) {
        if (response == null) {
            return "clearResponse=<null>";
        }
        return "clearStatus=" + response.getStatus();
    }

    public String getBaseUrl() {
        return apiClient.getBaseUri();
    }

    public void setBaseUrl(String baseUrl) {
        apiClient.updateBaseUri(baseUrl);
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        rebuildInterceptors();
    }

    public boolean isTraceHttp() {
        return traceHttp;
    }

    public void setTraceHttp(boolean traceHttp) {
        this.traceHttp = traceHttp;
        rebuildInterceptors();
    }

    /**
     * Sets the Log4j level for DoclingClient logs (instance helper that delegates to {@link #setGlobalLogLevel(Level)}).
     *
     * @param level Log4j level to apply (applies to both the root logger and {@code com.docling.client}).
     */
    public void setLogLevel(Level level) {
        setGlobalLogLevel(level);
    }

    /**
     * Convenience setter that accepts a level name (e.g., "DEBUG", "INFO") and applies it to Log4j.
     *
     * @param levelName Level name (case-insensitive).
     */
    public void setLogLevel(String levelName) {
        setGlobalLogLevel(levelName);
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        metricsCollector.setEnabled(metricsEnabled);
        rebuildInterceptors();
    }

    /**
     * Gets the current retry policy used for HTTP operations.
     *
     * @return The active retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Sets a custom retry policy for HTTP operations.
     * Use RetryPolicy.noRetry() to disable retries.
     *
     * @param retryPolicy The retry policy to use (null defaults to RetryPolicy.defaultPolicy())
     */
    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
    }

    /**
     * Gets the ExecutorService used for CompletableFuture operations.
     * If null, CompletableFuture operations use the common ForkJoinPool.
     *
     * @return The configured ExecutorService, or null if using the default
     */
    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Sets a custom ExecutorService for CompletableFuture operations.
     * This allows you to control thread pool size, thread names, and shutdown behavior.
     * If set to null, CompletableFuture operations will use the common ForkJoinPool.
     * <p>
     * Example usage:
     * <pre>{@code
     * ExecutorService executor = Executors.newFixedThreadPool(10,
     *     new ThreadFactoryBuilder().setNameFormat("docling-async-%d").build());
     * client.setAsyncExecutor(executor);
     * // Remember to shutdown the executor when done
     * executor.shutdown();
     * }</pre>
     *
     * @param asyncExecutor The ExecutorService to use for async operations, or null for default
     */
    public void setAsyncExecutor(ExecutorService asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Closes this client and releases any resources associated with it.
     * If a custom ExecutorService was set via {@link #setAsyncExecutor(ExecutorService)},
     * it will be shut down gracefully.
     * <p>
     * This method waits up to {@value #SHUTDOWN_TIMEOUT_SECONDS} seconds for pending tasks
     * to complete before forcing a shutdown.
     * <p>
     * Example:
     * <pre>{@code
     * try (DoclingClient client = DoclingClient.fromEnv()) {
     *     // Use the client
     *     client.convertFile(file);
     * } // Automatically closed
     * }</pre>
     */
    @Override
    public void close() {
        if (asyncExecutor != null) {
            log.debug("Shutting down async executor");
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Async executor did not terminate within {}s, forcing shutdown",
                            SHUTDOWN_TIMEOUT_SECONDS);
                    asyncExecutor.shutdownNow();
                    if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("Async executor did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for async executor shutdown");
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.debug("DoclingClient closed");
    }

    /**
     * Checks the health status of the Docling service.
     *
     * @return Health check response
     */
    public HealthCheckResponse health() {
        String correlationId = logEndpointRequest("health", () -> "baseUrl=" + apiClient.getBaseUri());
        HealthCheckResponse response = healthApi.healthHealthGet();
        logEndpointResponse("health", correlationId, () -> describeHealthResponse(response));
        return response;
    }

    /**
     * Converts a file synchronously using multipart upload.
     * Blocks until conversion completes or times out.
     *
     * @param file         File to convert
     * @param targetType   Target type (e.g., INBODY)
     * @param outputFormat Desired output format (e.g., MD, JSON)
     * @return Conversion response with converted document
     */
    public ResponseProcessFileV1ConvertFilePost convertFile(File file,
                                                            TargetName targetType,
                                                            OutputFormat outputFormat) {
        String correlationId = logEndpointRequest("convertFile", () ->
                describeFile(file) + " target=" + targetType + " output=" + outputFormat);
        ResponseProcessFileV1ConvertFilePost response = postMultipart("/v1/convert/file",
                convertFormFields(targetType, outputFormat),
                file,
                ResponseProcessFileV1ConvertFilePost.class);
        logEndpointResponse("convertFile", correlationId, () -> describeConvertEnvelope(response));
        return response;
    }

    /**
     * Converts a file synchronously with default options (INBODY, Markdown).
     *
     * @param file File to convert
     * @return Conversion response
     */
    public ResponseProcessFileV1ConvertFilePost convertFile(File file) {
        return convertFile(file, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Converts a file asynchronously using multipart upload.
     * Returns immediately with a task ID for later polling.
     *
     * @param file         File to convert
     * @param targetType   Target type
     * @param outputFormat Desired output format
     * @return Task status with task ID
     */
    public TaskStatusResponse convertFileAsync(File file,
                                               TargetName targetType,
                                               OutputFormat outputFormat) {
        String correlationId = logEndpointRequest("convertFileAsync", () ->
                describeFile(file) + " target=" + targetType + " output=" + outputFormat);
        TaskStatusResponse response = postMultipart("/v1/convert/file/async",
                convertFormFields(targetType, outputFormat),
                file,
                TaskStatusResponse.class);
        logEndpointResponse("convertFileAsync", correlationId, () -> describeTask(response));
        return response;
    }

    /**
     * Converts a file asynchronously with default options (INBODY, Markdown).
     *
     * @param file File to convert
     * @return Task status with task ID
     */
    public TaskStatusResponse convertFileAsync(File file) {
        return convertFileAsync(file, TargetName.INBODY, OutputFormat.MD);
    }

    public ResponseProcessUrlV1ConvertSourcePost convertUrl(String url,
                                                            ConvertDocumentsRequestOptions options) {
        String correlationId = logEndpointRequest("convertUrl", () ->
                "url=" + url + " " + describeConvertOptions(options));
        ConvertDocumentsRequest request = new ConvertDocumentsRequest();
        if (options != null) {
            request.setOptions(options);
        }
        request.addSourcesItem(new SourcesInner(new HttpSourceRequest().url(URI.create(url))));
        ResponseProcessUrlV1ConvertSourcePost response = convertApi.processUrlV1ConvertSourcePost(request);
        logEndpointResponse("convertUrl", correlationId, () -> describeConvertEnvelope(response));
        return response;
    }

    public ResponseProcessUrlV1ConvertSourcePost convertUrl(String url) {
        return convertUrl(url, defaultConvertOptions(OutputFormat.MD));
    }

    public TaskStatusResponse convertUrlAsync(String url,
                                              ConvertDocumentsRequestOptions options) {
        String correlationId = logEndpointRequest("convertUrlAsync", () ->
                "url=" + url + " " + describeConvertOptions(options));
        ConvertDocumentsRequest request = new ConvertDocumentsRequest();
        if (options != null) {
            request.setOptions(options);
        }
        request.addSourcesItem(new SourcesInner(new HttpSourceRequest().url(URI.create(url))));
        TaskStatusResponse response = convertApi.processUrlAsyncV1ConvertSourceAsyncPost(request);
        logEndpointResponse("convertUrlAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse convertUrlAsync(String url) {
        return convertUrlAsync(url, defaultConvertOptions(OutputFormat.MD));
    }

    public ChunkDocumentResponse chunkHybridSources(List<String> urls,
                                                    HybridChunkerOptions chunkOptions,
                                                    boolean includeConvertedDoc) {
        String correlationId = logEndpointRequest("chunkHybridSources", () ->
                describeUrls(urls) + " " + describeChunkOptions(chunkOptions) +
                        " includeConvertedDoc=" + includeConvertedDoc);
        HybridChunkerOptionsDocumentsRequest request = new HybridChunkerOptionsDocumentsRequest()
                .includeConvertedDoc(includeConvertedDoc)
                .chunkingOptions(chunkOptions);
        for (String url : urls) {
            request.addSourcesItem(new SourcesInner(new HttpSourceRequest().url(URI.create(url))));
        }
        ChunkDocumentResponse response = chunkApi.chunkSourcesWithHybridChunkerV1ChunkHybridSourcePost(request);
        logEndpointResponse("chunkHybridSources", correlationId, () -> describeChunkResponse(response));
        return response;
    }

    public ChunkDocumentResponse chunkHybridSources(List<String> urls) {
        return chunkHybridSources(urls, defaultChunkOptions(), false);
    }

    public TaskStatusResponse chunkHybridSourcesAsync(List<String> urls,
                                                      HybridChunkerOptions chunkOptions,
                                                      boolean includeConvertedDoc,
                                                      ConvertDocumentsRequestOptions convertOptions) {
        HybridChunkerOptions effectiveChunk = chunkOptions != null ? chunkOptions : defaultChunkOptions();
        ConvertDocumentsRequestOptions effectiveConvert = convertOptions;
        String correlationId = logEndpointRequest("chunkHybridSourcesAsync", () ->
                describeUrls(urls) + " " + describeChunkOptions(effectiveChunk) +
                        " includeConvertedDoc=" + includeConvertedDoc +
                        (effectiveConvert != null ? " " + describeConvertOptions(effectiveConvert) : ""));
        HybridChunkerOptionsDocumentsRequest request = new HybridChunkerOptionsDocumentsRequest()
                .includeConvertedDoc(includeConvertedDoc)
                .chunkingOptions(effectiveChunk);
        if (effectiveConvert != null) {
            request.convertOptions(effectiveConvert);
        }
        for (String url : urls) {
            request.addSourcesItem(new SourcesInner(new HttpSourceRequest().url(URI.create(url))));
        }
        TaskStatusResponse response = chunkApi.chunkSourcesWithHybridChunkerAsAsyncTaskV1ChunkHybridSourceAsyncPost(request);
        logEndpointResponse("chunkHybridSourcesAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse chunkHybridSourcesAsync(List<String> urls) {
        return chunkHybridSourcesAsync(urls, defaultChunkOptions(), false);
    }

    public TaskStatusResponse chunkHybridSourcesAsync(List<String> urls,
                                                      HybridChunkerOptions chunkOptions,
                                                      boolean includeConvertedDoc) {
        return chunkHybridSourcesAsync(urls, chunkOptions, includeConvertedDoc, null);
    }

    // ============================================================================
    // CompletableFuture-based async operations for modern framework compatibility
    // ============================================================================

    public TaskStatusResponse chunkHybridFilesAsync(File file,
                                                    boolean includeConvertedDoc,
                                                    TargetName targetType) {
        String correlationId = logEndpointRequest("chunkHybridFilesAsync", () ->
                describeFile(file) + " includeConvertedDoc=" + includeConvertedDoc +
                        " target=" + targetType);
        TaskStatusResponse response = postMultipart("/v1/chunk/hybrid/file/async",
                Map.of(
                        "include_converted_doc", Boolean.toString(includeConvertedDoc),
                        "target_type", targetType.toString()
                ),
                file,
                TaskStatusResponse.class);
        logEndpointResponse("chunkHybridFilesAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public ChunkDocumentResponse chunkHybridFile(File file,
                                                 boolean includeConvertedDoc,
                                                 TargetName targetType) {
        String correlationId = logEndpointRequest("chunkHybridFile", () ->
                describeFile(file) + " includeConvertedDoc=" + includeConvertedDoc +
                        " target=" + targetType);
        ChunkDocumentResponse response = postMultipart("/v1/chunk/hybrid/file",
                Map.of(
                        "include_converted_doc", Boolean.toString(includeConvertedDoc),
                        "target_type", targetType.toString()
                ),
                file,
                ChunkDocumentResponse.class);
        logEndpointResponse("chunkHybridFile", correlationId, () -> describeChunkResponse(response));
        return response;
    }

    public String chunkHybridFileRaw(File file,
                                     boolean includeConvertedDoc,
                                     TargetName targetType) {
        String correlationId = logEndpointRequest("chunkHybridFileRaw", () ->
                describeFile(file) + " includeConvertedDoc=" + includeConvertedDoc +
                        " target=" + targetType);
        String payload = postMultipartRaw("/v1/chunk/hybrid/file",
                Map.of(
                        "include_converted_doc", Boolean.toString(includeConvertedDoc),
                        "target_type", targetType.toString()
                ),
                file);
        logEndpointResponse("chunkHybridFileRaw", correlationId, () -> describeRawPayload(payload));
        return payload;
    }

    public TaskStatusResponse chunkHybridFilesAsync(File file) {
        return chunkHybridFilesAsync(file, false, TargetName.INBODY);
    }

    public ChunkDocumentResponse chunkHybridFile(File file) {
        return chunkHybridFile(file, false, TargetName.INBODY);
    }

    public String chunkHybridFileRaw(File file) {
        return chunkHybridFileRaw(file, false, TargetName.INBODY);
    }

    /**
     * Polls the status of an async task with long-polling support.
     *
     * @param taskId Task ID to poll
     * @param wait   Maximum duration to wait for status change (server-side long poll)
     * @return Current task status
     */
    public TaskStatusResponse pollStatus(String taskId, Duration wait) {
        BigDecimal waitSeconds = wait == null ? null : BigDecimal.valueOf(wait.toSeconds());
        String correlationId = logEndpointRequest("pollStatus", () ->
                "taskId=" + taskId + " waitSeconds=" + (waitSeconds != null ? waitSeconds : "default"));
        TaskStatusResponse response = tasksApi.taskStatusPollV1StatusPollTaskIdGet(taskId, waitSeconds);
        logEndpointResponse("pollStatus", correlationId, () -> describeTask(response));
        return response;
    }

    /**
     * Polls the status of an async task with default 5-second wait.
     *
     * @param taskId Task ID to poll
     * @return Current task status
     */
    public TaskStatusResponse pollStatus(String taskId) {
        return pollStatus(taskId, Duration.ofSeconds(5));
    }

    /**
     * Fetches the result of a completed async task.
     *
     * @param taskId Task ID to fetch
     * @return Task result containing converted document or chunk response
     */
    public ResponseTaskResultV1ResultTaskIdGet fetchResult(String taskId) {
        log.info("Fetching result for task {}", taskId);
        String correlationId = logEndpointRequest("fetchResult", () -> "taskId=" + taskId);
        ResponseTaskResultV1ResultTaskIdGet result = tasksApi.taskResultV1ResultTaskIdGet(taskId);
        logEndpointResponse("fetchResult", correlationId, () -> describeTaskResult(result));
        return result;
    }

    /**
     * Waits for an async task to complete and returns its result.
     * Uses environment variables POLL_MAX_SECONDS (default: 900) and POLL_WAIT_SECONDS (default: 10).
     *
     * @param taskId The task ID to wait for
     * @return The task result when the task completes successfully
     * @throws DoclingTaskFailureException if the task fails
     * @throws DoclingTimeoutException     if the task doesn't complete within the timeout
     * @throws DoclingClientException      if interrupted while waiting
     */
    public ResponseTaskResultV1ResultTaskIdGet waitForTaskResult(String taskId) {
        long maxSeconds = Long.parseLong(System.getenv().getOrDefault("POLL_MAX_SECONDS",
                String.valueOf(DEFAULT_POLL_MAX_SECONDS)));
        long waitSeconds = Long.parseLong(System.getenv().getOrDefault("POLL_WAIT_SECONDS",
                String.valueOf(DEFAULT_POLL_WAIT_SECONDS)));
        return waitForTaskResult(taskId, maxSeconds, waitSeconds);
    }

    private ResponseTaskResultV1ResultTaskIdGet waitForTaskResult(String taskId, long maxSeconds, long waitSeconds) {
        String correlationId = logEndpointRequest("waitForTaskResult", () ->
                "taskId=" + taskId + " maxSeconds=" + maxSeconds + " waitStep=" + waitSeconds);
        long elapsed = 0;
        int consecutiveErrors = 0;
        while (elapsed < maxSeconds) {
            try {
                TaskStatusResponse status = pollStatus(taskId, Duration.ofSeconds(waitSeconds));
                consecutiveErrors = 0; // Reset on success
                String state = status != null ? status.getTaskStatus() : null;
                if (isSuccessStatus(state)) {
                    log.info("Task {} completed status={}", taskId, state);
                    ResponseTaskResultV1ResultTaskIdGet result = fetchResult(taskId);
                    logEndpointResponse("waitForTaskResult", correlationId, () -> describeTaskResult(result));
                    return result;
                }
                if (isFailureStatus(state)) {
                    logEndpointResponse("waitForTaskResult", correlationId, () ->
                            "taskId=" + taskId + " terminalState=" + state);
                    throw new DoclingTaskFailureException(
                            "Task failed",
                            taskId,
                            state,
                            status.getTaskMeta()
                    );
                }
                log.info("Task {} pending status={} after {}s", taskId, state != null ? state : "pending", elapsed);
            } catch (DoclingTaskFailureException e) {
                // Task itself failed - don't retry, propagate immediately
                throw e;
            } catch (DoclingClientException e) {
                // Docling-specific exceptions that shouldn't be retried
                if (e instanceof DoclingTimeoutException || e instanceof DoclingHttpException) {
                    throw e;
                }
                // Other client exceptions - retry
                consecutiveErrors++;
                if (consecutiveErrors >= 3) {
                    log.error("Task {} poll failed {} times consecutively, giving up", taskId, consecutiveErrors);
                    throw new DoclingClientException(
                            "Failed to poll task status for task " + taskId +
                            " after " + consecutiveErrors + " attempts: " + e.getMessage(),
                            e
                    );
                }
                log.warn("Task {} poll failed (attempt {}/3), will retry: {}",
                         taskId, consecutiveErrors, e.getMessage());
                // Brief wait before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DoclingClientException("Interrupted while waiting for task " + taskId, ie);
                }
                continue; // Skip the normal sleep and elapsed increment
            } catch (Exception e) {
                // Unknown exceptions (network errors, etc.) - retry
                consecutiveErrors++;
                if (consecutiveErrors >= 3) {
                    log.error("Task {} poll failed {} times consecutively, giving up", taskId, consecutiveErrors);
                    throw new DoclingClientException(
                            "Failed to poll task status for task " + taskId +
                            " after " + consecutiveErrors + " attempts: " + e.getMessage(),
                            e
                    );
                }
                log.warn("Task {} poll failed (attempt {}/3), will retry: {}",
                         taskId, consecutiveErrors, e.getMessage());
                // Brief wait before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DoclingClientException("Interrupted while waiting for task " + taskId, ie);
                }
                continue; // Skip the normal sleep and elapsed increment
            }
            try {
                Thread.sleep(waitSeconds * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DoclingClientException("Interrupted while waiting for task " + taskId, e);
            }
            elapsed += waitSeconds;
        }
        log.warn("Task {} still not finished after {}s", taskId, maxSeconds);
        logEndpointResponse("waitForTaskResult", correlationId, () ->
                "taskId=" + taskId + " timeoutAfter=" + maxSeconds);
        throw new DoclingTimeoutException(
                "Task did not complete within timeout",
                "waitForTaskResult",
                maxSeconds
        );
    }

    /**
     * Downloads the raw task result payload (may be ZIP or JSON).
     * Useful when the server returns presigned URLs instead of inline content.
     * This operation is automatically retried on transient network failures.
     *
     * @param taskId Task ID to download
     * @return Task result payload with content type
     * @throws DoclingNetworkException if download fails after retries
     * @throws DoclingHttpException    if server returns an error response
     * @throws DoclingClientException  if interrupted during download
     */
    public TaskResultPayload downloadTaskResultPayload(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        URI uri = URI.create(apiClient.getBaseUri() + "/v1/result/" + taskId);

        String correlationId = logEndpointRequest("downloadTaskResultPayload", () -> "taskId=" + taskId);
        TaskResultPayload payload = retryPolicy.execute(() -> {
            long start = System.nanoTime();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Accept", "application/zip")
                    .timeout(apiClient.getReadTimeout() != null ? apiClient.getReadTimeout() : DEFAULT_REQUEST_TIMEOUT)
                    .GET();
            var interceptor = apiClient.getRequestInterceptor();
            if (interceptor != null) {
                interceptor.accept(req);
            }

            HttpRequest httpRequest = req.build();
            HttpResponse<byte[]> resp;
            try {
                resp = apiClient.getHttpClient()
                        .send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                throw new DoclingNetworkException("Failed to download task result", "GET", uri, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DoclingClientException("Interrupted while downloading task " + taskId, e);
            }

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String body = resp.body() != null ? new String(resp.body()) : "";
                throw new DoclingHttpException(
                        "Failed to download task result",
                        resp.statusCode(),
                        "GET",
                        uri,
                        body
                );
            }

            emitHttpTrace("GET", httpRequest.uri(), resp.statusCode(), durationMillis(start), "task-download",
                    resp.headers().map(), resp.headers().firstValue("Content-Type").orElse(null));
            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            return new TaskResultPayload(resp.body(), contentType);
        });
        logEndpointResponse("downloadTaskResultPayload", correlationId, () -> describePayload(payload));
        return payload;
    }

    /**
     * Clears cached task results on the server.
     *
     * @param olderThanSeconds Only clear results older than this many seconds (null for all)
     * @return Clear operation response
     */
    public ClearResponse clearResults(Integer olderThanSeconds) {
        BigDecimal older = olderThanSeconds == null ? null : BigDecimal.valueOf(olderThanSeconds);
        String correlationId = logEndpointRequest("clearResults", () ->
                "olderThanSeconds=" + (older != null ? older : "all"));
        ClearResponse response = clearApi.clearResultsV1ClearResultsGet(older);
        logEndpointResponse("clearResults", correlationId, () -> describeClearResponse(response));
        return response;
    }

    /**
     * Clears cached converters on the server to free up memory.
     *
     * @return Clear operation response
     */
    public ClearResponse clearConverters() {
        String correlationId = logEndpointRequest("clearConverters", () -> "baseUrl=" + apiClient.getBaseUri());
        ClearResponse response = clearApi.clearConvertersV1ClearConvertersGet();
        logEndpointResponse("clearConverters", correlationId, () -> describeClearResponse(response));
        return response;
    }

    /**
     * Waits for an async task to complete, returning a non-blocking CompletableFuture.
     * This is the modern, framework-compatible version of {@link #waitForTaskResult(String)}.
     * <p>
     * The polling happens in a background thread (from the configured ExecutorService or
     * ForkJoinPool.commonPool() if none is set), so this method returns immediately.
     * <p>
     * Usage examples:
     * <pre>{@code
     * // Basic usage
     * CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
     *     client.waitForTaskResultAsync(taskId);
     * ResponseTaskResultV1ResultTaskIdGet result = future.get(); // or .join()
     *
     * // With timeout (Java 9+)
     * ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResultAsync(taskId)
     *     .orTimeout(5, TimeUnit.MINUTES)
     *     .get();
     *
     * // Chaining operations
     * client.waitForTaskResultAsync(taskId)
     *     .thenApply(result -> ConversionResults.unwrap(result, ConversionOutputType.MARKDOWN))
     *     .thenAccept(doc -> saveToDatabase(doc))
     *     .exceptionally(ex -> {
     *         log.error("Conversion failed", ex);
     *         return null;
     *     });
     *
     * // Spring WebFlux integration
     * Mono<ResponseTaskResultV1ResultTaskIdGet> mono =
     *     Mono.fromFuture(client.waitForTaskResultAsync(taskId));
     * }</pre>
     *
     * @param taskId The task ID to wait for
     * @return CompletableFuture that completes when the task finishes
     * @see #waitForTaskResult(String) for the blocking version
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> waitForTaskResultAsync(String taskId) {
        return executeAsync(() -> waitForTaskResult(taskId));
    }

    /**
     * Converts a file asynchronously, returning a CompletableFuture with the result.
     * This is the modern, non-blocking version that integrates with async frameworks.
     * <p>
     * Works with all output formats: MD, JSON, HTML, TEXT, DOCTAGS.
     * <p>
     * Usage:
     * <pre>{@code
     * // Non-blocking async conversion
     * CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
     *     client.convertFileAsyncFuture(file, TargetName.INBODY, OutputFormat.MD);
     *
     * // Do other work while waiting...
     * System.out.println("Doing other work...");
     *
     * // Get result when ready
     * ResponseTaskResultV1ResultTaskIdGet result = future.get();
     * }</pre>
     *
     * @param file         File to convert
     * @param targetType   Target type (e.g., INBODY)
     * @param outputFormat Desired output format (MD, JSON, HTML, TEXT, DOCTAGS)
     * @return CompletableFuture with the task result
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertFileAsyncFuture(
            File file,
            TargetName targetType,
            OutputFormat outputFormat) {
        TaskStatusResponse task = convertFileAsync(file, targetType, outputFormat);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Converts a file asynchronously with default options (INBODY, Markdown).
     *
     * @param file File to convert
     * @return CompletableFuture with the task result
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertFileAsyncFuture(File file) {
        return convertFileAsyncFuture(file, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Converts a URL asynchronously, returning a CompletableFuture with the result.
     * Works with all output formats specified in the options.
     * <p>
     * Usage:
     * <pre>{@code
     * ConvertDocumentsRequestOptions options = DoclingClient.defaultConvertOptions(OutputFormat.JSON);
     * CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
     *     client.convertUrlAsyncFuture(url, options);
     * }</pre>
     *
     * @param url     URL to convert
     * @param options Conversion options (specifies output format and other settings)
     * @return CompletableFuture with the task result
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertUrlAsyncFuture(
            String url,
            ConvertDocumentsRequestOptions options) {
        TaskStatusResponse task = convertUrlAsync(url, options);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Converts a URL asynchronously with default Markdown options.
     *
     * @param url URL to convert
     * @return CompletableFuture with the task result
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertUrlAsyncFuture(String url) {
        return convertUrlAsyncFuture(url, defaultConvertOptions(OutputFormat.MD));
    }

    /**
     * Converts a stream asynchronously, returning a CompletableFuture.
     * Memory-efficient for large files. Works with all output formats.
     *
     * @param stream       Input stream to convert
     * @param filename     Filename (used for format detection)
     * @param outputFormat Desired output format
     * @return CompletableFuture with the task result
     * @throws IOException If stream cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertStreamAsyncFuture(
            InputStream stream,
            String filename,
            OutputFormat outputFormat) throws IOException {
        TaskStatusResponse task = convertStreamAsync(stream, filename, outputFormat);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Converts a stream asynchronously with custom options.
     *
     * @param stream   Input stream to convert
     * @param filename Filename (used for format detection)
     * @param options  Conversion options (specifies output format)
     * @return CompletableFuture with the task result
     * @throws IOException If stream cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertStreamAsyncFuture(
            InputStream stream,
            String filename,
            ConvertDocumentsRequestOptions options) throws IOException {
        TaskStatusResponse task = convertStreamAsync(stream, filename, options);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Converts a stream asynchronously with default Markdown options.
     *
     * @param stream   Input stream to convert
     * @param filename Filename (used for format detection)
     * @return CompletableFuture with the task result
     * @throws IOException If stream cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertStreamAsyncFuture(
            InputStream stream,
            String filename) throws IOException {
        return convertStreamAsyncFuture(stream, filename, OutputFormat.MD);
    }

    /**
     * Converts a file asynchronously using multipart streaming upload.
     * Returns a CompletableFuture for framework integration.
     *
     * @param file         File to convert
     * @param targetType   Target type
     * @param outputFormat Desired output format
     * @return CompletableFuture with the task result
     * @throws IOException If file cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertMultipartAsyncFuture(
            File file,
            TargetName targetType,
            OutputFormat outputFormat) throws IOException {
        TaskStatusResponse task = convertMultipartAsync(file, targetType, outputFormat);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Converts a file asynchronously using multipart with default options.
     *
     * @param file File to convert
     * @return CompletableFuture with the task result
     * @throws IOException If file cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> convertMultipartAsyncFuture(File file)
            throws IOException {
        return convertMultipartAsyncFuture(file, TargetName.INBODY, OutputFormat.MD);
    }

    /**
     * Chunks URLs asynchronously using hybrid chunker, returning a CompletableFuture.
     * Used for RAG (Retrieval-Augmented Generation) applications.
     * <p>
     * Usage:
     * <pre>{@code
     * List<String> urls = Arrays.asList("https://example.com/doc.pdf");
     * CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
     *     client.chunkHybridSourcesAsyncFuture(urls, options, true, convertOpts);
     *
     * // Extract chunks when ready
     * future.thenAccept(result -> {
     *     ChunkDocumentResponse chunks = (ChunkDocumentResponse) result.getActualInstance();
     *     System.out.println("Chunks: " + chunks.getChunks().size());
     * });
     * }</pre>
     *
     * @param urls                URLs to chunk
     * @param chunkOptions        Hybrid chunker options
     * @param includeConvertedDoc Whether to include the converted document
     * @param convertOptions      Conversion options (if any)
     * @return CompletableFuture with the task result containing chunks
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridSourcesAsyncFuture(
            List<String> urls,
            HybridChunkerOptions chunkOptions,
            boolean includeConvertedDoc,
            ConvertDocumentsRequestOptions convertOptions) {
        TaskStatusResponse task = chunkHybridSourcesAsync(urls, chunkOptions, includeConvertedDoc, convertOptions);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Chunks URLs asynchronously with default options.
     *
     * @param urls URLs to chunk
     * @return CompletableFuture with the task result containing chunks
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridSourcesAsyncFuture(List<String> urls) {
        return chunkHybridSourcesAsyncFuture(urls, defaultChunkOptions(), false, null);
    }

    /**
     * Chunks URLs asynchronously with custom chunk options.
     *
     * @param urls                URLs to chunk
     * @param chunkOptions        Hybrid chunker options
     * @param includeConvertedDoc Whether to include the converted document
     * @return CompletableFuture with the task result containing chunks
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridSourcesAsyncFuture(
            List<String> urls,
            HybridChunkerOptions chunkOptions,
            boolean includeConvertedDoc) {
        return chunkHybridSourcesAsyncFuture(urls, chunkOptions, includeConvertedDoc, null);
    }

    /**
     * Chunks a file asynchronously using hybrid chunker, returning a CompletableFuture.
     *
     * @param file                File to chunk
     * @param includeConvertedDoc Whether to include the converted document
     * @param targetType          Target type
     * @return CompletableFuture with the task result containing chunks
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridFilesAsyncFuture(
            File file,
            boolean includeConvertedDoc,
            TargetName targetType) {
        TaskStatusResponse task = chunkHybridFilesAsync(file, includeConvertedDoc, targetType);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Chunks a file asynchronously with default options.
     *
     * @param file File to chunk
     * @return CompletableFuture with the task result containing chunks
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridFilesAsyncFuture(File file) {
        return chunkHybridFilesAsyncFuture(file, false, TargetName.INBODY);
    }

    /**
     * Chunks a stream asynchronously using hybrid chunker, returning a CompletableFuture.
     *
     * @param stream              Input stream to chunk
     * @param filename            Filename (used for format detection)
     * @param includeConvertedDoc Whether to include the converted document
     * @param targetType          Target type
     * @param convertOptions      Conversion options
     * @param chunkOptions        Chunking options
     * @return CompletableFuture with the task result containing chunks
     * @throws IOException If stream cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridStreamAsyncFuture(
            InputStream stream,
            String filename,
            boolean includeConvertedDoc,
            TargetName targetType,
            ConvertDocumentsRequestOptions convertOptions,
            HybridChunkerOptions chunkOptions) throws IOException {
        TaskStatusResponse task = chunkHybridStreamAsync(stream, filename, includeConvertedDoc,
                targetType, convertOptions, chunkOptions);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Chunks a stream asynchronously with default options.
     *
     * @param stream   Input stream to chunk
     * @param filename Filename (used for format detection)
     * @return CompletableFuture with the task result containing chunks
     * @throws IOException If stream cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridStreamAsyncFuture(
            InputStream stream,
            String filename) throws IOException {
        return chunkHybridStreamAsyncFuture(stream, filename, false, TargetName.INBODY, null, null);
    }

    /**
     * Chunks a file asynchronously using multipart upload, returning a CompletableFuture.
     *
     * @param file                File to chunk
     * @param includeConvertedDoc Whether to include the converted document
     * @return CompletableFuture with the task result containing chunks
     * @throws IOException If file cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridMultipartAsyncFuture(
            File file,
            boolean includeConvertedDoc) throws IOException {
        TaskStatusResponse task = chunkHybridMultipartAsync(file, includeConvertedDoc);
        return waitForTaskResultAsync(task.getTaskId());
    }

    /**
     * Chunks a file asynchronously using multipart with default options.
     *
     * @param file File to chunk
     * @return CompletableFuture with the task result containing chunks
     * @throws IOException If file cannot be read
     */
    public CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> chunkHybridMultipartAsyncFuture(File file)
            throws IOException {
        return chunkHybridMultipartAsyncFuture(file, false);
    }

    /**
     * Internal helper to execute blocking operations in a CompletableFuture.
     * Uses the configured ExecutorService or ForkJoinPool.commonPool() as fallback.
     */
    private <T> CompletableFuture<T> executeAsync(Supplier<T> supplier) {
        if (asyncExecutor != null) {
            return CompletableFuture.supplyAsync(supplier, asyncExecutor);
        } else {
            return CompletableFuture.supplyAsync(supplier);
        }
    }

    // Stream-based helpers for client libraries
    public ResponseProcessUrlV1ConvertSourcePost convertStream(InputStream stream,
                                                               String filename,
                                                               OutputFormat outputFormat) throws IOException {
        String correlationId = logEndpointRequest("convertStream", () ->
                "filename=" + filename + " output=" + outputFormat + " streamProvided=" + (stream != null));
        ConvertDocumentsRequest req = convertRequestFromStream(stream, filename, outputFormat);
        ResponseProcessUrlV1ConvertSourcePost response = convertApi.processUrlV1ConvertSourcePost(req);
        logEndpointResponse("convertStream", correlationId, () -> describeConvertEnvelope(response));
        return response;
    }

    public ResponseProcessUrlV1ConvertSourcePost convertStream(InputStream stream,
                                                               String filename,
                                                               ConvertDocumentsRequestOptions options) throws IOException {
        ConvertDocumentsRequestOptions effective = options != null ? options : defaultConvertOptions(OutputFormat.MD);
        String correlationId = logEndpointRequest("convertStream", () ->
                "filename=" + filename + " streamProvided=" + (stream != null) + " " + describeConvertOptions(effective));
        ConvertDocumentsRequest req = convertRequestFromStream(stream, filename, effective);
        ResponseProcessUrlV1ConvertSourcePost response = convertApi.processUrlV1ConvertSourcePost(req);
        logEndpointResponse("convertStream", correlationId, () -> describeConvertEnvelope(response));
        return response;
    }

    public ResponseProcessUrlV1ConvertSourcePost convertStream(InputStream stream,
                                                               String filename) throws IOException {
        return convertStream(stream, filename, OutputFormat.MD);
    }

    public ResponseProcessFileV1ConvertFilePost convertMultipart(InputStream stream,
                                                                 String filename,
                                                                 TargetName targetType,
                                                                 OutputFormat outputFormat) {
        String correlationId = logEndpointRequest("convertMultipart", () ->
                "filename=" + filename + " target=" + targetType + " output=" + outputFormat +
                        " streamProvided=" + (stream != null));
        ResponseProcessFileV1ConvertFilePost response = postMultipartStreaming("/v1/convert/file",
                convertFormFields(targetType, outputFormat),
                stream,
                filename,
                ResponseProcessFileV1ConvertFilePost.class);
        logEndpointResponse("convertMultipart", correlationId, () -> describeConvertEnvelope(response));
        return response;
    }

    public ResponseProcessFileV1ConvertFilePost convertMultipart(InputStream stream,
                                                                 String filename) {
        return convertMultipart(stream, filename, TargetName.INBODY, OutputFormat.MD);
    }

    public ResponseProcessFileV1ConvertFilePost convertMultipart(File file,
                                                                 TargetName targetType,
                                                                 OutputFormat outputFormat) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return convertMultipart(is, file.getName(), targetType, outputFormat);
        }
    }

    public ResponseProcessFileV1ConvertFilePost convertMultipart(File file) throws IOException {
        return convertMultipart(file, TargetName.INBODY, OutputFormat.MD);
    }

    public TaskStatusResponse convertStreamAsync(InputStream stream,
                                                 String filename,
                                                 OutputFormat outputFormat) throws IOException {
        String correlationId = logEndpointRequest("convertStreamAsync", () ->
                "filename=" + filename + " output=" + outputFormat + " streamProvided=" + (stream != null));
        ConvertDocumentsRequest req = convertRequestFromStream(stream, filename, outputFormat);
        TaskStatusResponse response = convertApi.processUrlAsyncV1ConvertSourceAsyncPost(req);
        logEndpointResponse("convertStreamAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse convertStreamAsync(InputStream stream,
                                                 String filename,
                                                 ConvertDocumentsRequestOptions options) throws IOException {
        ConvertDocumentsRequestOptions effective = options != null ? options : defaultConvertOptions(OutputFormat.MD);
        String correlationId = logEndpointRequest("convertStreamAsync", () ->
                "filename=" + filename + " streamProvided=" + (stream != null) + " " + describeConvertOptions(effective));
        ConvertDocumentsRequest req = convertRequestFromStream(stream, filename, effective);
        TaskStatusResponse response = convertApi.processUrlAsyncV1ConvertSourceAsyncPost(req);
        logEndpointResponse("convertStreamAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse convertStreamAsync(InputStream stream,
                                                 String filename) throws IOException {
        return convertStreamAsync(stream, filename, OutputFormat.MD);
    }

    public TaskStatusResponse convertStreamAsync(File file,
                                                 ConvertDocumentsRequestOptions options) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return convertStreamAsync(is, file.getName(), options);
        }
    }

    public TaskStatusResponse convertMultipartAsync(InputStream stream,
                                                    String filename,
                                                    TargetName targetType,
                                                    OutputFormat outputFormat) {
        String correlationId = logEndpointRequest("convertMultipartAsync", () ->
                "filename=" + filename + " target=" + targetType + " output=" + outputFormat +
                        " streamProvided=" + (stream != null));
        TaskStatusResponse response = postMultipartStreaming("/v1/convert/file/async",
                convertFormFields(targetType, outputFormat),
                stream,
                filename,
                TaskStatusResponse.class);
        logEndpointResponse("convertMultipartAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse convertMultipartAsync(InputStream stream,
                                                    String filename) {
        return convertMultipartAsync(stream, filename, TargetName.INBODY, OutputFormat.MD);
    }

    public TaskStatusResponse convertMultipartAsync(File file,
                                                    TargetName targetType,
                                                    OutputFormat outputFormat) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return convertMultipartAsync(is, file.getName(), targetType, outputFormat);
        }
    }

    public TaskStatusResponse convertMultipartAsync(File file) throws IOException {
        return convertMultipartAsync(file, TargetName.INBODY, OutputFormat.MD);
    }

    public TaskStatusResponse chunkHybridStreamAsync(InputStream stream,
                                                     String filename,
                                                     boolean includeConvertedDoc) throws IOException {
        return chunkHybridStreamAsync(stream, filename, includeConvertedDoc, TargetName.INBODY, null, null);
    }

    public TaskStatusResponse chunkHybridStreamAsync(InputStream stream,
                                                     String filename) throws IOException {
        return chunkHybridStreamAsync(stream, filename, false);
    }

    public TaskStatusResponse chunkHybridStreamAsync(InputStream stream,
                                                     String filename,
                                                     boolean includeConvertedDoc,
                                                     TargetName targetType,
                                                     ConvertDocumentsRequestOptions convertOptions,
                                                     HybridChunkerOptions chunkOptions) throws IOException {
        ConvertDocumentsRequestOptions effectiveConvert = convertOptions != null
                ? convertOptions
                : defaultConvertOptions(OutputFormat.MD);
        HybridChunkerOptions effectiveChunk = chunkOptions != null ? chunkOptions : defaultChunkOptions();
        TargetName actualTarget = targetType != null ? targetType : TargetName.INBODY;
        String correlationId = logEndpointRequest("chunkHybridStreamAsync", () ->
                "filename=" + filename + " includeConvertedDoc=" + includeConvertedDoc +
                        " target=" + actualTarget + " streamProvided=" + (stream != null) +
                        " " + describeConvertOptions(effectiveConvert) + " " + describeChunkOptions(effectiveChunk));
        ConvertDocumentsRequest convertRequest = convertRequestFromStream(stream, filename, effectiveConvert);
        HybridChunkerOptionsDocumentsRequest req = new HybridChunkerOptionsDocumentsRequest()
                .includeConvertedDoc(includeConvertedDoc)
                .target(resolveChunkTarget(actualTarget))
                .chunkingOptions(effectiveChunk)
                .convertOptions(effectiveConvert)
                .sources(convertRequest.getSources());
        TaskStatusResponse response = chunkApi.chunkSourcesWithHybridChunkerAsAsyncTaskV1ChunkHybridSourceAsyncPost(req);
        logEndpointResponse("chunkHybridStreamAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse chunkHybridStreamAsync(File file,
                                                     boolean includeConvertedDoc,
                                                     TargetName targetType,
                                                     ConvertDocumentsRequestOptions convertOptions,
                                                     HybridChunkerOptions chunkOptions) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return chunkHybridStreamAsync(is,
                    file.getName(),
                    includeConvertedDoc,
                    targetType,
                    convertOptions,
                    chunkOptions);
        }
    }

    public TaskStatusResponse chunkHybridMultipartAsync(InputStream stream,
                                                        String filename,
                                                        boolean includeConvertedDoc) {
        String correlationId = logEndpointRequest("chunkHybridMultipartAsync", () ->
                "filename=" + filename + " includeConvertedDoc=" + includeConvertedDoc +
                        " streamProvided=" + (stream != null));
        TaskStatusResponse response = postMultipartStreaming("/v1/chunk/hybrid/file/async",
                Map.of("include_converted_doc", Boolean.toString(includeConvertedDoc)),
                stream,
                filename,
                TaskStatusResponse.class);
        logEndpointResponse("chunkHybridMultipartAsync", correlationId, () -> describeTask(response));
        return response;
    }

    public TaskStatusResponse chunkHybridMultipartAsync(File file,
                                                        boolean includeConvertedDoc) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return chunkHybridMultipartAsync(is, file.getName(), includeConvertedDoc);
        }
    }

    public TaskStatusResponse chunkHybridMultipartAsync(File file) throws IOException {
        return chunkHybridMultipartAsync(file, false);
    }

    private <T> T postMultipart(String path,
                                Map<String, Object> fields,
                                File file,
                                Class<T> clazz) {
        String body = postMultipartRaw(path, fields, file);
        try {
            return apiClient.getObjectMapper().readValue(body, clazz);
        } catch (IOException e) {
            throw new DoclingClientException("Failed to parse response from " + path, e);
        }
    }

    private String postMultipartRaw(String path,
                                    Map<String, Object> fields,
                                    File file) {
        URI uri = URI.create(apiClient.getBaseUri() + path);

        return retryPolicy.execute(() -> {
            long start = System.nanoTime();
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("files", file, ContentType.APPLICATION_OCTET_STREAM, file.getName());
            if (fields != null) {
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    for (String value : expandFieldValues(entry.getValue())) {
                        builder.addTextBody(entry.getKey(), value);
                    }
                }
            }
            var entity = builder.build();
            byte[] body;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                entity.writeTo(baos);
                body = baos.toByteArray();
            } catch (IOException e) {
                throw new DoclingClientException("Failed to buffer multipart body", e);
            }
            log.debug("POST {} ({} bytes body)", path, body.length);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", entity.getContentType().getValue())
                    .header("Accept", "application/json")
                    .timeout(apiClient.getReadTimeout() != null ? apiClient.getReadTimeout() : DEFAULT_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            var interceptor = apiClient.getRequestInterceptor();
            if (interceptor != null) {
                interceptor.accept(req);
            }

            HttpRequest httpRequest = req.build();
            HttpResponse<String> resp;
            try {
                resp = apiClient.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new DoclingNetworkException("Failed to POST multipart request", "POST", uri, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DoclingClientException("Interrupted during POST " + path, e);
            }

            emitHttpTrace("POST", httpRequest.uri(), resp.statusCode(), durationMillis(start), "multipart",
                    resp.headers().map(), resp.headers().firstValue("Content-Type").orElse(null));

            log.info("POST {} -> {} ({} bytes response)", path, resp.statusCode(), resp.body() == null ? 0 : resp.body().length());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            }
            throw new DoclingHttpException(
                    "POST request failed",
                    resp.statusCode(),
                    "POST",
                    uri,
                    resp.body()
            );
        });
    }

    private <T> T postMultipartStreaming(String path,
                                         Map<String, Object> fields,
                                         InputStream fileStream,
                                         String filename,
                                         Class<T> clazz) {
        URI uri = URI.create(apiClient.getBaseUri() + path);

        // Note: Streaming operations cannot be retried as the stream may have been consumed.
        // We use RetryPolicy.noRetry() to maintain consistent exception handling.
        return RetryPolicy.noRetry().execute(() -> {
            long start = System.nanoTime();
            String boundary = "----docling-stream-" + UUID.randomUUID();

            List<InputStream> parts = new ArrayList<>();
            if (fields != null) {
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    for (String value : expandFieldValues(entry.getValue())) {
                        String fieldPart = "--" + boundary + "\r\n" +
                                "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n" +
                                value + "\r\n";
                        parts.add(new ByteArrayInputStream(fieldPart.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }

            String fileHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            parts.add(new ByteArrayInputStream(fileHeader.getBytes(StandardCharsets.UTF_8)));
            parts.add(fileStream);
            parts.add(new ByteArrayInputStream(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8)));

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(
                    () -> new SequenceInputStream(Collections.enumeration(parts)));

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .timeout(apiClient.getReadTimeout() != null ? apiClient.getReadTimeout() : DEFAULT_REQUEST_TIMEOUT)
                    .POST(bodyPublisher);

            var interceptor = apiClient.getRequestInterceptor();
            if (interceptor != null) {
                interceptor.accept(req);
            }

            HttpRequest httpRequest = req.build();
            HttpResponse<String> resp;
            try {
                resp = apiClient.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new DoclingNetworkException("Failed to POST streaming multipart request", "POST", uri, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DoclingClientException("Interrupted during streaming POST " + path, e);
            }

            emitHttpTrace("POST", httpRequest.uri(), resp.statusCode(), durationMillis(start), "streaming-multipart",
                    resp.headers().map(), resp.headers().firstValue("Content-Type").orElse(null));

            log.info("POST {} (streaming) -> {} ({} bytes response)", path, resp.statusCode(), resp.body() == null ? 0 : resp.body().length());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try {
                    return apiClient.getObjectMapper().readValue(resp.body(), clazz);
                } catch (IOException e) {
                    throw new DoclingClientException("Failed to parse response from " + path, e);
                }
            }
            throw new DoclingHttpException(
                    "Streaming POST request failed",
                    resp.statusCode(),
                    "POST",
                    uri,
                    resp.body()
            );
        });
    }

    private void rebuildInterceptors() {
        apiClient.setRequestInterceptor(builder -> {
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("X-Api-Key", apiKey);
            }
            String correlationId = currentCorrelationId();
            if (correlationId != null) {
                builder.header(CORRELATION_HEADER, correlationId);
            }
            if (traceHttp || metricsEnabled) {
                REQUEST_START.set(System.nanoTime());
            }
        });
        if (traceHttp || metricsEnabled) {
            apiClient.setResponseInterceptor(resp -> {
                try {
                    Long start = REQUEST_START.get();
                    long durationMs = start == null ? -1 : durationMillis(start);
                    emitHttpTrace(resp.request().method(),
                            resp.request().uri(),
                            resp.statusCode(),
                            durationMs,
                            "generated-api",
                            resp.headers().map(),
                            resp.headers().firstValue("Content-Type").orElse(null));
                } finally {
                    // Always clean up ThreadLocal to prevent memory leaks in thread pools
                    REQUEST_START.remove();
                }
            });
        } else {
            apiClient.setResponseInterceptor(null);
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("API key header enabled");
        }
    }

    private void emitHttpTrace(String method,
                               URI uri,
                               int status,
                               long durationMs,
                               String source,
                               Map<String, List<String>> headers,
                               String contentType) {
        if (traceHttp) {
            String correlationId = currentCorrelationId();
            log.info("[http-trace] method={} uri={} status={} durationMs={} source={} contentType={} correlationId={} headers={}",
                    method,
                    uri,
                    status,
                    durationMs,
                    source,
                    contentType,
                    correlationId,
                    headers);
        }
        if (metricsEnabled) {
            metricsCollector.record(method, uri, status, durationMs);
        }
    }

    private String logEndpointRequest(String endpoint, Supplier<String> detailSupplier) {
        String correlationId = nextCorrelationId(endpoint);
        pushCorrelation(correlationId);
        if (log.isDebugEnabled()) {
            log.debug("[endpoint-request] {} correlationId={} {}", endpoint, correlationId, safeDetails(detailSupplier));
        }
        return correlationId;
    }

    private void logEndpointResponse(String endpoint, String correlationId, Supplier<String> detailSupplier) {
        if (log.isDebugEnabled()) {
            log.debug("[endpoint-response] {} correlationId={} {}", endpoint, correlationId, safeDetails(detailSupplier));
        }
        popCorrelation(correlationId);
    }

    private String safeDetails(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Failed to build endpoint log details", e);
            return "details-error=" + e.getMessage();
        }
    }

    private void pushCorrelation(String id) {
        Deque<String> stack = CORRELATION_STACK.get();
        stack.push(id);
    }

    private void popCorrelation(String expectedId) {
        Deque<String> stack = CORRELATION_STACK.get();
        if (!stack.isEmpty()) {
            String removed = stack.pop();
            if (!Objects.equals(removed, expectedId) && log.isDebugEnabled()) {
                log.debug("Correlation stack mismatch expected={} removed={}", expectedId, removed);
            }
        }
        if (stack.isEmpty()) {
            CORRELATION_STACK.remove();
        }
    }

    private String currentCorrelationId() {
        Deque<String> stack = CORRELATION_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private String nextCorrelationId(String endpoint) {
        return endpoint + "-" + correlationSequence.incrementAndGet();
    }

    public List<OcrScenarioRunner.ScenarioRunResult> runOcrScenarioSweep(
            OcrScenarioRunner.OcrMatrixConfig config,
            List<OcrScenarioRunner.InputSource> sources,
            ConversionOutputType outputType,
            Path outputRoot) {
        List<OcrScenarioRunner.OcrScenario> scenarios = buildOcrScenarioMatrix(config);
        return runOcrScenarioSweep(scenarios, sources, outputType, outputRoot);
    }

    public List<OcrScenarioRunner.ScenarioRunResult> runOcrScenarioSweep(
            List<OcrScenarioRunner.OcrScenario> scenarios,
            List<OcrScenarioRunner.InputSource> sources,
            ConversionOutputType outputType,
            Path outputRoot) {
        Objects.requireNonNull(scenarios, "scenarios");
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("scenarios must contain at least one entry");
        }
        return runOcrScenarioSweepInternal(List.copyOf(scenarios), sources, outputType, outputRoot);
    }

    private List<OcrScenarioRunner.ScenarioRunResult> runOcrScenarioSweepInternal(
            List<OcrScenarioRunner.OcrScenario> scenarios,
            List<OcrScenarioRunner.InputSource> sources,
            ConversionOutputType outputType,
            Path outputRoot) {
        List<OcrScenarioRunner.InputSource> effectiveSources =
                List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (effectiveSources.isEmpty()) {
            throw new IllegalArgumentException("sources must contain at least one entry");
        }
        ConversionOutputType effectiveOutput =
                outputType != null ? outputType : ConversionOutputType.MARKDOWN;
        Path effectiveRoot = outputRoot != null
                ? outputRoot
                : Path.of("output", "benchmarks", "ocr-options");

        OcrScenarioRunner runner = OcrScenarioRunner.builder()
                .client(this)
                .outputRoot(effectiveRoot)
                .outputType(effectiveOutput)
                .sources(effectiveSources)
                .build();

        log.info("Running {} OCR scenarios across {} sources (outputType={} outputRoot={})",
                scenarios.size(), effectiveSources.size(), effectiveOutput, effectiveRoot);

        List<OcrScenarioRunner.ScenarioRunResult> aggregate = new ArrayList<>();
        for (OcrScenarioRunner.OcrScenario scenario : scenarios) {
            Instant start = Instant.now();
            log.info("Starting OCR scenario={} doOcr={} forceOcr={} engine={} pipeline={}",
                    scenario.name(),
                    scenario.doOcr(),
                    scenario.forceOcr(),
                    scenario.engine(),
                    scenario.pipeline());
            List<OcrScenarioRunner.ScenarioRunResult> scenarioResults = runner.runScenario(scenario);
            aggregate.addAll(scenarioResults);
            long duration = Duration.between(start, Instant.now()).toMillis();
            int successes = 0;
            for (OcrScenarioRunner.ScenarioRunResult result : scenarioResults) {
                if (result.status().isSuccess()) {
                    successes++;
                }
            }
            int failures = scenarioResults.size() - successes;
            log.info("Finished OCR scenario={} durationMs={} successes={} failures={}",
                    scenario.name(), duration, successes, failures);
        }

        return aggregate;
    }

    private String describeConvertEnvelope(ResponseProcessFileV1ConvertFilePost response) {
        if (response == null) {
            return "convertResponse=<null>";
        }
        return describeConvertActual(response.getActualInstance());
    }

    private String describeConvertEnvelope(ResponseProcessUrlV1ConvertSourcePost response) {
        if (response == null) {
            return "convertResponse=<null>";
        }
        return describeConvertActual(response.getActualInstance());
    }

    private String describeConvertActual(Object actual) {
        if (actual instanceof ConvertDocumentResponse convert) {
            ExportDocumentResponse doc = convert.getDocument();
            String filename = doc != null ? doc.getFilename() : "<unknown>";
            List<String> formats = new ArrayList<>();
            if (doc != null) {
                if (doc.getMdContent() != null) {
                    formats.add("md");
                }
                if (doc.getJsonContent() != null) {
                    formats.add("json");
                }
                if (doc.getHtmlContent() != null) {
                    formats.add("html");
                }
                if (doc.getTextContent() != null) {
                    formats.add("text");
                }
                if (doc.getDoctagsContent() != null) {
                    formats.add("doctags");
                }
            }
            int errorCount = convert.getErrors() != null ? convert.getErrors().size() : 0;
            return "convertResponse[type=inline, filename=" + filename +
                    ", status=" + convert.getStatus() +
                    ", processingTime=" + convert.getProcessingTime() +
                    ", formats=" + formats +
                    ", errors=" + errorCount + "]";
        }
        if (actual instanceof PresignedUrlConvertDocumentResponse presigned) {
            return "convertResponse[type=presigned, converted=" + presigned.getNumConverted() +
                    ", succeeded=" + presigned.getNumSucceeded() +
                    ", failed=" + presigned.getNumFailed() +
                    ", processingTime=" + presigned.getProcessingTime() + "]";
        }
        if (actual instanceof ChunkDocumentResponse chunkResponse) {
            return describeChunkResponse(chunkResponse);
        }
        return "convertResponse[type=" + (actual != null ? actual.getClass().getSimpleName() : "null") + "]";
    }

    private String describeTaskResult(ResponseTaskResultV1ResultTaskIdGet result) {
        if (result == null) {
            return "taskResult=<null>";
        }
        return describeConvertActual(result.getActualInstance());
    }

    public record TaskResultPayload(byte[] body, String contentType) {
        public TaskResultPayload(byte[] body, String contentType) {
            this.body = body != null ? body : new byte[0];
            this.contentType = contentType != null ? contentType : "";
        }

        public boolean isZip() {
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("zip")) {
                return true;
            }
            return body.length >= 4 && body[0] == 0x50 && body[1] == 0x4b && body[2] == 0x03 && body[3] == 0x04;
        }
    }

    private static class MetricsCollector {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private boolean enabled;

        MetricsCollector(boolean enabled) {
            this.enabled = enabled;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        void record(String method, URI uri, int status, long durationMs) {
            if (!enabled) {
                return;
            }
            count.incrementAndGet();
            if (durationMs >= 0) {
                totalDuration.addAndGet(durationMs);
            }
            long avg = count.get() == 0 ? 0 : totalDuration.get() / count.get();
            log.info("[metrics] method={} path={} status={} durationMs={} avgMs={} total={}",
                    method,
                    uri != null ? uri.getPath() : null,
                    status,
                    durationMs,
                    avg,
                    count.get());
        }
    }
}
