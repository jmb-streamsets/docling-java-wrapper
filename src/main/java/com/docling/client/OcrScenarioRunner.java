package com.docling.client;

import com.docling.invoker.ApiException;
import com.docling.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reusable runner that exercises Docling OCR options across arbitrary inputs and reports results.
 */
public final class OcrScenarioRunner {

    private static final Logger log = LogManager.getLogger(OcrScenarioRunner.class);

    private final DoclingClient client;
    private final Path outputRoot;
    private final ConversionOutputType outputType;
    private final List<InputSource> sources;

    private OcrScenarioRunner(Builder builder) {
        this.client = builder.client;
        this.outputRoot = builder.outputRoot;
        this.outputType = builder.outputType;
        this.sources = Collections.unmodifiableList(new ArrayList<>(builder.sources));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ScenarioRunResult> runAll(List<OcrScenario> scenarios) {
        Objects.requireNonNull(scenarios, "scenarios");
        List<ScenarioRunResult> results = new ArrayList<>();
        for (OcrScenario scenario : scenarios) {
            results.addAll(runScenario(scenario));
        }
        return results;
    }

    public List<ScenarioRunResult> runScenario(OcrScenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        List<ScenarioRunResult> results = new ArrayList<>();
        int successCount = 0;
        for (InputSource source : sources) {
            ScenarioRunResult outcome = executeScenario(scenario, source);
            results.add(outcome);
            if (outcome.status().isSuccess()) {
                successCount++;
            }
        }
        log.info("Scenario={} processed={} success={} failure={}",
                scenario.name(), results.size(), successCount, results.size() - successCount);
        return results;
    }

    public static Path writeReport(List<ScenarioRunResult> results, Path reportFile) throws IOException {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(reportFile, "reportFile");
        Files.createDirectories(reportFile.getParent());
        try (var writer = Files.newBufferedWriter(reportFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(String.join(",",
                    "scenario_name",
                    "source_type",
                    "source_value",
                    "do_ocr",
                    "force_ocr",
                    "ocr_engine",
                    "pipeline",
                    "include_images",
                    "image_mode",
                    "do_picture_description",
                    "picture_description_area_threshold",
                    "do_picture_classification",
                    "status",
                    "duration_millis",
                    "output_path",
                    "message"));
            writer.newLine();
            for (ScenarioRunResult result : results) {
                writer.write(String.join(",",
                        csv(result.scenario().name()),
                        csv(result.source().type().name()),
                        csv(result.source().displayValue()),
                        csv(Boolean.toString(result.scenario().doOcr())),
                        csv(Boolean.toString(result.scenario().forceOcr())),
                        csv(result.scenario().engine().name()),
                        csv(result.scenario().pipeline().name()),
                        csv(Boolean.toString(Boolean.TRUE.equals(result.scenario().includeImages()))),
                        csv(result.scenario().imageMode() != null ? result.scenario().imageMode().name() : ""),
                        csv(Boolean.toString(Boolean.TRUE.equals(result.scenario().doPictureDescription()))),
                        csv(result.scenario().pictureDescriptionAreaThreshold() != null
                                ? result.scenario().pictureDescriptionAreaThreshold().toPlainString()
                                : ""),
                        csv(Boolean.toString(Boolean.TRUE.equals(result.scenario().doPictureClassification()))),
                        csv(result.status().name()),
                        csv(Long.toString(result.durationMillis())),
                        csv(result.outputPath() != null ? result.outputPath().toString() : ""),
                        csv(result.detail())));
                writer.newLine();
            }
        }
        return reportFile;
    }

    public static List<OcrScenario> buildScenarioMatrix(OcrMatrixConfig config) {
        Objects.requireNonNull(config, "config");
        List<OcrScenario> scenarios = new ArrayList<>();
        int counter = 1;
        List<ProcessingPipeline> pipelines = config.pipelines().isEmpty()
                ? List.of(ProcessingPipeline.STANDARD)
                : config.pipelines();
        List<OcrEnginesEnum> engines = config.engines().isEmpty()
                ? Arrays.asList(OcrEnginesEnum.values())
                : config.engines();
        List<BigDecimal> thresholds = config.pictureDescriptionThresholds().isEmpty()
                ? List.of(new BigDecimal("0.02"))
                : config.pictureDescriptionThresholds();
        List<ImageRefMode> imagesModes = config.imageModesWithImages().isEmpty()
                ? List.of(ImageRefMode.EMBEDDED)
                : config.imageModesWithImages();
        ImageRefMode fallbackMode = config.imageModeWithoutImages() != null
                ? config.imageModeWithoutImages()
                : ImageRefMode.PLACEHOLDER;
        for (boolean doOcr : new boolean[]{false, true}) {
            boolean[] forceValues = doOcr ? new boolean[]{false, true} : new boolean[]{false};
            for (boolean forceOcr : forceValues) {
                for (OcrEnginesEnum engine : engines) {
                    for (ProcessingPipeline pipeline : pipelines) {
                        for (boolean includeImages : new boolean[]{false, true}) {
                            List<ImageRefMode> modeCandidates = includeImages ? imagesModes : List.of(fallbackMode);
                            for (ImageRefMode mode : modeCandidates) {
                                for (boolean doPictureDescription : new boolean[]{false, true}) {
                                    List<BigDecimal> thresholdCandidates = doPictureDescription ? thresholds : List.of((BigDecimal) null);
                                    for (BigDecimal threshold : thresholdCandidates) {
                                        for (boolean doPictureClassification : new boolean[]{false, true}) {
                                            String name = String.format("ocr-%04d", counter++);
                                            scenarios.add(new OcrScenario(name,
                                                    doOcr,
                                                    forceOcr,
                                                    engine,
                                                    pipeline,
                                                    includeImages,
                                                    mode,
                                                    doPictureDescription,
                                                    doPictureClassification,
                                                    threshold));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableList(scenarios);
    }

    public static OcrMatrixConfig defaultMatrixConfig() {
        return new OcrMatrixConfig(
                List.of(ProcessingPipeline.LEGACY, ProcessingPipeline.STANDARD),
                List.of(ImageRefMode.EMBEDDED, ImageRefMode.PLACEHOLDER, ImageRefMode.REFERENCED),
                ImageRefMode.PLACEHOLDER,
                List.of(new BigDecimal("0.02"), new BigDecimal("0.05")),
                Arrays.asList(OcrEnginesEnum.values())
        );
    }

    private ScenarioRunResult executeScenario(OcrScenario scenario, InputSource source) {
        Instant start = Instant.now();
        try {
            ConvertDocumentsRequestOptions options = buildScenarioOptions(scenario);
            ResponseProcessUrlV1ConvertSourcePost payload = switch (source.type()) {
                case URL -> client.convertUrl(source.displayValue(), options);
                case FILE -> convertFileSource(source.file(), options);
            };
            ValidationOutcome validation = validateResponse(payload);
            Path destination = null;
            ResultStatus status = validation.success() ? ResultStatus.SUCCESS : ResultStatus.VALIDATION_FAILURE;
            if (validation.success()) {
                destination = writeResult(validation.response(), scenario, source);
            }
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new ScenarioRunResult(scenario, source, status, validation.message(), destination, duration);
        } catch (ConversionMaterializationException ex) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new ScenarioRunResult(scenario,
                    source,
                    ResultStatus.REQUEST_FAILURE,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    null,
                    duration);
        } catch (ApiException | DoclingClientException | IOException ex) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new ScenarioRunResult(scenario,
                    source,
                    ResultStatus.REQUEST_FAILURE,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    null,
                    duration);
        }
    }

    private ConvertDocumentsRequestOptions buildScenarioOptions(OcrScenario scenario) {
        ConvertDocumentsRequestOptions options = DoclingClient.defaultConvertOptions(outputType.getOutputFormat());
        options.doOcr(scenario.doOcr());
        options.forceOcr(scenario.forceOcr());
        if (scenario.engine() != null) {
            options.ocrEngine(scenario.engine());
        }
        if (scenario.pipeline() != null) {
            options.pipeline(scenario.pipeline());
        }
        if (scenario.includeImages() != null) {
            options.includeImages(scenario.includeImages());
        }
        if (scenario.imageMode() != null) {
            options.imageExportMode(scenario.imageMode());
        }
        if (scenario.doPictureDescription() != null) {
            options.doPictureDescription(scenario.doPictureDescription());
        }
        if (scenario.doPictureClassification() != null) {
            options.doPictureClassification(scenario.doPictureClassification());
        }
        if (scenario.pictureDescriptionAreaThreshold() != null) {
            options.pictureDescriptionAreaThreshold(scenario.pictureDescriptionAreaThreshold());
        }
        return options;
    }

    private ResponseProcessUrlV1ConvertSourcePost convertFileSource(Path file,
                                                                    ConvertDocumentsRequestOptions options) throws IOException {
        if (file == null) {
            throw new IOException("File source path was not provided");
        }
        if (!Files.exists(file)) {
            throw new IOException("File source " + file + " does not exist");
        }
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            return client.convertStream(is, file.getFileName().toString(), options);
        }
    }

    private ValidationOutcome validateResponse(ResponseProcessUrlV1ConvertSourcePost response) {
        if (response == null) {
            return ValidationOutcome.failure("Response was null");
        }
        Object instance = response.getActualInstance();
        if (!(instance instanceof ConvertDocumentResponse convert)) {
            return ValidationOutcome.failure("Unexpected response type: " +
                    (instance == null ? "null" : instance.getClass().getSimpleName()))
                    ;
        }
        ConversionStatus status = convert.getStatus();
        if (status == null) {
            return ValidationOutcome.failure("Conversion status missing");
        }
        if (status != ConversionStatus.SUCCESS && status != ConversionStatus.PARTIAL_SUCCESS) {
            return ValidationOutcome.failure("Conversion status=" + status);
        }
        ExportDocumentResponse document = convert.getDocument();
        if (document == null) {
            return ValidationOutcome.failure("Document payload missing");
        }
        String markdown = extractMarkdown(document);
        if (isBlank(markdown)) {
            return ValidationOutcome.failure("Markdown content missing");
        }
        String message = "status=" + status + " mdChars=" + markdown.length();
        return ValidationOutcome.success(message, convert);
    }

    private Path writeResult(ConvertDocumentResponse response,
                             OcrScenario scenario,
                             InputSource source) throws IOException {
        Path destination = outputRoot
                .resolve(scenario.name())
                .resolve(source.safeToken() + outputType.getFileExtension());
        ConversionResults.write(response, destination, outputType, client.getApiClient().getObjectMapper());
        return destination;
    }

    private static String extractMarkdown(ExportDocumentResponse document) {
        if (document.getMdContent() == null) {
            return null;
        }
        try {
            Object actual = document.getMdContent().getActualInstance();
            return actual instanceof String ? (String) actual : null;
        } catch (ClassCastException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String csv(String value) {
        String raw = value == null ? "" : value;
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            raw = raw.replace("\"", "\"\"");
            return "\"" + raw + "\"";
        }
        return raw;
    }

    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unnamed";
        }
        return raw.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
    }

    public static final class Builder {
        private DoclingClient client;
        private Path outputRoot = Path.of("output", "benchmarks", "ocr-options");
        private ConversionOutputType outputType = ConversionOutputType.MARKDOWN;
        private final List<InputSource> sources = new CopyOnWriteArrayList<>();

        public Builder client(DoclingClient client) {
            this.client = client;
            return this;
        }

        public Builder outputRoot(Path outputRoot) {
            if (outputRoot != null) {
                this.outputRoot = outputRoot;
            }
            return this;
        }

        public Builder outputType(ConversionOutputType outputType) {
            if (outputType != null) {
                this.outputType = outputType;
            }
            return this;
        }

        public Builder addSource(InputSource source) {
            if (source != null) {
                this.sources.add(source);
            }
            return this;
        }

        public Builder sources(List<InputSource> sources) {
            this.sources.clear();
            if (sources != null) {
                this.sources.addAll(sources);
            }
            return this;
        }

        public OcrScenarioRunner build() {
            if (client == null) {
                throw new IllegalStateException("client must be provided");
            }
            if (sources.isEmpty()) {
                throw new IllegalStateException("at least one input source must be provided");
            }
            try {
                Files.createDirectories(outputRoot);
            } catch (IOException e) {
                throw new DoclingClientException("Failed to create output directory: " + outputRoot, e);
            }
            return new OcrScenarioRunner(this);
        }
    }

    public record OcrMatrixConfig(List<ProcessingPipeline> pipelines,
                                  List<ImageRefMode> imageModesWithImages,
                                  ImageRefMode imageModeWithoutImages,
                                  List<BigDecimal> pictureDescriptionThresholds,
                                  List<OcrEnginesEnum> engines) {
        public OcrMatrixConfig {
            pipelines = pipelines != null ? pipelines : Collections.emptyList();
            imageModesWithImages = imageModesWithImages != null ? imageModesWithImages : Collections.emptyList();
            pictureDescriptionThresholds = pictureDescriptionThresholds != null ? pictureDescriptionThresholds : Collections.emptyList();
            engines = engines != null ? engines : Collections.emptyList();
        }
    }

    public record OcrScenario(String name,
                               boolean doOcr,
                               boolean forceOcr,
                               OcrEnginesEnum engine,
                               ProcessingPipeline pipeline,
                               Boolean includeImages,
                               ImageRefMode imageMode,
                               Boolean doPictureDescription,
                               Boolean doPictureClassification,
                               BigDecimal pictureDescriptionAreaThreshold) {
    }

    public record InputSource(SourceType type, String displayValue, Path file) {
        public static InputSource url(String url) {
            return new InputSource(SourceType.URL, url, null);
        }

        public static InputSource file(Path path) {
            return new InputSource(SourceType.FILE,
                    path != null ? path.toAbsolutePath().toString() : null,
                    path);
        }

        String safeToken() {
            if (type == SourceType.FILE && file != null) {
                return safeName(file.getFileName().toString());
            }
            return safeName(displayValue);
        }
    }

    public enum SourceType {
        URL,
        FILE
    }

    public enum ResultStatus {
        SUCCESS(true),
        VALIDATION_FAILURE(false),
        REQUEST_FAILURE(false);

        private final boolean success;

        ResultStatus(boolean success) {
            this.success = success;
        }

        boolean isSuccess() {
            return success;
        }
    }

    public record ScenarioRunResult(OcrScenario scenario,
                                    InputSource source,
                                    ResultStatus status,
                                    String detail,
                                    Path outputPath,
                                    long durationMillis) {
    }

    private record ValidationOutcome(boolean success, String message, ConvertDocumentResponse response) {
        static ValidationOutcome success(String message, ConvertDocumentResponse response) {
            return new ValidationOutcome(true, message, response);
        }

        static ValidationOutcome failure(String message) {
            return new ValidationOutcome(false, message, null);
        }
    }
}
