package com.docling.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BufferedDoclingClient record validation.
 */
class BufferedDoclingClientTest {

    @Test
    void constructorRequiresNonNullDelegate() {
        assertThrows(NullPointerException.class, () ->
                new BufferedDoclingClient(null));
    }

    @Test
    void constructorSucceedsWithValidDelegate() {
        DoclingClient delegate = new DoclingClient("http://localhost:5001", null, Duration.ofMinutes(1));
        BufferedDoclingClient client = new BufferedDoclingClient(delegate);
        assertNotNull(client);
        assertEquals(delegate, client.delegate());
    }

    @Test
    void constructorWithParametersCreatesClient() {
        BufferedDoclingClient client = new BufferedDoclingClient("http://localhost:5001", "key", Duration.ofMinutes(2));
        assertNotNull(client);
        assertNotNull(client.delegate());
        assertEquals("http://localhost:5001", client.delegate().getBaseUrl());
    }

    @Test
    void fromEnvCreatesClientWithDefaults() {
        BufferedDoclingClient client = BufferedDoclingClient.fromEnv();
        assertNotNull(client);
        assertNotNull(client.delegate());
    }
}
