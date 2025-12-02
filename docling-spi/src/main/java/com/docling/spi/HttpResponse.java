package com.docling.spi;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Transport-agnostic HTTP response representation.
 * <p>
 * This abstraction allows the client to handle HTTP responses without
 * depending on any specific HTTP client library.
 */
public class HttpResponse {
    private final int statusCode;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpResponse(int statusCode, Map<String, String> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers != null
            ? Collections.unmodifiableMap(new HashMap<>(headers))
            : Collections.emptyMap();
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    /**
     * Get the response body as a UTF-8 string.
     *
     * @return body as string, or null if body is null
     */
    public String getBodyAsString() {
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    /**
     * Check if the response indicates success (2xx status code).
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Check if the response indicates client error (4xx status code).
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Check if the response indicates server error (5xx status code).
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    @Override
    public String toString() {
        return "HTTP " + statusCode + " (" + (body != null ? body.length : 0) + " bytes)";
    }
}
