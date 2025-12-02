package com.docling.client;

/**
 * Indicates that Docling could not provide the requested on-disk representation
 * (e.g., server responded with a presigned URL instead of inline content).
 */
public class ConversionMaterializationException extends DoclingClientException {

    public ConversionMaterializationException(String message) {
        super(message);
    }

    public ConversionMaterializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
