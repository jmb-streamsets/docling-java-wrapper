package com.docling.client;

/**
 * Base exception for all Docling client errors.
 * Provides a hierarchy for specific error types to enable fine-grained exception handling.
 */
public class DoclingClientException extends RuntimeException {

    public DoclingClientException(String message) {
        super(message);
    }

    public DoclingClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public DoclingClientException(Throwable cause) {
        super(cause);
    }
}
