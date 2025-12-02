package com.docling.client;

import com.docling.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Minimal CLI that uses BufferedDoclingClient to upload a single file via streaming multipart.
 * Usage:
 * ./gradlew run -PmainClass=com.docling.client.BufferedCli --args="<path-to-file> [sync|async|chunk] [poll]"
 */
public final class BufferedCli {

    private static final Logger log = LogManager.getLogger(BufferedCli.class);

    private BufferedCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: <filePath> [sync|async|chunk]");
            System.exit(1);
        }

        String mode = args.length > 1 ? args[1].toLowerCase() : "async";
        boolean doPoll = (args.length > 2 && args[2].equalsIgnoreCase("poll")) ||
                "true".equalsIgnoreCase(System.getenv().getOrDefault("DOCLING_POLL", "false"));
        File file = new File(args[0]);
        if (!file.exists() || !file.isFile()) {
            System.err.println("File not found: " + file);
            System.exit(1);
        }

        ConversionOutputType outputType;
        try {
            outputType = ConversionOutputType.fromNullable(System.getenv("DOCLING_OUTPUT_TYPE"));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        log.info("Materializing convert outputs as {}", outputType.getPrimaryToken());

        BufferedDoclingClient client = BufferedDoclingClient.fromEnv();

        log.info("Uploading {} via {} mode", file.getName(), mode);

        switch (mode) {
            case "sync" -> {
                var resp = client.convert(file, TargetName.INBODY, outputType.getOutputFormat());
                var actual = resp.getActualInstance();
                var status = actual instanceof ConvertDocumentResponse
                        ? ((ConvertDocumentResponse) actual).getStatus()
                        : null;
                System.out.println("status=" + (status != null ? status : "n/a"));
            }
            case "chunk" -> {
                var task = client.chunkHybridAsync(file, true);
                System.out.println("taskId=" + task.getTaskId() + " status=" + task.getTaskStatus());
                if (doPoll) {
                    pollAndPersist(client.delegate(), task.getTaskId(), file.getName(), "chunk", outputType);
                }
            }
            case "async" -> {
                var task = client.convertAsync(file, TargetName.INBODY, outputType.getOutputFormat());
                System.out.println("taskId=" + task.getTaskId() + " status=" + task.getTaskStatus());
                if (doPoll) {
                    pollAndPersist(client.delegate(), task.getTaskId(), file.getName(), "convert", outputType);
                }
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void pollAndPersist(DoclingClient client,
                                       String taskId,
                                       String filename,
                                       String kind,
                                       ConversionOutputType outputType) throws Exception {
        long maxSeconds = Long.parseLong(System.getenv().getOrDefault("POLL_MAX_SECONDS", "900"));
        long waitSeconds = Long.parseLong(System.getenv().getOrDefault("POLL_WAIT_SECONDS", "5"));
        var mapper = client.getApiClient().getObjectMapper();
        ResponseTaskResultV1ResultTaskIdGet result = waitFor(client, taskId, maxSeconds, waitSeconds);
        if (result == null) {
            System.out.println("Task " + taskId + " not finished within " + maxSeconds + "s");
            return;
        }
        File outDir = new File("build/docling-results");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outDir);
        }
        if (kind != null && kind.startsWith("chunk")) {
            File out = new File(outDir, filename + "." + kind + ".result.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(out, result);
            System.out.println("Saved result -> " + out);
            if (!materializeChunkConvertedDoc(result, outDir.toPath(), filename, kind, outputType, mapper)) {
                log.info("Chunk result {} did not include converted document payload", taskId);
            }
        } else {
            Path outPath = outDir.toPath().resolve(filename + "." + kind + ".result" + outputType.getFileExtension());
            try {
                ConversionResults.write(result, outPath, outputType, mapper);
                System.out.println("Saved result -> " + outPath);
            } catch (ConversionMaterializationException e) {
                log.warn("Materialization failed: {}", e.getMessage());
                try {
                    DoclingClient.TaskResultPayload payload = client.downloadTaskResultPayload(taskId);
                    if (payload.isZip()) {
                        Path zipPath = outDir.toPath().resolve(filename + "." + kind + ".result.zip");
                        Files.createDirectories(zipPath.getParent());
                        Files.write(zipPath, payload.body());
                        if (outputType.writeFromZip(payload.body(), outPath)) {
                            System.out.println("Extracted result -> " + outPath);
                        } else {
                            System.out.println("Zip saved -> " + zipPath + " (no " + outputType.getPrimaryToken() + " entry)");
                        }
                    } else {
                        try {
                            ConvertDocumentResponse convert = mapper.readValue(payload.body(), ConvertDocumentResponse.class);
                            ConversionResults.write(convert, outPath, outputType, mapper);
                            System.out.println("Saved result -> " + outPath);
                        } catch (Exception inner) {
                            Path raw = outDir.toPath().resolve(filename + "." + kind + ".result.raw.json");
                            Files.createDirectories(raw.getParent());
                            Files.write(raw, payload.body());
                            System.out.println("Saved raw JSON result -> " + raw);
                        }
                    }
                } catch (DoclingNetworkException netEx) {
                    System.err.println("Network error downloading task result: " + netEx.getMessage());
                    log.error("Network failure for task {}", taskId, netEx);
                } catch (DoclingHttpException httpEx) {
                    System.err.println("HTTP error downloading task result: " + httpEx.getMessage());
                    log.error("HTTP error for task {}: status={}", taskId, httpEx.getStatusCode(), httpEx);
                } catch (DoclingClientException docEx) {
                    System.err.println("Docling error: " + docEx.getMessage());
                    log.error("Docling client error for task {}", taskId, docEx);
                } catch (Exception ex) {
                    System.err.println("Unexpected error: " + ex.getMessage());
                    log.error("Fallback download failed for task {}", taskId, ex);
                }
            } catch (DoclingTaskFailureException taskEx) {
                System.err.println("Task failed: " + taskEx.getMessage());
                log.error("Task {} failed", taskId, taskEx);
            } catch (DoclingTimeoutException timeoutEx) {
                System.err.println("Task timed out: " + timeoutEx.getMessage());
                log.error("Task {} timed out after {}s", taskId, timeoutEx.getTimeoutSeconds());
            } catch (DoclingClientException docEx) {
                System.err.println("Docling error: " + docEx.getMessage());
                log.error("Docling client error for task {}", taskId, docEx);
            }
        }
    }

    private static boolean materializeChunkConvertedDoc(ResponseTaskResultV1ResultTaskIdGet result,
                                                        Path outputDir,
                                                        String filename,
                                                        String kind,
                                                        ConversionOutputType outputType,
                                                        com.fasterxml.jackson.databind.ObjectMapper mapper) {
        Object actual = result.getActualInstance();
        if (!(actual instanceof ChunkDocumentResponse chunk)) {
            return false;
        }
        if (chunk.getDocuments() == null || chunk.getDocuments().isEmpty()) {
            return false;
        }
        for (ExportResult exportResult : chunk.getDocuments()) {
            if (exportResult == null || exportResult.getContent() == null) {
                continue;
            }
            try {
                ConvertDocumentResponse convert = new ConvertDocumentResponse()
                        .document(exportResult.getContent())
                        .status(exportResult.getStatus())
                        .processingTime(chunk.getProcessingTime());
                convert.setErrors(exportResult.getErrors());
                convert.setTimings(exportResult.getTimings());
                Path outPath = outputDir.resolve(filename + "." + kind + ".result" + outputType.getFileExtension());
                ConversionResults.write(convert, outPath, outputType, mapper);
                System.out.println("Saved chunk converted output -> " + outPath);
                return true;
            } catch (Exception e) {
                log.warn("Failed to materialize chunk converted output", e);
            }
        }
        return false;
    }

    private static ResponseTaskResultV1ResultTaskIdGet waitFor(DoclingClient client,
                                                               String taskId,
                                                               long maxSeconds,
                                                               long waitSeconds) throws InterruptedException {
        long elapsed = 0;
        while (elapsed < maxSeconds) {
            TaskStatusResponse status = client.pollStatus(taskId, Duration.ofSeconds(waitSeconds));
            String state = status != null ? status.getTaskStatus() : null;
            if (DoclingClient.isSuccessStatus(state)) {
                return client.fetchResult(taskId);
            }
            if (DoclingClient.isFailureStatus(state)) {
                throw new DoclingTaskFailureException(
                        "Task failed",
                        taskId,
                        state,
                        status.getTaskMeta()
                );
            }
            log.info("Task {} still {} after {}s (timeout {}s)", taskId, state != null ? state : "pending", elapsed, maxSeconds);
            Thread.sleep(waitSeconds * 1000);
            elapsed += waitSeconds;
        }
        return null;
    }
}
