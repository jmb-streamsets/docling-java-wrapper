package com.docling.client;

import com.docling.model.ConvertDocumentsRequestOptions;
import com.docling.model.HybridChunkerOptions;
import com.docling.model.ResponseTaskResultV1ResultTaskIdGet;
import com.docling.model.TargetName;
import com.docling.model.TaskStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reusable async workflow helpers for Docling client consumers.
 * Provides detailed logging around async conversions + chunking with
 * consistent validation and artifact materialization.
 */
public final class AsyncWorkflowHelper {

    private static final Logger log = LogManager.getLogger(AsyncWorkflowHelper.class);

    private AsyncWorkflowHelper() {
    }

    public static List<TaskCompletion> runAsyncFileConversions(DoclingClient client,
                                                               File file,
                                                               Path destinationDir,
                                                               Collection<ConversionOutputType> outputTypes,
                                                               ConvertDocumentsRequestOptions options) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(destinationDir, "destinationDir");
        ensureFileExists(file);
        Files.createDirectories(destinationDir);
        List<ConversionOutputType> formats = normalizeOutputs(outputTypes);
        List<TaskCompletion> completions = new ArrayList<>();
        for (ConversionOutputType outputType : formats) {
            ConvertDocumentsRequestOptions effectiveOptions = options != null
                    ? cloneOptionsForFormat(client, options, outputType)
                    : null;
            TaskStatusResponse task;
            if (effectiveOptions != null) {
                task = client.convertStreamAsync(file, effectiveOptions);
            } else {
                task = client.convertMultipartAsync(file,
                        TargetName.INBODY,
                        outputType.getOutputFormat());
            }
            logTaskQueued("file-convert", task, file.getName(), outputType.getPrimaryToken());
            ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
            Path dest = destinationDir.resolve(safeName(file.getName()) + "-" +
                    outputType.getPrimaryToken() + outputType.getFileExtension());
            ConversionResults.writeFromTask(client,
                    task.getTaskId(),
                    dest,
                    outputType,
                    client.getApiClient().getObjectMapper());
            assertMaterialized(dest, outputType, "file async convert");
            logTaskCompletion("file async convert", task, result, dest);
            completions.add(new TaskCompletion("file-async-convert", task, result, dest));
        }
        return completions;
    }

    public static List<TaskCompletion> runAsyncUrlConversions(DoclingClient client,
                                                              Collection<String> urls,
                                                              Path destinationDir,
                                                              Collection<ConversionOutputType> outputTypes,
                                                              ConvertDocumentsRequestOptions options) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(destinationDir, "destinationDir");
        List<String> sources = normalizeUrls(urls);
        Files.createDirectories(destinationDir);
        List<ConversionOutputType> formats = normalizeOutputs(outputTypes);
        List<TaskCompletion> completions = new ArrayList<>();
        for (String url : sources) {
            for (ConversionOutputType outputType : formats) {
                ConvertDocumentsRequestOptions effectiveOptions = options != null
                        ? cloneOptionsForFormat(client, options, outputType)
                        : DoclingClient.defaultConvertOptions(outputType.getOutputFormat());
                TaskStatusResponse task = client.convertUrlAsync(url, effectiveOptions);
                logTaskQueued("url-convert", task, url, outputType.getPrimaryToken());
                ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
                Path dest = destinationDir.resolve("url-async-convert-" +
                        safeName(url) + "-" + outputType.getPrimaryToken() + outputType.getFileExtension());
                ConversionResults.writeFromTask(client,
                        task.getTaskId(),
                        dest,
                        outputType,
                        client.getApiClient().getObjectMapper());
                assertMaterialized(dest, outputType, "url async convert (" + url + ")");
                logTaskCompletion("url async convert (" + url + ")", task, result, dest);
                completions.add(new TaskCompletion("url-async-convert", task, result, dest));
            }
        }
        return completions;
    }

    public static List<TaskCompletion> runAsyncFileChunking(DoclingClient client,
                                                            File file,
                                                            Path destinationDir,
                                                            ConvertDocumentsRequestOptions convertOptions,
                                                            HybridChunkerOptions chunkOptions,
                                                            boolean includeConvertedDoc,
                                                            TargetName targetName) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(destinationDir, "destinationDir");
        ensureFileExists(file);
        Files.createDirectories(destinationDir);
        TargetName resolvedTarget = targetName != null ? targetName : TargetName.INBODY;
        HybridChunkerOptions effectiveChunk = chunkOptions;
        ConvertDocumentsRequestOptions effectiveConvert = convertOptions != null
                ? cloneOptions(client, convertOptions)
                : null;
        boolean requiresStream = effectiveConvert != null
                || effectiveChunk != null
                || resolvedTarget != TargetName.INBODY;
        TaskStatusResponse task;
        if (requiresStream) {
            task = client.chunkHybridStreamAsync(file,
                    includeConvertedDoc,
                    resolvedTarget,
                    effectiveConvert,
                    effectiveChunk);
        } else {
            task = client.chunkHybridFilesAsync(file, includeConvertedDoc, resolvedTarget);
        }
        logTaskQueued("file-chunk", task, file.getName(), includeConvertedDoc ? "withDoc" : "noDoc");
        ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
        JsonNode chunk = extractChunkPayload(client, task.getTaskId(), result);
        validateChunkPayload(chunk, "file-chunk-async");
        Path dest = destinationDir.resolve("file-chunk-async.json");
        client.getApiClient().getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), chunk);
        logTaskCompletion("file async chunk", task, result, dest);
        return List.of(new TaskCompletion("file-async-chunk", task, result, dest));
    }

    public static List<TaskCompletion> runAsyncUrlChunking(DoclingClient client,
                                                           Collection<String> urls,
                                                           Path destinationDir,
                                                           ConvertDocumentsRequestOptions convertOptions,
                                                           HybridChunkerOptions chunkOptions,
                                                           boolean includeConvertedDoc) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(destinationDir, "destinationDir");
        List<String> sources = normalizeUrls(urls);
        Files.createDirectories(destinationDir);
        HybridChunkerOptions options = chunkOptions != null ? chunkOptions : DoclingClient.defaultChunkOptions();
        List<TaskCompletion> completions = new ArrayList<>();
        for (String url : sources) {
            ConvertDocumentsRequestOptions perUrlConvert = convertOptions != null ? cloneOptions(client, convertOptions) : null;
            TaskStatusResponse task = client.chunkHybridSourcesAsync(List.of(url), options, includeConvertedDoc, perUrlConvert);
            logTaskQueued("url-chunk", task, url, includeConvertedDoc ? "withDoc" : "noDoc");
            ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
            String label = "url-chunk-async-" + safeName(url);
            JsonNode chunk = extractChunkPayload(client, task.getTaskId(), result);
            validateChunkPayload(chunk, label);
            Path dest = destinationDir.resolve(label + ".json");
            client.getApiClient().getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), chunk);
            logTaskCompletion(label, task, result, dest);
            completions.add(new TaskCompletion("url-async-chunk", task, result, dest));
        }
        return completions;
    }

    public static List<TaskCompletion> runStreamFileConversions(DoclingClient client,
                                                                File file,
                                                                Path destinationDir,
                                                                Collection<ConversionOutputType> outputTypes,
                                                                ConvertDocumentsRequestOptions options) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(destinationDir, "destinationDir");
        ensureFileExists(file);
        Files.createDirectories(destinationDir);
        List<ConversionOutputType> formats = normalizeOutputs(outputTypes);
        List<TaskCompletion> completions = new ArrayList<>();
        for (ConversionOutputType outputType : formats) {
            ConvertDocumentsRequestOptions effectiveOptions = options != null
                    ? cloneOptionsForFormat(client, options, outputType)
                    : DoclingClient.defaultConvertOptions(outputType.getOutputFormat());
            TaskStatusResponse task = client.convertStreamAsync(file, effectiveOptions);
            logTaskQueued("stream-file-convert", task, file.getName(), outputType.getPrimaryToken());
            ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
            Path dest = destinationDir.resolve("stream-" + safeName(file.getName()) + "-" +
                    outputType.getPrimaryToken() + outputType.getFileExtension());
            ConversionResults.writeFromTask(client,
                    task.getTaskId(),
                    dest,
                    outputType,
                    client.getApiClient().getObjectMapper());
            assertMaterialized(dest, outputType, "stream file convert");
            logTaskCompletion("stream file convert", task, result, dest);
            completions.add(new TaskCompletion("stream-file-convert", task, result, dest));
        }
        return completions;
    }

    public static List<TaskCompletion> runStreamUrlConversions(DoclingClient client,
                                                               Collection<String> urls,
                                                               Path destinationDir,
                                                               Collection<ConversionOutputType> outputTypes,
                                                               ConvertDocumentsRequestOptions options) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(destinationDir, "destinationDir");
        List<String> sources = normalizeUrls(urls);
        Files.createDirectories(destinationDir);
        List<ConversionOutputType> formats = normalizeOutputs(outputTypes);
        List<TaskCompletion> completions = new ArrayList<>();
        for (String url : sources) {
            byte[] payload = downloadUrlBytes(url);
            for (ConversionOutputType outputType : formats) {
                ConvertDocumentsRequestOptions effectiveOptions = options != null
                        ? cloneOptionsForFormat(client, options, outputType)
                        : DoclingClient.defaultConvertOptions(outputType.getOutputFormat());
                TaskStatusResponse task;
                try (InputStream stream = new ByteArrayInputStream(payload)) {
                    task = client.convertStreamAsync(stream, guessFilenameFromUrl(url), effectiveOptions);
                }
                logTaskQueued("stream-url-convert", task, url, outputType.getPrimaryToken());
                ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
                Path dest = destinationDir.resolve("stream-url-convert-" +
                        safeName(url) + "-" + outputType.getPrimaryToken() + outputType.getFileExtension());
                ConversionResults.writeFromTask(client,
                        task.getTaskId(),
                        dest,
                        outputType,
                        client.getApiClient().getObjectMapper());
                assertMaterialized(dest, outputType, "stream url convert (" + url + ")");
                logTaskCompletion("stream url convert (" + url + ")", task, result, dest);
                completions.add(new TaskCompletion("stream-url-convert", task, result, dest));
            }
        }
        return completions;
    }

    public static List<TaskCompletion> runStreamFileChunking(DoclingClient client,
                                                             File file,
                                                             Path destinationDir,
                                                             ConvertDocumentsRequestOptions convertOptions,
                                                             HybridChunkerOptions chunkOptions,
                                                             boolean includeConvertedDoc,
                                                             TargetName targetName) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(destinationDir, "destinationDir");
        ensureFileExists(file);
        Files.createDirectories(destinationDir);
        TargetName resolvedTarget = targetName != null ? targetName : TargetName.INBODY;
        ConvertDocumentsRequestOptions effectiveConvert = convertOptions != null
                ? cloneOptions(client, convertOptions)
                : null;
        HybridChunkerOptions effectiveChunk = chunkOptions != null ? chunkOptions : DoclingClient.defaultChunkOptions();
        TaskStatusResponse task = client.chunkHybridStreamAsync(file,
                includeConvertedDoc,
                resolvedTarget,
                effectiveConvert,
                effectiveChunk);
        logTaskQueued("stream-file-chunk", task, file.getName(), includeConvertedDoc ? "withDoc" : "noDoc");
        ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
        JsonNode chunk = extractChunkPayload(client, task.getTaskId(), result);
        String label = "stream-file-chunk" + (includeConvertedDoc ? "-with-doc" : "");
        Path dest = destinationDir.resolve(label + ".json");
        client.getApiClient().getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), chunk);
        logTaskCompletion("stream file chunk", task, result, dest);
        return List.of(new TaskCompletion("stream-file-chunk", task, result, dest));
    }

    public static List<TaskCompletion> runStreamUrlChunking(DoclingClient client,
                                                            Collection<String> urls,
                                                            Path destinationDir,
                                                            ConvertDocumentsRequestOptions convertOptions,
                                                            HybridChunkerOptions chunkOptions,
                                                            boolean includeConvertedDoc) throws IOException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(destinationDir, "destinationDir");
        List<String> sources = normalizeUrls(urls);
        Files.createDirectories(destinationDir);
        HybridChunkerOptions effectiveChunk = chunkOptions != null ? chunkOptions : DoclingClient.defaultChunkOptions();
        List<TaskCompletion> completions = new ArrayList<>();
        for (String url : sources) {
            ConvertDocumentsRequestOptions perUrlConvert = convertOptions != null ? cloneOptions(client, convertOptions) : null;
            TaskStatusResponse task;
            try (InputStream stream = openUrlStream(url)) {
                task = client.chunkHybridStreamAsync(stream,
                        guessFilenameFromUrl(url),
                        includeConvertedDoc,
                        TargetName.INBODY,
                        perUrlConvert,
                        effectiveChunk);
            }
            logTaskQueued("stream-url-chunk", task, url, includeConvertedDoc ? "withDoc" : "noDoc");
            ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
            String label = "stream-url-chunk-" + safeName(url);
            JsonNode chunk = extractChunkPayload(client, task.getTaskId(), result);
            Path dest = destinationDir.resolve(label + ".json");
            client.getApiClient().getObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(dest.toFile(), chunk);
            logTaskCompletion("stream url chunk (" + url + ")", task, result, dest);
            completions.add(new TaskCompletion("stream-url-chunk", task, result, dest));
        }
        return completions;
    }

    private static void ensureFileExists(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
    }

    private static List<ConversionOutputType> normalizeOutputs(Collection<ConversionOutputType> outputTypes) {
        if (outputTypes == null || outputTypes.isEmpty()) {
            return List.of(ConversionOutputType.MARKDOWN, ConversionOutputType.DOCLING_JSON);
        }
        return new ArrayList<>(new LinkedHashSet<>(outputTypes));
    }

    private static List<String> normalizeUrls(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("At least one URL must be provided");
        }
        List<String> normalized = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            normalized.add(url.trim());
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("No valid URLs provided");
        }
        return normalized;
    }

    private static ConvertDocumentsRequestOptions cloneOptions(DoclingClient client,
                                                               ConvertDocumentsRequestOptions options) throws IOException {
        if (options == null) {
            return null;
        }
        var mapper = client.getApiClient().getObjectMapper();
        byte[] serialized = mapper.writeValueAsBytes(options);
        return mapper.readValue(serialized, ConvertDocumentsRequestOptions.class);
    }

    private static ConvertDocumentsRequestOptions cloneOptionsForFormat(DoclingClient client,
                                                                        ConvertDocumentsRequestOptions options,
                                                                        ConversionOutputType outputType) throws IOException {
        if (options == null) {
            return null;
        }
        ConvertDocumentsRequestOptions clone = cloneOptions(client, options);
        if (clone == null) {
            return null;
        }
        clone.toFormats(List.of(outputType.getOutputFormat()));
        return clone;
    }

    private static JsonNode extractChunkPayload(DoclingClient client,
                                                String taskId,
                                                ResponseTaskResultV1ResultTaskIdGet result) throws IOException {
        var mapper = client.getApiClient().getObjectMapper();
        if (result != null && result.getActualInstance() != null) {
            try {
                JsonNode candidate = mapper.valueToTree(result.getActualInstance());
                if (hasChunkFields(candidate)) {
                    return candidate;
                }
            } catch (IllegalArgumentException e) {
                // Result instance couldn't be converted to JSON tree, fall through to download
                log.debug("Failed to convert result instance to JSON tree for task {}: {}", taskId, e.getMessage());
            }
        }
        DoclingClient.TaskResultPayload payload = client.downloadTaskResultPayload(taskId);
        if (!payload.isZip()) {
            JsonNode node = mapper.readTree(new ByteArrayInputStream(payload.body()));
            if (hasChunkFields(node)) {
                return node;
            }
        } else {
            JsonNode fromZip = findChunkJsonEntry(payload.body(), mapper);
            if (hasChunkFields(fromZip)) {
                return fromZip;
            }
        }
        throw new IllegalStateException("Task " + taskId + " did not include chunk JSON payload");
    }

    private static JsonNode findChunkJsonEntry(byte[] zipBytes,
                                               com.fasterxml.jackson.databind.ObjectMapper mapper) throws IOException {
        if (zipBytes == null) {
            return null;
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                byte[] data = zis.readAllBytes();
                try {
                    JsonNode candidate = mapper.readTree(new ByteArrayInputStream(data));
                    if (hasChunkFields(candidate)) {
                        return candidate;
                    }
                } catch (Exception e) {
                    // Entry is not valid JSON or doesn't have chunk fields, keep scanning
                    log.trace("ZIP entry {} is not valid chunk JSON: {}", entry.getName(), e.getMessage());
                } finally {
                    zis.closeEntry();
                }
            }
        }
        return null;
    }

    private static boolean hasChunkFields(JsonNode node) {
        if (node == null) {
            return false;
        }
        JsonNode chunks = node.path("chunks");
        return chunks.isArray() && chunks.size() > 0;
    }

    private static void validateChunkPayload(JsonNode chunk, String context) {
        if (!hasChunkFields(chunk)) {
            throw new IllegalStateException("Chunk response for " + context + " missing chunks");
        }
        log.info("Validated chunk payload for {} (chunks={})", context, chunk.path("chunks").size());
    }

    private static void assertMaterialized(Path file,
                                           ConversionOutputType expectedType,
                                           String context) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalStateException("Expected " + expectedType.getPrimaryToken() +
                    " artifact for " + context + " but file was not created: " + file);
        }
        if (!file.getFileName().toString().endsWith(expectedType.getFileExtension())) {
            throw new IllegalStateException("File " + file + " does not end with " +
                    expectedType.getFileExtension());
        }
        if (Files.size(file) == 0) {
            throw new IllegalStateException("File " + file + " is empty; cannot validate " +
                    expectedType.getPrimaryToken() + " content");
        }
        log.info("Validated {} output {} for {}", expectedType.getPrimaryToken(), file.getFileName(), context);
    }

    private static void logTaskQueued(String context,
                                      TaskStatusResponse task,
                                      String source,
                                      String variant) {
        if (task == null) {
            return;
        }
        log.info("Queued {} task id={} source={} variant={} status={}",
                context,
                task.getTaskId(),
                source,
                variant,
                task.getTaskStatus());
    }

    private static void logTaskCompletion(String context,
                                          TaskStatusResponse task,
                                          ResponseTaskResultV1ResultTaskIdGet result,
                                          Path artifact) {
        String payloadType = result != null && result.getActualInstance() != null
                ? result.getActualInstance().getClass().getSimpleName()
                : "unknown";
        log.info("Task {} completed id={} status={} payloadType={} artifact={}",
                context,
                task != null ? task.getTaskId() : "unknown",
                task != null ? task.getTaskStatus() : "unknown",
                payloadType,
                artifact != null ? artifact.toAbsolutePath() : "<none>");
    }

    private static byte[] downloadUrlBytes(String url) throws IOException {
        try (InputStream stream = openUrlStream(url)) {
            return stream.readAllBytes();
        }
    }

    private static InputStream openUrlStream(String url) throws IOException {
        URI uri = URI.create(url);
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "DoclingAsyncWorkflowHelper/1.0");
        return new BufferedInputStream(connection.getInputStream());
    }

    private static String guessFilenameFromUrl(String url) {
        String path = URI.create(url).getPath();
        if (path == null || path.isBlank()) {
            return safeName(url) + ".bin";
        }
        int idx = path.lastIndexOf('/');
        if (idx >= 0 && idx < path.length() - 1) {
            return path.substring(idx + 1);
        }
        return safeName(url) + ".bin";
    }

    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unnamed";
        }
        return raw.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
    }

    public record TaskCompletion(String context,
                                 TaskStatusResponse task,
                                 ResponseTaskResultV1ResultTaskIdGet result,
                                 Path artifactPath) {
    }
}
