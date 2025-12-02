package com.docling.client;

import java.net.URI;

/**
 * Exception thrown when the Docling API returns an HTTP error response (4xx or 5xx).
 */
public class DoclingHttpException extends DoclingClientException {

    private final int statusCode;
    private final String method;
    private final URI uri;
    private final String responseBody;

    public DoclingHttpException(String message, int statusCode, String method, URI uri, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.method = method;
        this.uri = uri;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        StringBuilder sb = new StringBuilder(base);
        if (method != null && uri != null) {
            sb.append(" [").append(method).append(" ").append(uri).append("]");
        }
        sb.append(" status=").append(statusCode);
        if (responseBody != null && !responseBody.isEmpty()) {
            String truncated = responseBody.length() > 200
                    ? responseBody.substring(0, 200) + "..."
                    : responseBody;
            sb.append(" response=").append(truncated);
        }
        return sb.toString();
    }
}
