package com.docling.client;

import com.docling.model.TargetName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Async streaming benchmark companion to UsageAsync. Delegates to
 * AsyncWorkflowHelper stream helpers to exercise convert + chunk flows that rely
 * on DoclingClient's InputStream-centric APIs.
 */
public final class UsageAsyncStreaming {

    private static final File SAMPLE_FILE = new File("documents/2512.01970v1.pdf");

    private static final List<String> SAMPLE_URLS = List.of(
            "https://arxiv.org/pdf/2501.17887",
            "https://arxiv.org/pdf/2511.15709",
            "https://arxiv.org/pdf/2511.15706"
    );
    private static final Path OUTPUT_ROOT = Path.of("output", "benchmarks", "async-streaming");
    private static final Path CONVERT_DIR = OUTPUT_ROOT.resolve("conversions");
    private static final Path CHUNK_DIR = OUTPUT_ROOT.resolve("chunks");

    public static void main(String[] args) throws Exception {
        DoclingClient client = DoclingClient.fromEnv();
        Files.createDirectories(CONVERT_DIR);
        Files.createDirectories(CHUNK_DIR);
        ensureSampleFile();

        benchmark("async-stream-convert-file", () -> {
            AsyncWorkflowHelper.runStreamFileConversions(
                    client,
                    SAMPLE_FILE,
                    CONVERT_DIR,
                    Arrays.asList(ConversionOutputType.values()),
                    null
            );
            return null;
        });

        benchmark("async-stream-convert-urls", () -> {
            AsyncWorkflowHelper.runStreamUrlConversions(
                    client,
                    SAMPLE_URLS,
                    CONVERT_DIR,
                    Arrays.asList(ConversionOutputType.values()),
                    null
            );
            return null;
        });

        benchmark("async-stream-chunk-file", () -> {
            AsyncWorkflowHelper.runStreamFileChunking(
                    client,
                    SAMPLE_FILE,
                    CHUNK_DIR,
                    null,
                    DoclingClient.defaultChunkOptions(),
                    false,
                    TargetName.INBODY
            );
            return null;
        });

        benchmark("async-stream-chunk-file-targets", () -> {
            runChunkTargetsBenchmark(client);
            return null;
        });

        benchmark("async-stream-chunk-urls", () -> {
            AsyncWorkflowHelper.runStreamUrlChunking(
                    client,
                    SAMPLE_URLS,
                    CHUNK_DIR,
                    null,
                    DoclingClient.defaultChunkOptions(),
                    false
            );
            return null;
        });

        System.out.println("UsageAsyncStreaming benchmarks complete.");
    }

    private static void runChunkTargetsBenchmark(DoclingClient client) throws IOException {
        for (TargetName target : TargetName.values()) {
            Path targetDir = CHUNK_DIR.resolve("target-" + target.name().toLowerCase());
            Files.createDirectories(targetDir);
            boolean includeConvertedDoc = target != TargetName.INBODY;
            try {
                AsyncWorkflowHelper.runStreamFileChunking(
                        client,
                        SAMPLE_FILE,
                        targetDir,
                        null,
                        DoclingClient.defaultChunkOptions(),
                        includeConvertedDoc,
                        target
                );
            } catch (DoclingTaskFailureException e) {
                if (target == TargetName.ZIP) {
                    System.err.printf("Skipping ZIP chunk target – service currently cannot persist zipped chunk files (%s)%n",
                            e.getMessage());
                } else {
                    throw e;
                }
            }
        }
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

    private UsageAsyncStreaming() {
        // no-op
    }
}
