package com.docling.client;

import com.docling.model.ChunkDocumentResponse;
import com.docling.model.HybridChunkerOptions;
import com.docling.model.ResponseProcessFileV1ConvertFilePost;
import com.docling.model.ResponseProcessUrlV1ConvertSourcePost;
import com.docling.model.TargetName;
import com.docling.model.TaskStatusResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * End-to-end synchronous benchmark covering every DoclingClient sync entry point.
 * Demonstrates file conversion, URL conversion, and chunking (file + URL) while
 * validating every supported output format.
 */
public final class UsageSync {

    private static final File SAMPLE_FILE = new File("documents/2512.01970v1.pdf");
    private static final List<String> SAMPLE_URLS = List.of(
            "https://arxiv.org/pdf/2501.17887"
    );
    private static final Path OUTPUT_ROOT = Path.of("output", "benchmarks", "sync");
    private static final Path CONVERT_DIR = OUTPUT_ROOT.resolve("conversions");
    private static final Path CHUNK_DIR = OUTPUT_ROOT.resolve("chunks");

    public static void main(String[] args) throws Exception {
        DoclingClient client = DoclingClient.fromEnv();
        Files.createDirectories(CONVERT_DIR);
        Files.createDirectories(CHUNK_DIR);
        ensureSampleFile();

        benchmark("sync-convert-file", () -> {
            runSyncFileConversions(client);
            return null;
        });

        benchmark("sync-convert-urls", () -> {
            runSyncUrlConversions(client);
            return null;
        });

        benchmark("sync-chunk-file", () -> {
            runSyncFileChunking(client);
            return null;
        });

        benchmark("sync-chunk-urls", () -> {
            runSyncUrlChunking(client);
            return null;
        });

        System.out.println("UsageSync benchmarks complete.");
    }

    private static void runSyncFileConversions(DoclingClient client) throws IOException {
        var mapper = client.getApiClient().getObjectMapper();
        for (ConversionOutputType outputType : ConversionOutputType.values()) {
            Path dest = CONVERT_DIR.resolve("file-convert-" + outputType.getPrimaryToken() + outputType.getFileExtension());
            try {
                ResponseProcessFileV1ConvertFilePost response =
                        client.convertFile(SAMPLE_FILE, TargetName.INBODY, outputType.getOutputFormat());
                ConversionResults.write(response, dest, outputType, mapper);
            } catch (ConversionMaterializationException e) {
                System.out.println("Sync file convert fell back to async: " + e.getMessage());
                TaskStatusResponse task = client.convertFileAsync(SAMPLE_FILE, TargetName.INBODY, outputType.getOutputFormat());
                client.waitForTaskResult(task.getTaskId());
                ConversionResults.writeFromTask(client, task.getTaskId(), dest, outputType, mapper);
            }
            assertMaterialized(dest, outputType, "file sync convert");
        }
    }

    private static void runSyncUrlConversions(DoclingClient client) throws IOException {
        var mapper = client.getApiClient().getObjectMapper();
        for (String url : SAMPLE_URLS) {
            for (ConversionOutputType outputType : ConversionOutputType.values()) {
                Path dest = CONVERT_DIR.resolve("url-convert-" + safeName(url) + "-" +
                        outputType.getPrimaryToken() + outputType.getFileExtension());
                try {
                    ResponseProcessUrlV1ConvertSourcePost response =
                            client.convertUrl(url, DoclingClient.defaultConvertOptions(outputType.getOutputFormat()));
                    ConversionResults.write(response, dest, outputType, mapper);
                } catch (ConversionMaterializationException e) {
                    System.out.println("Sync url convert fell back to async for " + url + ": " + e.getMessage());
                    TaskStatusResponse task =
                            client.convertUrlAsync(url, DoclingClient.defaultConvertOptions(outputType.getOutputFormat()));
                    client.waitForTaskResult(task.getTaskId());
                    ConversionResults.writeFromTask(client, task.getTaskId(), dest, outputType, mapper);
                }
                assertMaterialized(dest, outputType, "url sync convert (" + url + ")");
            }
        }
    }

    private static void runSyncFileChunking(DoclingClient client) throws IOException {
        ChunkDocumentResponse chunk = client.chunkHybridFile(SAMPLE_FILE);
        validateChunkPayload(chunk, "file chunk sync");
        writeChunkSnapshot(client, chunk, "file-chunk-sync");
    }

    private static void runSyncUrlChunking(DoclingClient client) throws IOException {
        HybridChunkerOptions chunkOptions = DoclingClient.defaultChunkOptions();
        for (String url : SAMPLE_URLS) {
            ChunkDocumentResponse chunk = client.chunkHybridSources(List.of(url), chunkOptions, false);
            String label = "url-chunk-sync-" + safeName(url);
            validateChunkPayload(chunk, label);
            writeChunkSnapshot(client, chunk, label);
        }
    }

    private static void writeChunkSnapshot(DoclingClient client,
                                           ChunkDocumentResponse chunk,
                                           String label) throws IOException {
        var mapper = client.getApiClient().getObjectMapper();
        Path chunkJson = CHUNK_DIR.resolve(label + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(chunkJson.toFile(), chunk);
        System.out.println("Saved chunk snapshot -> " + chunkJson);
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
        System.out.printf("Validated %s output (%s) for %s%n",
                expectedType.getPrimaryToken(), file.getFileName(), context);
    }

    private static void validateChunkPayload(ChunkDocumentResponse chunk, String context) {
        if (chunk == null || chunk.getChunks() == null || chunk.getChunks().isEmpty()) {
            throw new IllegalStateException("Chunk response for " + context + " did not include any chunks");
        }
        System.out.printf("Validated chunk payload (%d chunks) for %s%n",
                chunk.getChunks().size(), context);
    }

    private static void ensureSampleFile() throws IOException {
        if (!SAMPLE_FILE.exists()) {
            throw new IOException("Sample file not found: " + SAMPLE_FILE);
        }
    }

    private static <T> void benchmark(String label, Callable<T> action) throws Exception {
        Instant start = Instant.now();
        try {
            System.out.println("Starting " + label + "...");
            action.call();
        } finally {
            long millis = Duration.between(start, Instant.now()).toMillis();
            System.out.println(label + " finished in " + millis + " ms");
        }
    }

    private static String safeName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
    }

    private UsageSync() {
        // no-op
    }
}
