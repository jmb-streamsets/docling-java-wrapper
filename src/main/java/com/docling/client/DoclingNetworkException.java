package com.docling.client;

import java.net.URI;

/**
 * Exception thrown when network-level failures occur during HTTP communication.
 * This includes connection failures, timeouts, and general I/O errors.
 */
public class DoclingNetworkException extends DoclingClientException {

    private final String method;
    private final URI uri;

    public DoclingNetworkException(String message, String method, URI uri) {
        super(message);
        this.method = method;
        this.uri = uri;
    }

    public DoclingNetworkException(String message, String method, URI uri, Throwable cause) {
        super(message, cause);
        this.method = method;
        this.uri = uri;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (method != null && uri != null) {
            return base + " [" + method + " " + uri + "]";
        }
        return base;
    }
}
