package com.docling.client.simple;

import com.docling.api.ConversionResponse;
import com.docling.api.OutputFormat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark demonstrating the simple, opinionated client architecture.
 * <p>
 * This benchmark showcases the "batteries included" approach:
 * <ul>
 *   <li>No plugin discovery or configuration needed</li>
 *   <li>Just create the client and use it</li>
 *   <li>Java 11+ HttpClient + Jackson bundled together</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * # Ensure Docling server is running
 * docker run -p 5001:5001 docling/server
 *
 * # Run benchmark
 * ./gradlew :docling-client-simple:run
 * </pre>
 */
public class SimpleBenchmark {

    private static final String BASE_URL = System.getenv().getOrDefault("DOCLING_BASE_URL", "http://127.0.0.1:5001");
    private static final String TEST_URL = System.getenv().getOrDefault(
        "TEST_URL",
        "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf"
    );
    private static final int WARMUP_ITERATIONS = 1;
    private static final int BENCHMARK_ITERATIONS = 3;

    public static void main(String[] args) {
        printHeader();
        printConfiguration();

        // Create the client - no plugins, no configuration, just works!
        SimpleDoclingClient client = SimpleDoclingClient.builder()
            .baseUrl(BASE_URL)
            .timeout(Duration.ofMinutes(3))
            .build();

        System.out.println("Client: " + client.getInfo());
        System.out.println();

        // Check server health
        System.out.println("Testing server connectivity...");
        if (!client.health()) {
            System.err.println("ERROR: Cannot connect to Docling server at " + BASE_URL);
            System.err.println("Please ensure the server is running:");
            System.err.println("  docker run -p 5001:5001 docling/server");
            client.close();
            System.exit(1);
        }
        System.out.println("Server is healthy");
        System.out.println();

        // Run benchmarks
        List<BenchmarkResult> results = new ArrayList<>();

        results.add(benchmarkSync(client));
        results.add(benchmarkAsync(client));

        // Print summary
        printSummary(results);

        client.close();
        System.out.println();
        System.out.println("Benchmark completed successfully!");
    }

    private static BenchmarkResult benchmarkSync(SimpleDoclingClient client) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("SYNCHRONOUS Benchmark");
        System.out.println("═══════════════════════════════════════════════════════════════");

        // Warmup
        System.out.print("  Warmup: ");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                client.convertUrl(TEST_URL, OutputFormat.MARKDOWN);
                System.out.print(".");
            } catch (Exception e) {
                System.out.println("\n  Warmup failed: " + e.getMessage());
                return new BenchmarkResult("SYNC", 0, 0, 0, false, e.getMessage());
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

                if (response == null || response.getDocument() == null) {
                    throw new Exception("Empty response");
                }
            } catch (Exception e) {
                System.out.println("\n  Benchmark failed: " + e.getMessage());
                success = false;
                error = e.getMessage();
                break;
            }
        }
        System.out.println(success ? " done" : "");

        BenchmarkResult result = calculateResult("SYNC", times, success, error);
        System.out.println("  " + result);
        System.out.println();
        return result;
    }

    private static BenchmarkResult benchmarkAsync(SimpleDoclingClient client) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("ASYNCHRONOUS Benchmark");
        System.out.println("═══════════════════════════════════════════════════════════════");

        // Warmup
        System.out.print("  Warmup: ");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                client.convertUrlAsync(TEST_URL, OutputFormat.MARKDOWN).get();
                System.out.print(".");
            } catch (Exception e) {
                System.out.println("\n  Warmup failed: " + e.getMessage());
                return new BenchmarkResult("ASYNC", 0, 0, 0, false, e.getMessage());
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

                if (response == null || response.getDocument() == null) {
                    throw new Exception("Empty response");
                }
            } catch (Exception e) {
                System.out.println("\n  Benchmark failed: " + e.getMessage());
                success = false;
                error = e.getMessage();
                break;
            }
        }
        System.out.println(success ? " done" : "");

        BenchmarkResult result = calculateResult("ASYNC", times, success, error);
        System.out.println("  " + result);
        System.out.println();
        return result;
    }

    private static BenchmarkResult calculateResult(String mode, List<Long> times, boolean success, String error) {
        if (!success || times.isEmpty()) {
            return new BenchmarkResult(mode, 0, 0, 0, false, error);
        }

        long sum = times.stream().mapToLong(Long::longValue).sum();
        long avg = sum / times.size();
        long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = times.stream().mapToLong(Long::longValue).max().orElse(0);

        return new BenchmarkResult(mode, avg, min, max, true, null);
    }

    private static void printHeader() {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Docling Simple Client Benchmark                              ║");
        System.out.println("║  (Opinionated: Java HttpClient + Jackson)                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printConfiguration() {
        System.out.println("Configuration:");
        System.out.println("  Server URL: " + BASE_URL);
        System.out.println("  Test Document: " + TEST_URL);
        System.out.println("  Warmup Iterations: " + WARMUP_ITERATIONS);
        System.out.println("  Benchmark Iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();
        System.out.println("Technology Stack:");
        System.out.println("  HTTP Client: Java 11+ HttpClient (built-in)");
        System.out.println("  JSON Library: Jackson");
        System.out.println();
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  BENCHMARK SUMMARY                                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("%-12s %10s %10s %10s %-10s%n",
            "Mode", "Avg (ms)", "Min (ms)", "Max (ms)", "Status");
        System.out.println("─".repeat(55));

        for (BenchmarkResult result : results) {
            String status = result.success ? "OK" : "FAILED";
            System.out.printf("%-12s %10d %10d %10d %-10s%n",
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

        // Find fastest
        BenchmarkResult fastest = results.stream()
            .filter(r -> r.success)
            .min((a, b) -> Long.compare(a.avgMs, b.avgMs))
            .orElse(null);

        if (fastest != null) {
            System.out.println("Fastest Mode: " + fastest.mode + " (" + fastest.avgMs + "ms avg)");
        }
    }

    private static class BenchmarkResult {
        final String mode;
        final long avgMs;
        final long minMs;
        final long maxMs;
        final boolean success;
        final String error;

        BenchmarkResult(String mode, long avgMs, long minMs, long maxMs, boolean success, String error) {
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
                return String.format("avg=%dms, min=%dms, max=%dms", avgMs, minMs, maxMs);
            } else {
                return "FAILED: " + error;
            }
        }
    }
}
