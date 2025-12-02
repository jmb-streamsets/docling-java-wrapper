package com.docling.transport.native_;

import com.docling.spi.HttpRequest;
import com.docling.spi.HttpResponse;
import com.docling.spi.HttpTransport;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Native Java HttpClient implementation of HttpTransport.
 * Uses java.net.http.HttpClient from Java 11+.
 * <p>
 * This implementation is registered via ServiceLoader and will be
 * auto-discovered if no other HTTP transport is specified.
 */
public class NativeHttpTransport implements HttpTransport {

    private final HttpClient httpClient;

    public NativeHttpTransport() {
        this(HttpClient.newBuilder()
            // Docling server expects HTTP/1.1; match DoclingClient defaults.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build());
    }

    public NativeHttpTransport(HttpClient customClient) {
        this.httpClient = customClient;
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        try {
            java.net.http.HttpRequest httpReq = toJavaHttpRequest(request);

            java.net.http.HttpResponse<byte[]> httpResp =
                httpClient.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            return fromJavaHttpResponse(httpResp);

        } catch (Exception e) {
            throw new HttpTransportException("HTTP request failed: " + request, e);
        }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request) {
        try {
            java.net.http.HttpRequest httpReq = toJavaHttpRequest(request);

            return httpClient.sendAsync(httpReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(this::fromJavaHttpResponse)
                .exceptionally(ex -> {
                    throw new HttpTransportException("Async HTTP request failed: " + request, ex);
                });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new HttpTransportException("Failed to start async HTTP request: " + request, e)
            );
        }
    }

    @Override
    public String getName() {
        return "Native";
    }

    @Override
    public void close() {
        // HttpClient doesn't need explicit closing
    }

    /**
     * Convert abstract HttpRequest to java.net.http.HttpRequest.
     */
    private java.net.http.HttpRequest toJavaHttpRequest(HttpRequest req) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(req.getUrl()))
            .timeout(Duration.ofMillis(req.getTimeoutMillis()));

        // Add headers
        req.getHeaders().forEach(builder::header);

        // Add body
        if (req.getBody() != null) {
            builder.method(req.getMethod(),
                java.net.http.HttpRequest.BodyPublishers.ofByteArray(req.getBody()));
        } else {
            builder.method(req.getMethod(),
                java.net.http.HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    /**
     * Convert java.net.http.HttpResponse to abstract HttpResponse.
     */
    private HttpResponse fromJavaHttpResponse(java.net.http.HttpResponse<byte[]> resp) {
        Map<String, String> headers = new HashMap<>();
        resp.headers().map().forEach((k, v) -> {
            if (!v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });

        return new HttpResponse(
            resp.statusCode(),
            headers,
            resp.body()
        );
    }

    /**
     * Exception thrown when HTTP transport operations fail.
     */
    public static class HttpTransportException extends RuntimeException {
        public HttpTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
