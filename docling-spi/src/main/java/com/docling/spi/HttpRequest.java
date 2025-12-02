package com.docling.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Transport-agnostic HTTP request representation.
 * <p>
 * This abstraction allows the client to build HTTP requests without
 * depending on any specific HTTP client library.
 */
public class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final byte[] body;
    private final long timeoutMillis;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.body = builder.body;
        this.timeoutMillis = builder.timeoutMillis;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Create a new builder for constructing HTTP requests.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing HttpRequest instances.
     */
    public static class Builder {
        private String method = "GET";
        private String url;
        private Map<String, String> headers = new HashMap<>();
        private byte[] body;
        private long timeoutMillis = 60000; // 60 seconds default

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder timeout(long millis) {
            this.timeoutMillis = millis;
            return this;
        }

        public HttpRequest build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL is required");
            }
            return new HttpRequest(this);
        }
    }

    @Override
    public String toString() {
        return method + " " + url + " (" + headers.size() + " headers)";
    }
}
