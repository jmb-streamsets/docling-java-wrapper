package com.docling.spi;

import java.util.concurrent.CompletableFuture;

/**
 * Service Provider Interface for HTTP transport.
 * <p>
 * Implementations provide pluggable HTTP client libraries
 * (Java native HttpClient, Apache HttpClient, OkHttp, etc.).
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * Register your implementation in META-INF/services/com.docling.spi.HttpTransport
 *
 * @see java.util.ServiceLoader
 */
public interface HttpTransport {

    /**
     * Execute synchronous HTTP request.
     *
     * @param request the HTTP request
     * @return HTTP response
     */
    HttpResponse execute(HttpRequest request);

    /**
     * Execute asynchronous HTTP request (non-blocking).
     *
     * @param request the HTTP request
     * @return CompletableFuture that will complete with HTTP response
     */
    CompletableFuture<HttpResponse> executeAsync(HttpRequest request);

    /**
     * Get the name of this transport implementation (for debugging/logging).
     *
     * @return transport name (e.g., "Native", "Apache", "OkHttp")
     */
    String getName();

    /**
     * Close/cleanup resources used by this transport.
     * Should be called when the transport is no longer needed.
     */
    void close();
}
