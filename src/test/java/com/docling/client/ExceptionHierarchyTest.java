package com.docling.client;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for exception hierarchy and message formatting.
 */
class ExceptionHierarchyTest {

    @Test
    void doclingClientExceptionIsRuntimeException() {
        assertTrue(new DoclingClientException("test") instanceof RuntimeException);
    }

    @Test
    void allExceptionsExtendDoclingClientException() {
        assertTrue(new DoclingNetworkException("test", "GET", null) instanceof DoclingClientException);
        assertTrue(new DoclingHttpException("test", 500, "POST", null, null) instanceof DoclingClientException);
        assertTrue(new DoclingTaskFailureException("test", "task-1", "failed", null) instanceof DoclingClientException);
        assertTrue(new DoclingTimeoutException("test", "operation", 300) instanceof DoclingClientException);
        assertTrue(new ConversionMaterializationException("test") instanceof DoclingClientException);
    }

    @Test
    void doclingNetworkExceptionFormatsMessage() {
        URI uri = URI.create("http://example.com/api/test");
        DoclingNetworkException ex = new DoclingNetworkException(
                "Connection failed",
                "POST",
                uri
        );

        String message = ex.getMessage();
        assertTrue(message.contains("Connection failed"));
        assertTrue(message.contains("POST"));
        assertTrue(message.contains("http://example.com/api/test"));

        assertEquals("POST", ex.getMethod());
        assertEquals(uri, ex.getUri());
    }

    @Test
    void doclingHttpExceptionFormatsMessage() {
        URI uri = URI.create("http://example.com/api/test");
        String responseBody = "Error details from server";

        DoclingHttpException ex = new DoclingHttpException(
                "Request failed",
                503,
                "POST",
                uri,
                responseBody
        );

        String message = ex.getMessage();
        assertTrue(message.contains("Request failed"));
        assertTrue(message.contains("503"));
        assertTrue(message.contains("POST"));
        assertTrue(message.contains("http://example.com/api/test"));
        assertTrue(message.contains(responseBody));

        assertEquals(503, ex.getStatusCode());
        assertEquals("POST", ex.getMethod());
        assertEquals(uri, ex.getUri());
        assertEquals(responseBody, ex.getResponseBody());
    }

    @Test
    void doclingHttpExceptionTruncatesLongBody() {
        String longBody = "x".repeat(500);
        DoclingHttpException ex = new DoclingHttpException(
                "Request failed",
                500,
                "GET",
                null,
                longBody
        );

        String message = ex.getMessage();
        assertTrue(message.length() < longBody.length() + 100); // Message should be truncated
        assertTrue(message.contains("...")); // Should indicate truncation
    }

    @Test
    void doclingHttpExceptionIdentifiesErrorTypes() {
        DoclingHttpException clientError = new DoclingHttpException("test", 400, "GET", null, null);
        assertTrue(clientError.isClientError());
        assertFalse(clientError.isServerError());

        DoclingHttpException serverError = new DoclingHttpException("test", 500, "GET", null, null);
        assertTrue(serverError.isServerError());
        assertFalse(serverError.isClientError());

        DoclingHttpException success = new DoclingHttpException("test", 200, "GET", null, null);
        assertFalse(success.isClientError());
        assertFalse(success.isServerError());
    }

    @Test
    void doclingTaskFailureExceptionFormatsMessage() {
        DoclingTaskFailureException ex = new DoclingTaskFailureException(
                "Task failed",
                "task-123",
                "error",
                "Out of memory"
        );

        String message = ex.getMessage();
        assertTrue(message.contains("Task failed"));
        assertTrue(message.contains("task-123"));
        assertTrue(message.contains("error"));
        assertTrue(message.contains("Out of memory"));

        assertEquals("task-123", ex.getTaskId());
        assertEquals("error", ex.getTaskStatus());
        assertEquals("Out of memory", ex.getTaskMeta());
    }

    @Test
    void doclingTimeoutExceptionFormatsMessage() {
        DoclingTimeoutException ex = new DoclingTimeoutException(
                "Operation timed out",
                "waitForTaskResult",
                900
        );

        String message = ex.getMessage();
        assertTrue(message.contains("Operation timed out"));
        assertTrue(message.contains("waitForTaskResult"));
        assertTrue(message.contains("900"));

        assertEquals("waitForTaskResult", ex.getOperation());
        assertEquals(900, ex.getTimeoutSeconds());
    }

    @Test
    void conversionMaterializationExceptionSupportsChaining() {
        Exception cause = new RuntimeException("Original error");
        ConversionMaterializationException ex = new ConversionMaterializationException(
                "Cannot materialize",
                cause
        );

        assertEquals("Cannot materialize", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void exceptionsCanBeCaughtByBaseClass() {
        DoclingClientException[] exceptions = {
                new DoclingNetworkException("test", "GET", null),
                new DoclingHttpException("test", 500, "POST", null, null),
                new DoclingTaskFailureException("test", "id", "failed", null),
                new DoclingTimeoutException("test", "op", 300),
                new ConversionMaterializationException("test")
        };

        for (DoclingClientException ex : exceptions) {
            // Verify each exception is an instance of DoclingClientException
            assertTrue(ex instanceof DoclingClientException);
            assertNotNull(ex.getMessage());
        }
    }
}
