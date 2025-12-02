package com.docling.benchmark;

import com.docling.api.OutputFormat;
import com.docling.api.ConversionResponse;
import com.docling.client.modular.ModularDoclingClient;
import com.docling.spi.HttpTransport;
import com.docling.spi.JsonSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark demonstrating the new modular architecture.
 * <p>
 * This benchmark tests all available combinations of:
 * - HTTP transports (Native, Apache, OkHttp)
 * - JSON serializers (Jackson, Gson)
 * <p>
 * It measures performance for both synchronous and asynchronous operations,
 * showcasing the pluggability of the SPI-based design.
 * <p>
 * Usage:
 * <pre>
 * # Ensure Docling server is running
 * docker run -p 5001:5001 docling/server
 *
 * # Run benchmark with all available implementations
 * ./gradlew :docling-client-modular:run -PmainClass=com.docling.benchmark.ModularBenchmark
 *
 * # Or set custom server URL
 * DOCLING_BASE_URL=http://localhost:5001 ./gradlew :docling-client-modular:run -PmainClass=com.docling.benchmark.ModularBenchmark
 * </pre>
 */
public class ModularBenchmark {

    private static final String BASE_URL = System.getenv().getOrDefault("DOCLING_BASE_URL", "http://127.0.0.1:5001");
    // Use a smaller test document to avoid sync endpoint timeout (max 120s)
    // Arxiv PDFs are too large: "https://arxiv.org/pdf/2206.01062"
    private static final String TEST_URL = System.getenv().getOrDefault(
        "TEST_URL",
        "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf"  // Small test PDF
    );
    private static final int WARMUP_ITERATIONS = 1;  // Reduced for faster testing
    private static final int BENCHMARK_ITERATIONS = 3;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Docling Modular Architecture Benchmark                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Server URL: " + BASE_URL);
        System.out.println("Test Document: " + TEST_URL);
        System.out.println("Iterations: " + BENCHMARK_ITERATIONS + " (after " + WARMUP_ITERATIONS + " warmup)");
        System.out.println();

        // Discover all available implementations
        List<HttpTransport> transports = discoverTransports();
        List<JsonSerializer> serializers = discoverSerializers();

        System.out.println("Discovered Implementations:");
        System.out.println("  HTTP Transports: " + transports.stream().map(HttpTransport::getName).toList());
        System.out.println("  JSON Serializers: " + serializers.stream().map(JsonSerializer::getName).toList());
        System.out.println();

        if (transports.isEmpty() || serializers.isEmpty()) {
            System.err.println("ERROR: No implementations found!");
            System.err.println("Ensure you have the required dependencies on the classpath:");
            System.err.println("  - docling-transport-* (native, apache, okhttp)");
            System.err.println("  - docling-json-* (jackson, gson)");
            System.exit(1);
        }

        // Test server connectivity
        System.out.println("Testing server connectivity...");
        ModularDoclingClient testClient = ModularDoclingClient.builder()
            .baseUrl(BASE_URL)
            .httpTransport(transports.get(0))
            .jsonSerializer(serializers.get(0))
            .build();

        if (!testClient.health()) {
            System.err.println("ERROR: Cannot connect to Docling server at " + BASE_URL);
            System.err.println("Please ensure the server is running:");
            System.err.println("  docker run -p 5001:5001 docling/server");
            testClient.close();
            System.exit(1);
        }
        System.out.println("✓ Server is healthy");
        System.out.println();
        testClient.close();

        // Run benchmarks for all combinations
        List<BenchmarkResult> results = new ArrayList<>();

        for (HttpTransport transport : transports) {
            for (JsonSerializer serializer : serializers) {
                System.out.println("═══════════════════════════════════════════════════════════════");
                System.out.println("Testing: " + transport.getName() + " + " + serializer.getName());
                System.out.println("═══════════════════════════════════════════════════════════════");

                ModularDoclingClient client = ModularDoclingClient.builder()
                    .baseUrl(BASE_URL)
                    .httpTransport(transport)
                    .jsonSerializer(serializer)
                    .build();

                System.out.println("Client: " + client.getInfo());
                System.out.println();

                // Synchronous benchmark
                BenchmarkResult syncResult = benchmarkSync(client);
                results.add(syncResult);

                // Asynchronous benchmark
                BenchmarkResult asyncResult = benchmarkAsync(client);
                results.add(asyncResult);

                System.out.println();
            }
        }

        // Print summary
        printSummary(results);

