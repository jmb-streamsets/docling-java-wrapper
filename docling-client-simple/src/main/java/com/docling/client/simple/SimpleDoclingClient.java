package com.docling.client.simple;

import com.docling.api.ConversionResponse;
import com.docling.api.OutputFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Simple, opinionated Docling client using Java 11+ HttpClient and Jackson.
 * <p>
 * This is a "batteries included" client that works out of the box with sensible defaults.
 * No configuration or plugin discovery needed - just create and use.
 * <p>
 * <h2>Architecture</h2>
 * This client is part of the "simple" architecture:
 * <ul>
 *   <li><b>docling-api</b> - Neutral domain models (pure POJOs, no dependencies)</li>
 *   <li><b>docling-client-simple</b> - Opinionated implementation (this module)</li>
 * </ul>
 * <p>
 * <h2>Technology Choices</h2>
 * <ul>
 *   <li><b>HTTP:</b> Java 11+ HttpClient (built-in, no external dependencies)</li>
 *   <li><b>JSON:</b> Jackson (well-tested, widely used, excellent performance)</li>
 * </ul>
 * <p>
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create client with defaults
 * SimpleDoclingClient client = SimpleDoclingClient.create();
 *
 * // Or with custom configuration
 * SimpleDoclingClient client = SimpleDoclingClient.builder()
 *     .baseUrl("http://localhost:5001")
 *     .timeout(Duration.ofMinutes(5))
 *     .build();
 *
 * // Synchronous conversion
 * ConversionResponse response = client.convertUrl(
 *     "https://example.com/document.pdf",
 *     OutputFormat.MARKDOWN
 * );
 *
 * // Asynchronous conversion
 * CompletableFuture<ConversionResponse> future = client.convertUrlAsync(
 *     "https://example.com/document.pdf",
 *     OutputFormat.MARKDOWN
 * );
 *
 * // Don't forget to close when done
 * client.close();
 * }</pre>
 *
 * @see com.docling.api.ConversionResponse
 * @see com.docling.api.OutputFormat
 */
public class SimpleDoclingClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SimpleDoclingClient.class);

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:5001";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    private SimpleDoclingClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.timeout = builder.timeout;
        this.httpClient = HttpClient.newBuilder()
            // Docling server expects HTTP/1.1
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = createObjectMapper();

        log.debug("SimpleDoclingClient created: baseUrl={}, timeout={}", baseUrl, timeout);
    }

    /**
     * Creates a new client with default settings.
     * Uses DOCLING_BASE_URL environment variable or defaults to http://127.0.0.1:5001
     */
    public static SimpleDoclingClient create() {
        return builder().build();
    }

    /**
     * Creates a new builder for customizing the client.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates the ObjectMapper with proper configuration for Docling API.
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Handle snake_case field names from Docling API (md_content, json_content, etc.)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // Be lenient with unknown properties for forward compatibility
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Converts a URL to the specified format (synchronous).
     *
     * @param url    URL of the document to convert
     * @param format Desired output format
     * @return Conversion response with the converted document
     * @throws DoclingClientException if conversion fails
     */
    public ConversionResponse convertUrl(String url, OutputFormat format) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(format, "format cannot be null");

        log.debug("Converting URL synchronously: url={}, format={}", url, format);

        try {
            String requestBody = buildConvertRequest(url, format);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/convert/source"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return handleResponse(response);

        } catch (IOException e) {
            throw new DoclingClientException("Network error during conversion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DoclingClientException("Conversion interrupted", e);
        }
    }

    /**
     * Converts a URL to the specified format (asynchronous).
     *
     * @param url    URL of the document to convert
     * @param format Desired output format
     * @return CompletableFuture with the conversion response
     */
    public CompletableFuture<ConversionResponse> convertUrlAsync(String url, OutputFormat format) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(format, "format cannot be null");

        log.debug("Converting URL asynchronously: url={}, format={}", url, format);

        try {
            String requestBody = buildConvertRequest(url, format);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/convert/source"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);

        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new DoclingClientException("Failed to initiate async conversion", e)
            );
        }
    }

    /**
     * Checks if the Docling server is healthy.
     *
     * @return true if server is healthy, false otherwise
     */
    public boolean health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;

        } catch (Exception e) {
            log.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets information about this client configuration.
     */
    public String getInfo() {
        return String.format("SimpleDoclingClient[baseUrl=%s, timeout=%s]", baseUrl, timeout);
    }

    /**
     * Closes the client and releases resources.
     */
    @Override
    public void close() {
        log.debug("SimpleDoclingClient closed");
        // HttpClient doesn't need explicit closing in Java 11+
        // but we implement AutoCloseable for consistency and future-proofing
    }

    /**
     * Builds the JSON request body for conversion.
     */
    private String buildConvertRequest(String url, OutputFormat format) {
        try {
            Map<String, Object> request = new HashMap<>();

            // Source
            Map<String, Object> source = new HashMap<>();
            source.put("kind", "http");
            source.put("url", url);
            source.put("headers", new HashMap<>());
            request.put("sources", List.of(source));

            // Options
            Map<String, Object> options = new HashMap<>();
            options.put("to_formats", List.of(format.getValue()));
            request.put("options", options);

            // Target
            Map<String, Object> target = new HashMap<>();
            target.put("kind", "inbody");
            request.put("target", target);

            return objectMapper.writeValueAsString(request);

        } catch (IOException e) {
            throw new DoclingClientException("Failed to serialize request", e);
        }
    }

    /**
     * Handles the HTTP response and deserializes it.
     */
    private ConversionResponse handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();

        if (statusCode < 200 || statusCode >= 300) {
            throw new DoclingClientException(
                "HTTP " + statusCode + ": " + response.body()
            );
        }

        try {
            return objectMapper.readValue(response.body(), ConversionResponse.class);
        } catch (IOException e) {
            throw new DoclingClientException("Failed to deserialize response", e);
        }
    }

    /**
     * Builder for SimpleDoclingClient.
     */
    public static class Builder {
        private String baseUrl = System.getenv().getOrDefault("DOCLING_BASE_URL", DEFAULT_BASE_URL);
        private Duration timeout = DEFAULT_TIMEOUT;

        /**
         * Sets the base URL of the Docling server.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            return this;
        }

        /**
         * Sets the request timeout.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
            return this;
        }

        /**
         * Builds the client.
         */
        public SimpleDoclingClient build() {
            return new SimpleDoclingClient(this);
        }
    }

    /**
     * Exception thrown by SimpleDoclingClient.
     */
    public static class DoclingClientException extends RuntimeException {
        public DoclingClientException(String message) {
            super(message);
        }

        public DoclingClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
