package com.docling.client;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI harness that delegates to {@link OcrScenarioRunner} to exhaustively sweep OCR options.
 */
public final class UsageOcrOptions {

    private static final List<String> SAMPLE_URLS = List.of(
            "https://arxiv.org/pdf/2501.17887"
    );

    private static final Path SAMPLE_FILE = Path.of("documents/2512.01970v1.pdf");

    private static final Path OUTPUT_ROOT = Path.of("output", "benchmarks", "ocr-options");
    private static final Path REPORT_FILE = OUTPUT_ROOT.resolve("ocr-scenarios-report.csv");

    private static final OcrScenarioRunner.OcrMatrixConfig MATRIX_CONFIG = DoclingClient.defaultOcrScenarioMatrix();
    private static final List<OcrScenarioRunner.OcrScenario> SCENARIOS =
            DoclingClient.buildOcrScenarioMatrix(MATRIX_CONFIG);
    private static final List<OcrScenarioRunner.InputSource> INPUT_SOURCES = buildInputSources();

    private UsageOcrOptions() {
        // utility
    }

    public static void main(String[] args) throws Exception {
        DoclingClient client = DoclingClient.fromEnv();
        System.out.printf("Running %d OCR option scenarios across %d sources...%n", SCENARIOS.size(), INPUT_SOURCES.size());

        List<OcrScenarioRunner.ScenarioRunResult> allResults =
                benchmark("ocr-sweep", () -> client.runOcrScenarioSweep(
                        SCENARIOS,
                        INPUT_SOURCES,
                        ConversionOutputType.MARKDOWN,
                        OUTPUT_ROOT));

        OcrScenarioRunner.writeReport(allResults, REPORT_FILE);
        System.out.printf("UsageOcrOptions scenarios complete. Total runs=%d report=%s%n",
                allResults.size(), REPORT_FILE.toAbsolutePath());
    }

    private static List<OcrScenarioRunner.InputSource> buildInputSources() {
        List<OcrScenarioRunner.InputSource> sources = new ArrayList<>();
        SAMPLE_URLS.forEach(url -> sources.add(DoclingClient.ocrSourceFromUrl(url)));
        sources.add(DoclingClient.ocrSourceFromFile(SAMPLE_FILE));
        return sources;
    }

    private static <T> T benchmark(String label, Callable<T> action) throws Exception {
        Instant start = Instant.now();
        try {
            System.out.println("Starting " + label + "...");
            return action.call();
        } finally {
            long millis = Duration.between(start, Instant.now()).toMillis();
            System.out.println(label + " finished in " + millis + " ms");
        }
    }
}
