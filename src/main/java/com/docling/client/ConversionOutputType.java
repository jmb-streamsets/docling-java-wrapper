package com.docling.client;

import com.docling.model.ConvertDocumentResponse;
import com.docling.model.DoclingDocument;
import com.docling.model.ExportDocumentResponse;
import com.docling.model.OutputFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Supported materialized output types for Docling conversions.
 * <p>
 * This enum defines the output formats for saving converted documents to disk.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Parse from String</h3>
 * <pre>{@code
 * ConversionOutputType type = ConversionOutputType.parse("markdown");
 * // Returns MARKDOWN
 *
 * ConversionOutputType type2 = ConversionOutputType.parse("docling");
 * // Returns DOCLING_JSON
 * }</pre>
 *
 * <h3>From Environment Variable</h3>
 * <pre>{@code
 * // Reads from DOCLING_OUTPUT_TYPE env var, defaults to MARKDOWN
 * ConversionOutputType type = ConversionOutputType.fromNullable(
 *     System.getenv("DOCLING_OUTPUT_TYPE")
 * );
 * }</pre>
 *
 * <h3>Save Conversion Result</h3>
 * <pre>{@code
 * DoclingClient client = DoclingClient.fromEnv();
 * ResponseProcessFileV1ConvertFilePost response = client.convertFile(pdfFile);
 *
 * // Save as Markdown
 * ConversionOutputType.MARKDOWN.write(
 *     response,
 *     Path.of("output.md"),
 *     client.getApiClient().getObjectMapper()
 * );
 * }</pre>
 *
 * <h3>Extract from ZIP</h3>
 * <pre>{@code
 * TaskResultPayload payload = client.downloadTaskResultPayload(taskId);
 *
 * if (payload.isZip()) {
 *     boolean extracted = ConversionOutputType.MARKDOWN.writeFromZip(
 *         payload.body(),
 *         Path.of("output.md")
 *     );
 * }
 * }</pre>
 *
 * <h3>Supported Aliases</h3>
 * <ul>
 *   <li>DOCLING_JSON: "docling", "docling-json", "json", "docling_document"</li>
 *   <li>MARKDOWN: "markdown", "md"</li>
 * </ul>
 *
 * @see ConversionResults
 */
public enum ConversionOutputType {