        System.out.println();
        System.out.println("Benchmark completed successfully!");
    }

    private static BenchmarkResult benchmarkSync(ModularDoclingClient client) {
        System.out.println("Running SYNCHRONOUS benchmark...");

        // Warmup
        System.out.print("  Warmup: ");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                client.convertUrl(TEST_URL, OutputFormat.MARKDOWN);
                System.out.print(".");
            } catch (Exception e) {
                System.out.println("\n  ✗ Warmup failed: " + e.getMessage());
                return new BenchmarkResult(
                    client.getInfo(),
                    "SYNC",
                    0,
                    0,
                    0,
                    false,
                    e.getMessage()
                );
            }
        }
        System.out.println(" done");

        // Benchmark
        System.out.print("  Benchmark: ");
        List<Long> times = new ArrayList<>();
        boolean success = true;
        String error = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try {
                long start = System.nanoTime();
                ConversionResponse response = client.convertUrl(TEST_URL, OutputFormat.MARKDOWN);
                long end = System.nanoTime();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(end - start);
                times.add(durationMs);
                System.out.print(".");

                // Validate response
                if (response == null || response.getDocument() == null) {
                    throw new Exception("Empty response");
                }
            } catch (Exception e) {
                System.out.println("\n  ✗ Benchmark failed: " + e.getMessage());
                success = false;
                error = e.getMessage();
                break;
            }
        }
        System.out.println(success ? " done" : "");

        BenchmarkResult result = calculateResult(client.getInfo(), "SYNC", times, success, error);
        System.out.println("  " + result);
        return result;
    }

    private static BenchmarkResult benchmarkAsync(ModularDoclingClient client) {
        System.out.println("Running ASYNCHRONOUS benchmark...");

        // Warmup
        System.out.print("  Warmup: ");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                client.convertUrlAsync(TEST_URL, OutputFormat.MARKDOWN).get();
                System.out.print(".");
            } catch (Exception e) {
                System.out.println("\n  ✗ Warmup failed: " + e.getMessage());
                return new BenchmarkResult(
                    client.getInfo(),
                    "ASYNC",
                    0,
                    0,
                    0,
                    false,
                    e.getMessage()
                );
            }
        }
        System.out.println(" done");

        // Benchmark
        System.out.print("  Benchmark: ");
        List<Long> times = new ArrayList<>();
        boolean success = true;
        String error = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try {
                long start = System.nanoTime();
                ConversionResponse response = client.convertUrlAsync(TEST_URL, OutputFormat.MARKDOWN).get();
                long end = System.nanoTime();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(end - start);
                times.add(durationMs);
                System.out.print(".");

                // Validate response
                if (response == null || response.getDocument() == null) {
                    throw new Exception("Empty response");
                }
            } catch (Exception e) {
                System.out.println("\n  ✗ Benchmark failed: " + e.getMessage());
                success = false;
                error = e.getMessage();
                break;
            }
        }
        System.out.println(success ? " done" : "");

        BenchmarkResult result = calculateResult(client.getInfo(), "ASYNC", times, success, error);
        System.out.println("  " + result);
        return result;
    }

    private static BenchmarkResult calculateResult(String config, String mode, List<Long> times, boolean success, String error) {
        if (!success || times.isEmpty()) {
            return new BenchmarkResult(config, mode, 0, 0, 0, false, error);
        }

        long sum = times.stream().mapToLong(Long::longValue).sum();
        long avg = sum / times.size();
        long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = times.stream().mapToLong(Long::longValue).max().orElse(0);

        return new BenchmarkResult(config, mode, avg, min, max, true, null);
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  BENCHMARK SUMMARY                                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("%-40s %-8s %10s %10s %10s %-10s%n",
            "Configuration", "Mode", "Avg (ms)", "Min (ms)", "Max (ms)", "Status");
        System.out.println("─".repeat(90));

        for (BenchmarkResult result : results) {
            String configShort = result.config
                .replace("ModularDoclingClient[transport=", "")
                .replace(", serializer=", "+")
                .replaceAll(", baseUrl=.*\\]", "");

            String status = result.success ? "✓" : "✗";
            System.out.printf("%-40s %-8s %10d %10d %10d %-10s%n",
                configShort,
                result.mode,
                result.avgMs,
                result.minMs,
                result.maxMs,
                status);

            if (!result.success && result.error != null) {
                System.out.println("  Error: " + result.error);
            }
        }

        System.out.println();

        // Find fastest combination
        BenchmarkResult fastest = results.stream()
            .filter(r -> r.success)
            .min((a, b) -> Long.compare(a.avgMs, b.avgMs))
            .orElse(null);

        if (fastest != null) {
            System.out.println("Fastest Configuration:");
            System.out.println("  " + fastest.config.replace("ModularDoclingClient[", "").replace("]", ""));
            System.out.println("  Mode: " + fastest.mode);
            System.out.println("  Avg: " + fastest.avgMs + "ms");
        }
    }

    private static List<HttpTransport> discoverTransports() {
        List<HttpTransport> transports = new ArrayList<>();
        ServiceLoader.load(HttpTransport.class).forEach(transports::add);
        return transports;
    }

    private static List<JsonSerializer> discoverSerializers() {
        List<JsonSerializer> serializers = new ArrayList<>();
        ServiceLoader.load(JsonSerializer.class).forEach(serializers::add);
        return serializers;
    }

    /**
     * Holds benchmark results for a specific configuration.
     */
    private static class BenchmarkResult {
        final String config;
        final String mode;
        final long avgMs;
        final long minMs;
        final long maxMs;
        final boolean success;
        final String error;

        BenchmarkResult(String config, String mode, long avgMs, long minMs, long maxMs, boolean success, String error) {
            this.config = config;
            this.mode = mode;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.success = success;
            this.error = error;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("✓ avg=%dms, min=%dms, max=%dms", avgMs, minMs, maxMs);
            } else {
                return "✗ FAILED: " + error;
            }
        }
    }
}
