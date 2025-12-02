package com.docling.client;

import com.docling.invoker.ApiClient;
import com.docling.model.ConvertDocumentResponse;
import com.docling.model.ExportDocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertResponseParsingTest {

    private ApiClient apiClient;

    @BeforeEach
    void setUpMapper() {
        apiClient = new ApiClient();
        var mapper = apiClient.getObjectMapper();
        DoclingClient.configureObjectMapper(mapper);
        apiClient.setObjectMapper(mapper);
        assertTrue(
                apiClient.getObjectMapper().getRegisteredModuleIds().contains("DoclingConvertDocumentResponseModule"),
                "Custom convert response module should be registered");
    }

    @Test
    void parsesLegacyDocumentField() throws Exception {
        String json = """
{
  \"document\": {
    \"filename\": \"sample.pdf\",
    \"md_content\": \"Hello\",
    \"json_content\": null,
    \"html_content\": null,
    \"text_content\": null,
    \"doctags_content\": null
  },
  \"status\": \"success\",
  \"errors\": [],
  \"processing_time\": 1.23,
  \"timings\": {}
}
""";
        ConvertDocumentResponse response = apiClient.getObjectMapper().readValue(json, ConvertDocumentResponse.class);
        ExportDocumentResponse doc = response.getDocument();
        assertNotNull(doc);
        assertEquals("sample.pdf", doc.getFilename());
    }

    @Test
    void hydratesDocumentsArrayPayloads() throws Exception {
        String json = """
{
  \"documents\": [{
    \"content\": {
      \"filename\": \"sample.pdf\",
      \"md_content\": \"Hello\",
      \"json_content\": null,
      \"html_content\": null,
      \"text_content\": null,
      \"doctags_content\": null
    }
  }],
  \"status\": \"success\",
  \"errors\": [],
  \"processing_time\": 1.23,
  \"timings\": {}
}
""";
        ConvertDocumentResponse response = apiClient.getObjectMapper().readValue(json, ConvertDocumentResponse.class);
        ExportDocumentResponse doc = response.getDocument();
        assertNotNull(doc, "Document should be populated from documents[0].content");
        assertEquals("sample.pdf", doc.getFilename());
        assertEquals("Hello", doc.getMdContent().getString());
    }
}