    DOCLING_JSON(Arrays.asList("docling", "docling-json", "json", "docling_document"),
            OutputFormat.JSON,
            ".docling.json",
            Arrays.asList(".docling.json", ".json")) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            DoclingDocument doclingDocument = document.getJsonContent() != null ? document.getJsonContent().getDoclingDocument() : null;
            if (doclingDocument == null) {
                throw new IllegalStateException("Docling JSON content missing in response");
            }
            ensureParent(destination);
            mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), doclingDocument);
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            return writeZipEntry(zipBytes, destination);
        }
    },

    MARKDOWN(Arrays.asList("markdown", "md"),
            OutputFormat.MD,
            ".md",
            Collections.singletonList(".md")) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            writeStringOrFallback(document,
                    destination,
                    mapper,
                    () -> document.getMdContent() != null ? document.getMdContent().getString() : null,
                    "Markdown not provided by Docling.");
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            return writeZipEntry(zipBytes, destination);
        }
    },

    HTML(Collections.singletonList("html"),
            OutputFormat.HTML,
            ".html",
            Arrays.asList(".html", ".htm")) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            writeStringOrFallback(document,
                    destination,
                    mapper,
                    () -> document.getHtmlContent() != null ? document.getHtmlContent().getString() : null,
                    "HTML not provided by Docling.");
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            return writeZipEntry(zipBytes, destination);
        }
    },

    HTML_SPLIT_PAGE(Arrays.asList("html_split_page", "html-split", "htmlsplit"),
            OutputFormat.HTML_SPLIT_PAGE,
            ".html_split",
            Collections.emptyList()) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            writeStringOrFallback(document,
                    destination,
                    mapper,
                    () -> document.getHtmlContent() != null ? document.getHtmlContent().getString() : null,
                    "HTML split-page archive not embedded in inline response.");
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            if (destination == null) {
                return false;
            }
            if (Files.exists(destination) && !Files.isDirectory(destination)) {
                Files.delete(destination);
            }
            Files.createDirectories(destination);
            boolean extracted = false;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    try {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        Path relative = resolveSplitEntryRelative(entry.getName());
                        if (relative == null) {
                            continue;
                        }
                        Path target = destination.resolve(relative).normalize();
                        if (!target.startsWith(destination)) {
                            log.warn("Skipping suspicious html_split_page entry {}", entry.getName());
                            continue;
                        }
                        ensureParent(target);
                        try (OutputStream out = Files.newOutputStream(target)) {
                            zis.transferTo(out);
                        }
                        extracted = true;
                    } finally {
                        zis.closeEntry();
                    }
                }
            }
            return extracted;
        }
    },

    TEXT(Arrays.asList("text", "txt", "plain"),
            OutputFormat.TEXT,
            ".txt",
            Collections.singletonList(".txt")) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            writeStringOrFallback(document,
                    destination,
                    mapper,
                    () -> document.getTextContent() != null ? document.getTextContent().getString() : null,
                    "Plain text not provided by Docling.");
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            return writeZipEntry(zipBytes, destination);
        }
    },

    DOCTAGS(Arrays.asList("doctags", "tags", "doc-tags"),
            OutputFormat.DOCTAGS,
            ".doctags",
            Arrays.asList(".doctags", ".json")) {
        @Override
        public void write(ConvertDocumentResponse response, Path destination, ObjectMapper mapper) throws IOException {
            ExportDocumentResponse document = requireDocument(response);
            writeStringOrFallback(document,
                    destination,
                    mapper,
                    () -> document.getDoctagsContent() != null ? document.getDoctagsContent().getString() : null,
                    "DocTags not provided by Docling.");
        }

        @Override
        public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
            return writeZipEntry(zipBytes, destination);
        }
    };

    private static final Logger log = LogManager.getLogger(ConversionOutputType.class);

    private final String primaryToken;
    private final Set<String> aliases;
    private final OutputFormat outputFormat;
    private final String fileExtension;
    private final List<String> zipSuffixes;

    ConversionOutputType(List<String> aliases,
                         OutputFormat format,
                         String fileExtension,
                         List<String> zipSuffixes) {
        if (aliases == null || aliases.isEmpty()) {
            throw new IllegalArgumentException("At least one alias is required");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String alias : aliases) {
            normalized.add(alias.toLowerCase(Locale.ROOT));
        }
        this.aliases = Collections.unmodifiableSet(normalized);
        this.primaryToken = normalized.iterator().next();
        this.outputFormat = format;
        this.fileExtension = fileExtension;
        List<String> suffixes = new ArrayList<>();
        for (String suffix : zipSuffixes) {
            suffixes.add(suffix.toLowerCase(Locale.ROOT));
        }
        this.zipSuffixes = Collections.unmodifiableList(suffixes);
    }

    public static ConversionOutputType fromNullable(String raw) {
        return fromNullable(raw, MARKDOWN);
    }

    public static ConversionOutputType fromNullable(String raw, ConversionOutputType defaultType) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultType;
        }
        return parse(raw);
    }

    public static ConversionOutputType parse(String raw) {
        String normalized = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        for (ConversionOutputType value : values()) {
            if (value.aliases.contains(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported output type '" + raw + "'. Supported: " + supportedValues());
    }

    /**
     * Returns a comma-separated string of all supported output type values.
     *
     * @return Supported output types (e.g., "docling, markdown")
     */
    public static String supportedValues() {
        return Arrays.stream(values())
                .map(ConversionOutputType::getPrimaryToken)
                .collect(Collectors.joining(", "));
    }

    private static ExportDocumentResponse requireDocument(ConvertDocumentResponse response) {
        ExportDocumentResponse document = response != null ? response.getDocument() : null;
        if (document == null) {
            throw new IllegalStateException("ConvertDocumentResponse did not include a document payload");
        }
        return document;
    }

    private static void ensureParent(Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static Path writeFallbackDocling(ExportDocumentResponse document,
                                             Path destination,
                                             ObjectMapper mapper) throws IOException {
        DoclingDocument doc;
        try {
            doc = document.getJsonContent() != null ? document.getJsonContent().getDoclingDocument() : null;
        } catch (Exception e) {
            log.debug("Failed to extract DoclingDocument for fallback: {}", e.getMessage());
            doc = null;
        }
        if (doc == null) {
            return destination;
        }
        Path fallback = destination.resolveSibling(destination.getFileName().toString() + ".docling.json");
        ensureParent(fallback);
        mapper.writerWithDefaultPrettyPrinter().writeValue(fallback.toFile(), doc);
        return fallback;
    }

    private static void writeStringOrFallback(ExportDocumentResponse document,
                                              Path destination,
                                              ObjectMapper mapper,
                                              Supplier<String> supplier,
                                              String missingMessage) throws IOException {
        String value = safeExtractString(supplier);
        if (value == null) {
            Path fallback = writeFallbackDocling(document, destination, mapper);
            String message = missingMessage;
            if (fallback != null && destination != null && !fallback.equals(destination)) {
                message = missingMessage + " See " + fallback.getFileName() + " for structured content.";
            }
            ensureParent(destination);
            Files.writeString(destination, message, StandardCharsets.UTF_8);
            return;
        }
        ensureParent(destination);
        Files.writeString(destination, value, StandardCharsets.UTF_8);
    }

    private static String safeExtractString(Supplier<String> supplier) {
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("Unable to extract inline content: {}", e.getMessage());
            return null;
        }
    }

    private static Path resolveSplitEntryRelative(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf("html_split_page");
        if (marker < 0) {
            return null;
        }
        String relative = normalized.substring(marker + "html_split_page".length());
        relative = relative.replaceFirst("^/+", "");
        if (relative.isEmpty()) {
            int lastSlash = normalized.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash + 1 < normalized.length()) {
                relative = normalized.substring(lastSlash + 1);
            } else {
                relative = normalized;
            }
        }
        if (relative.isBlank()) {
            return null;
        }
        return Path.of(relative);
    }

    public abstract void write(ConvertDocumentResponse response,
                               Path destination,
                               ObjectMapper mapper) throws IOException;

    public boolean writeFromZip(byte[] zipBytes, Path destination) throws IOException {
        return false;
    }

    /**
     * Extracts matching entries from a ZIP archive and writes to destination.
     *
     * @param zipBytes    ZIP file bytes
     * @param destination Destination path for extracted content
     * @return true if a matching entry was found and written
     * @throws IOException if extraction fails
     */
    protected boolean writeZipEntry(byte[] zipBytes, Path destination) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (matchesZipEntry(entry.getName())) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        zis.transferTo(baos);
                        ensureParent(destination);
                        Files.write(destination, baos.toByteArray());
                        return true;
                    }
                } finally {
                    zis.closeEntry();
                }
            }
            return false;
        }
    }

    private boolean matchesZipEntry(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : zipSuffixes) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public String getPrimaryToken() {
        return primaryToken;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
