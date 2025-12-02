package com.docling.client;

/**
 * Exception thrown when an operation times out.
 */
public class DoclingTimeoutException extends DoclingClientException {

    private final long timeoutSeconds;
    private final String operation;

    public DoclingTimeoutException(String message, String operation, long timeoutSeconds) {
        super(message);
        this.operation = operation;
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (operation != null) {
            return base + " [operation=" + operation + " timeout=" + timeoutSeconds + "s]";
        }
        return base;
    }
}
