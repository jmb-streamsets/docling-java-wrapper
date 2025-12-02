package com.docling.client;

import com.docling.model.TargetName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.time.Duration;
import java.time.Instant;

/**
 * Async benchmark companion to UsageSync. Exercises every async-facing
 * DoclingClient entry point (convert + chunk) with validation that each
 * supported output format is materialized.
 */
public final class UsageAsync {

    private static final File SAMPLE_FILE = new File("documents/2512.01970v1.pdf");
    private static final List<String> SAMPLE_URLS = List.of(
            "https://arxiv.org/pdf/2501.17887"
    );
    private static final Path OUTPUT_ROOT = Path.of("output", "benchmarks", "async");
    private static final Path CONVERT_DIR = OUTPUT_ROOT.resolve("conversions");
    private static final Path CHUNK_DIR = OUTPUT_ROOT.resolve("chunks");

    public static void main(String[] args) throws Exception {
        DoclingClient client = DoclingClient.fromEnv();
        Files.createDirectories(CONVERT_DIR);
        Files.createDirectories(CHUNK_DIR);
        ensureSampleFile();

        benchmark("async-convert-file", () -> {
            AsyncWorkflowHelper.runAsyncFileConversions(
                    client,
                    SAMPLE_FILE,
                    CONVERT_DIR,
                    Arrays.asList(ConversionOutputType.values()),
                    null
            );
            return null;
        });

        benchmark("async-convert-urls", () -> {
            AsyncWorkflowHelper.runAsyncUrlConversions(
                    client,
                    SAMPLE_URLS,
                    CONVERT_DIR,
                    Arrays.asList(ConversionOutputType.values()),
                    null
            );
            return null;
        });

        benchmark("async-chunk-file", () -> {
            AsyncWorkflowHelper.runAsyncFileChunking(
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

        benchmark("async-chunk-urls", () -> {
            AsyncWorkflowHelper.runAsyncUrlChunking(
                    client,
                    SAMPLE_URLS,
                    CHUNK_DIR,
                    null,
                    DoclingClient.defaultChunkOptions(),
                    false
            );
            return null;
        });

        System.out.println("UsageAsync benchmarks complete.");
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


    private UsageAsync() {
        // no-op
    }
}
