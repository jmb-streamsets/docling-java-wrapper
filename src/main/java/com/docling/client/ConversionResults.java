package com.docling.client;

import com.docling.model.ConvertDocumentResponse;
import com.docling.model.ResponseProcessFileV1ConvertFilePost;
import com.docling.model.ResponseProcessUrlV1ConvertSourcePost;
import com.docling.model.ResponseTaskResultV1ResultTaskIdGet;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Small helpers for turning Docling convert responses into materialized files.
 */
public final class ConversionResults {

    private static final Logger log = LogManager.getLogger(ConversionResults.class);

    private ConversionResults() {
    }

    public static Path write(Object payload,
                             Path destination,
                             ConversionOutputType outputType,
                             ObjectMapper mapper) throws IOException {
        ConvertDocumentResponse response = unwrap(payload, outputType);
        outputType.write(response, destination, mapper);
        return destination;
    }

    public static ConvertDocumentResponse unwrap(Object payload, ConversionOutputType outputType) {
        if (payload instanceof ConvertDocumentResponse) {
            return (ConvertDocumentResponse) payload;
        }
        if (payload instanceof ResponseProcessUrlV1ConvertSourcePost) {
            return extractFromAnyOf(((ResponseProcessUrlV1ConvertSourcePost) payload)::getConvertDocumentResponse,
                    outputType,
                    "convert response body");
        }
        if (payload instanceof ResponseProcessFileV1ConvertFilePost) {
            return extractFromAnyOf(((ResponseProcessFileV1ConvertFilePost) payload)::getConvertDocumentResponse,
                    outputType,
                    "convert response body");
        }
        if (payload instanceof ResponseTaskResultV1ResultTaskIdGet) {
            return extractFromAnyOf(((ResponseTaskResultV1ResultTaskIdGet) payload)::getConvertDocumentResponse,
                    outputType,
                    "task result");
        }
        throw new IllegalArgumentException("Unsupported payload type " +
                (payload == null ? "null" : payload.getClass().getName()));
    }

    private static ConvertDocumentResponse extractFromAnyOf(Supplier<ConvertDocumentResponse> supplier,
                                                            ConversionOutputType outputType,
                                                            String context) {
        try {
            return supplier.get();
        } catch (ClassCastException e) {
            log.debug("ClassCastException while extracting {} from {}: {}", outputType.getPrimaryToken(), context, e.getMessage());
            throw new ConversionMaterializationException(
                    "Server returned a presigned URL for " + context + "; cannot materialize " + outputType.getPrimaryToken(), e);
        }
    }

    public static Path writeFromTask(DoclingClient client,
                                     String taskId,
                                     Path destination,
                                     ConversionOutputType outputType,
                                     ObjectMapper mapper) throws IOException {
        DoclingClient.TaskResultPayload payload;
        try {
            payload = client.downloadTaskResultPayload(taskId);
        } catch (DoclingClientException e) {
            // Propagate Docling-specific exceptions as-is
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to download task result " + taskId, e);
        }
        ensureParent(destination);
        if (payload.isZip()) {
            if (outputType.writeFromZip(payload.body(), destination)) {
                return destination;
            }
            throw new ConversionMaterializationException("Zip result for task " + taskId +
                    " did not contain " + outputType.getPrimaryToken() + " entry");
        }
        try {
            ConvertDocumentResponse document = mapper.readValue(payload.body(), ConvertDocumentResponse.class);
            return write(document, destination, outputType, mapper);
        } catch (Exception parseError) {
            // Task results may return md_content as plain string instead of wrapped in MdContent object
            // Or the polymorphic types may not match the expected structure
            // Try to extract content directly from the JSON
            try {
                var tree = mapper.readTree(payload.body());
                var documentNode = tree.get("document");
                if (documentNode != null) {
                    var mdContentNode = documentNode.get("md_content");
                    var jsonContentNode = documentNode.get("json_content");
                    var htmlContentNode = documentNode.get("html_content");
                    var textContentNode = documentNode.get("text_content");
                    var doctagsContentNode = documentNode.get("doctags_content");

                    if (outputType == ConversionOutputType.MARKDOWN && mdContentNode != null && mdContentNode.isTextual()) {
                        ensureParent(destination);
                        Files.writeString(destination, mdContentNode.asText());
                        return destination;
                    } else if (outputType == ConversionOutputType.DOCLING_JSON && jsonContentNode != null) {
                        ensureParent(destination);
                        Files.write(destination, jsonContentNode.toString().getBytes());
                        return destination;
                    } else if (outputType == ConversionOutputType.HTML && htmlContentNode != null && htmlContentNode.isTextual()) {
                        ensureParent(destination);
                        Files.writeString(destination, htmlContentNode.asText());
                        return destination;
                    } else if (outputType == ConversionOutputType.TEXT && textContentNode != null && textContentNode.isTextual()) {
                        ensureParent(destination);
                        Files.writeString(destination, textContentNode.asText());
                        return destination;
                    } else if (outputType == ConversionOutputType.DOCTAGS && doctagsContentNode != null && doctagsContentNode.isTextual()) {
                        ensureParent(destination);
                        Files.writeString(destination, doctagsContentNode.asText());
                        return destination;
                    }
                }
            } catch (Exception fallbackError) {
                // Log the fallback error but continue to save raw file for debugging
                log.debug("Fallback JSON extraction failed for task {}: {}", taskId, fallbackError.getMessage());
            }

            // Save raw payload for debugging
            Path raw = destination.resolveSibling(destination.getFileName() + ".raw.json");
            ensureParent(raw);
            Files.write(raw, payload.body());
            throw new ConversionMaterializationException(
                    "Downloaded JSON could not be parsed as " + outputType.getPrimaryToken() +
                            " content; raw payload saved to " + raw.getFileName() +
                            " (error: " + parseError.getMessage() + ")",
                    parseError
            );
        }
    }

    private static void ensureParent(Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
