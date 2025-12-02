package com.docling.client;

import com.docling.invoker.ApiClient;
import com.docling.model.ConvertDocumentResponse;
import com.docling.model.ExportDocumentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConvertDocumentResponseModule with depth limits.
 */
class ConvertDocumentResponseModuleTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        ApiClient apiClient = new ApiClient();
        mapper = apiClient.getObjectMapper();
        DoclingClient.configureObjectMapper(mapper);
    }

    @Test
    void handlesLegacyDocumentField() throws Exception {
        String json = """
                {
                  "document": {
                    "filename": "test.pdf",
                    "md_content": "# Test"
                  },
                  "status": "success"
                }
                """;
        ConvertDocumentResponse response = mapper.readValue(json, ConvertDocumentResponse.class);
        assertNotNull(response.getDocument());
        assertEquals("test.pdf", response.getDocument().getFilename());
    }

    @Test
    void hydratesFromNestedDocuments() throws Exception {
        String json = """
                {
                  "documents": [{
                    "content": {
                      "filename": "test.pdf",
                      "md_content": "# Test"
                    }
                  }],
                  "status": "success"
                }
                """;
        ConvertDocumentResponse response = mapper.readValue(json, ConvertDocumentResponse.class);
        ExportDocumentResponse doc = response.getDocument();
        assertNotNull(doc, "Document should be hydrated from nested structure");
        assertEquals("test.pdf", doc.getFilename());
    }

    @Test
    void handlesEmptyDocument() throws Exception {
        String json = """
                {
                  "status": "success"
                }
                """;
        ConvertDocumentResponse response = mapper.readValue(json, ConvertDocumentResponse.class);
        // Should not throw, document may be null
        assertNotNull(response);
    }

    @Test
    void handlesDeeplyNestedStructures() throws Exception {
        // Create a deeply nested JSON to test depth limit
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < 50; i++) {
            json.append("\"level").append(i).append("\": {");
        }
        json.append("\"md_content\": \"test\"");
        for (int i = 0; i < 50; i++) {
            json.append("}");
        }
        json.append(", \"status\": \"success\"}");

        // Should not stack overflow due to depth limit
        assertDoesNotThrow(() -> {
            ConvertDocumentResponse response = mapper.readValue(json.toString(), ConvertDocumentResponse.class);
            assertNotNull(response);
        });
    }
}
