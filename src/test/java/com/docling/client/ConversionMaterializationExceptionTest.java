package com.docling.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionMaterializationException.
 */
class ConversionMaterializationExceptionTest {

    @Test
    void constructorSetsMessage() {
        String message = "Conversion failed";
        ConversionMaterializationException ex = new ConversionMaterializationException(message);
        assertEquals(message, ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        ConversionMaterializationException ex = new ConversionMaterializationException("test");
        assertTrue(ex instanceof RuntimeException);
    }
}
