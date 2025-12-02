package com.docling.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DoclingClient construction and validation.
 */
class DoclingClientTest {

    @Test
    void constructorRequiresNonNullBaseUrl() {
        assertThrows(NullPointerException.class, () ->
                new DoclingClient(null, "key", Duration.ofMinutes(1)));
    }

    @Test
    void constructorRequiresNonNullTimeout() {
        assertThrows(NullPointerException.class, () ->
                new DoclingClient("http://localhost:5001", "key", null));
    }

    @Test
    void constructorRequiresPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new DoclingClient("http://localhost:5001", "key", Duration.ZERO));

        assertThrows(IllegalArgumentException.class, () ->
                new DoclingClient("http://localhost:5001", "key", Duration.ofSeconds(-1)));
    }

    @Test
    void constructorAcceptsNullApiKey() {
        assertDoesNotThrow(() ->
                new DoclingClient("http://localhost:5001", null, Duration.ofMinutes(1)));
    }

    @Test
    void constructorSucceedsWithValidParameters() {
        DoclingClient client = new DoclingClient("http://localhost:5001", "api-key", Duration.ofMinutes(5));
        assertNotNull(client);
        assertEquals("http://localhost:5001", client.getBaseUrl());
        assertEquals("api-key", client.getApiKey());
    }

    @Test
    void fromEnvCreatesClientWithDefaults() {
        DoclingClient client = DoclingClient.fromEnv();
        assertNotNull(client);
        assertNotNull(client.getBaseUrl());
    }

    @Test
    void statusCheckMethodsWorkCorrectly() {
        assertTrue(DoclingClient.isSuccessStatus("done"));
        assertTrue(DoclingClient.isSuccessStatus("success"));
        assertTrue(DoclingClient.isSuccessStatus("succeeded"));
        assertTrue(DoclingClient.isSuccessStatus("completed"));
        assertTrue(DoclingClient.isSuccessStatus("DONE")); // case insensitive

        assertTrue(DoclingClient.isFailureStatus("error"));
        assertTrue(DoclingClient.isFailureStatus("failed"));
        assertTrue(DoclingClient.isFailureStatus("failure"));
        assertTrue(DoclingClient.isFailureStatus("ERROR")); // case insensitive

        assertFalse(DoclingClient.isSuccessStatus("pending"));
        assertFalse(DoclingClient.isSuccessStatus((String) null));
        assertFalse(DoclingClient.isFailureStatus("pending"));
        assertFalse(DoclingClient.isFailureStatus((String) null));
    }

    @Test
    void setBaseUrlUpdatesUrl() {
        DoclingClient client = new DoclingClient("http://localhost:5001", null, Duration.ofMinutes(1));
        client.setBaseUrl("http://localhost:9999");
        assertEquals("http://localhost:9999", client.getBaseUrl());
    }

    @Test
    void setApiKeyUpdatesKey() {
        DoclingClient client = new DoclingClient("http://localhost:5001", "old-key", Duration.ofMinutes(1));
        client.setApiKey("new-key");
        assertEquals("new-key", client.getApiKey());
    }

    @Test
    void metricsEnabledCanBeToggled() {
        DoclingClient client = new DoclingClient("http://localhost:5001", null, Duration.ofMinutes(1));
        assertFalse(client.isMetricsEnabled()); // default false

        client.setMetricsEnabled(true);
        assertTrue(client.isMetricsEnabled());

        client.setMetricsEnabled(false);
        assertFalse(client.isMetricsEnabled());
    }

    @Test
    void traceHttpCanBeToggled() {
        DoclingClient client = new DoclingClient("http://localhost:5001", null, Duration.ofMinutes(1));
        assertFalse(client.isTraceHttp()); // default false

        client.setTraceHttp(true);
        assertTrue(client.isTraceHttp());

        client.setTraceHttp(false);
        assertFalse(client.isTraceHttp());
    }
}
