package com.docling.client.modular;

import com.docling.api.*;
import com.docling.spi.HttpRequest;
import com.docling.spi.HttpResponse;
import com.docling.spi.HttpTransport;
import com.docling.spi.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/**
 * Modular Docling client with pluggable HTTP transport and JSON serialization.
 * <p>
 * This client demonstrates the new architecture where HTTP transport and JSON
 * serialization are completely decoupled and swappable at runtime.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Auto-discover implementations via ServiceLoader
 * ModularDoclingClient client = ModularDoclingClient.builder()
 *     .baseUrl("http://localhost:5001")
 *     .apiKey("my-key")
 *     .build();
 *
 * // Or specify explicitly
 * ModularDoclingClient client = ModularDoclingClient.builder()
 *     .baseUrl("http://localhost:5001")
 *     .httpTransport(new NativeHttpTransport())
 *     .jsonSerializer(new JacksonJsonSerializer())
 *     .build();
 *
 * // Use the client
 * CompletableFuture<ConversionResponse> future =
 *     client.convertUrlAsync("https://example.com/doc.pdf", OutputFormat.MARKDOWN);
 * }</pre>
 */
public class ModularDoclingClient {

    private final String baseUrl;
    private final HttpTransport httpTransport;
    private final JsonSerializer jsonSerializer;
    private final String apiKey;

    private ModularDoclingClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.httpTransport = builder.httpTransport;
        this.jsonSerializer = builder.jsonSerializer;
        this.apiKey = builder.apiKey;
    }

    /**
     * Convert a URL to the specified format (synchronous).
     */
    public ConversionResponse convertUrl(String url, OutputFormat format) {
        // Build request to match the OpenAPI-generated client structure
        // Based on ConvertDocumentsRequest + HttpSourceRequest + SourcesInner
        java.util.Map<String, Object> requestMap = new java.util.HashMap<>();

        // Build source object matching HttpSourceRequest structure
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("kind", "http");
        source.put("url", url);
        source.put("headers", new java.util.HashMap<>());  // Empty headers map

        requestMap.put("sources", java.util.List.of(source));

        // Build options matching ConvertDocumentsRequestOptions
        java.util.Map<String, Object> options = new java.util.HashMap<>();
        options.put("to_formats", java.util.List.of(format.getValue()));
        requestMap.put("options", options);

        // Add target field (default value from ConvertDocumentsRequest)
        java.util.Map<String, Object> target = new java.util.HashMap<>();
        target.put("kind", "inbody");
        requestMap.put("target", target);

        String json = jsonSerializer.toJson(requestMap);

        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpRequest httpReq = HttpRequest.builder()
            .method("POST")
            .url(baseUrl + "/v1/convert/source")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, application/zip")  // Match generated client
            .body(body)
            .timeout(120000)  // 120 second timeout like the server
            .build();

        HttpResponse httpResp = httpTransport.execute(httpReq);

        if (!httpResp.isSuccessful()) {
            throw new DoclingException("HTTP " + httpResp.getStatusCode() + ": " + httpResp.getBodyAsString());
        }

        return jsonSerializer.fromJson(httpResp.getBodyAsString(), ConversionResponse.class);
    }

    /**
     * Convert a URL to the specified format (asynchronous).
     */
    public CompletableFuture<ConversionResponse> convertUrlAsync(String url, OutputFormat format) {
        // Build request to match the OpenAPI-generated client structure
        java.util.Map<String, Object> requestMap = new java.util.HashMap<>();

        // Build source object matching HttpSourceRequest structure
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("kind", "http");
        source.put("url", url);
        source.put("headers", new java.util.HashMap<>());  // Empty headers map

        requestMap.put("sources", java.util.List.of(source));

        // Build options matching ConvertDocumentsRequestOptions
        java.util.Map<String, Object> options = new java.util.HashMap<>();
        options.put("to_formats", java.util.List.of(format.getValue()));
        requestMap.put("options", options);

        // Add target field (default value from ConvertDocumentsRequest)
        java.util.Map<String, Object> target = new java.util.HashMap<>();
        target.put("kind", "inbody");
        requestMap.put("target", target);

        String json = jsonSerializer.toJson(requestMap);

        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpRequest httpReq = HttpRequest.builder()
            .method("POST")
            .url(baseUrl + "/v1/convert/source")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, application/zip")  // Match generated client
            .body(body)
            .timeout(120000)  // 120 second timeout like the server
            .build();

        return httpTransport.executeAsync(httpReq)
            .thenApply(httpResp -> {
                if (!httpResp.isSuccessful()) {
                    throw new DoclingException("HTTP " + httpResp.getStatusCode() + ": " + httpResp.getBodyAsString());
                }
                return jsonSerializer.fromJson(httpResp.getBodyAsString(), ConversionResponse.class);
            });
    }

    /**
     * Check server health.
     */
    public boolean health() {
        try {
            HttpRequest httpReq = HttpRequest.builder()
                .method("GET")
                .url(baseUrl + "/health")
                .build();

            HttpResponse httpResp = httpTransport.execute(httpReq);
            return httpResp.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get information about the transport and serializer being used.
     */
    public String getInfo() {
        return String.format("ModularDoclingClient[transport=%s, serializer=%s, baseUrl=%s]",
            httpTransport.getName(),
            jsonSerializer.getName(),
            baseUrl);
    }

    /**
     * Close resources.
     */
    public void close() {
        httpTransport.close();
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ModularDoclingClient.
     */
    public static class Builder {
        private String baseUrl = "http://127.0.0.1:5001";
        private HttpTransport httpTransport;
        private JsonSerializer jsonSerializer;
        private String apiKey;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder httpTransport(HttpTransport transport) {
            this.httpTransport = transport;
            return this;
        }

        public Builder jsonSerializer(JsonSerializer serializer) {
            this.jsonSerializer = serializer;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ModularDoclingClient build() {
            // Auto-discover implementations if not specified
            if (httpTransport == null) {
                httpTransport = discoverHttpTransport();
            }
            if (jsonSerializer == null) {
                jsonSerializer = discoverJsonSerializer();
            }

            return new ModularDoclingClient(this);
        }

        private HttpTransport discoverHttpTransport() {
            ServiceLoader<HttpTransport> loader = ServiceLoader.load(HttpTransport.class);
            return loader.findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No HttpTransport implementation found on classpath. " +
                    "Add docling-transport-native, docling-transport-apache, or docling-transport-okhttp to your dependencies."
                ));
        }

        private JsonSerializer discoverJsonSerializer() {
            ServiceLoader<JsonSerializer> loader = ServiceLoader.load(JsonSerializer.class);
            return loader.findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No JsonSerializer implementation found on classpath. " +
                    "Add docling-json-jackson, docling-json-gson, or docling-json-moshi to your dependencies."
                ));
        }
    }

    /**
     * Exception thrown by the Docling client.
     */
    public static class DoclingException extends RuntimeException {
        public DoclingException(String message) {
            super(message);
        }

        public DoclingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
