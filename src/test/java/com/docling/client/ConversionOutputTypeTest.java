package com.docling.client;

import com.docling.model.OutputFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionOutputType enum.
 */
class ConversionOutputTypeTest {

    @Test
    void parseRecognizesDoclingJsonAliases() {
        assertEquals(ConversionOutputType.DOCLING_JSON, ConversionOutputType.parse("docling"));
        assertEquals(ConversionOutputType.DOCLING_JSON, ConversionOutputType.parse("docling-json"));
        assertEquals(ConversionOutputType.DOCLING_JSON, ConversionOutputType.parse("json"));
        assertEquals(ConversionOutputType.DOCLING_JSON, ConversionOutputType.parse("docling_document"));
        assertEquals(ConversionOutputType.DOCLING_JSON, ConversionOutputType.parse("DOCLING")); // case insensitive
    }

    @Test
    void parseRecognizesMarkdownAliases() {
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.parse("markdown"));
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.parse("md"));
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.parse("MD")); // case insensitive
    }

    @Test
    void parseRecognizesHtmlAliases() {
        assertEquals(ConversionOutputType.HTML, ConversionOutputType.parse("html"));
        assertEquals(ConversionOutputType.HTML_SPLIT_PAGE, ConversionOutputType.parse("html_split_page"));
        assertEquals(ConversionOutputType.HTML_SPLIT_PAGE, ConversionOutputType.parse("HTML-SPLIT"));
    }

    @Test
    void parseRecognizesTextAndDoctagsAliases() {
        assertEquals(ConversionOutputType.TEXT, ConversionOutputType.parse("text"));
        assertEquals(ConversionOutputType.TEXT, ConversionOutputType.parse("TXT"));
        assertEquals(ConversionOutputType.DOCTAGS, ConversionOutputType.parse("doctags"));
        assertEquals(ConversionOutputType.DOCTAGS, ConversionOutputType.parse("doc-tags"));
    }

    @Test
    void parseThrowsOnInvalidType() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ConversionOutputType.parse("invalid"));
        assertTrue(ex.getMessage().contains("Unsupported output type"));
        assertTrue(ex.getMessage().contains("invalid"));
    }

    @Test
    void fromNullableReturnsDefaultForNull() {
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.fromNullable(null));
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.fromNullable(""));
        assertEquals(ConversionOutputType.MARKDOWN, ConversionOutputType.fromNullable("  "));
    }

    @Test
    void fromNullableReturnsCustomDefault() {
        assertEquals(ConversionOutputType.DOCLING_JSON,
                ConversionOutputType.fromNullable(null, ConversionOutputType.DOCLING_JSON));
    }

    @Test
    void supportedValuesReturnsCommaSeparatedList() {
        String supported = ConversionOutputType.supportedValues();
        assertNotNull(supported);
        assertTrue(supported.contains("docling"));
        assertTrue(supported.contains("markdown"));
        assertTrue(supported.contains("html"));
        assertTrue(supported.contains("html_split_page"));
        assertTrue(supported.contains("text"));
        assertTrue(supported.contains(", ")); // comma separator
    }

    @Test
    void doclingJsonHasCorrectProperties() {
        ConversionOutputType type = ConversionOutputType.DOCLING_JSON;
        assertEquals("docling", type.getPrimaryToken());
        assertEquals(OutputFormat.JSON, type.getOutputFormat());
        assertEquals(".docling.json", type.getFileExtension());
    }

    @Test
    void markdownHasCorrectProperties() {
        ConversionOutputType type = ConversionOutputType.MARKDOWN;
        assertEquals("markdown", type.getPrimaryToken());
        assertEquals(OutputFormat.MD, type.getOutputFormat());
        assertEquals(".md", type.getFileExtension());
    }

    @Test
    void htmlAndSplitHaveCorrectProperties() {
        ConversionOutputType html = ConversionOutputType.HTML;
        assertEquals("html", html.getPrimaryToken());
        assertEquals(OutputFormat.HTML, html.getOutputFormat());
        assertEquals(".html", html.getFileExtension());

        ConversionOutputType split = ConversionOutputType.HTML_SPLIT_PAGE;
        assertEquals("html_split_page", split.getPrimaryToken());
        assertEquals(OutputFormat.HTML_SPLIT_PAGE, split.getOutputFormat());
        assertEquals(".html_split", split.getFileExtension());
    }

    @Test
    void textAndDoctagsHaveCorrectProperties() {
        ConversionOutputType text = ConversionOutputType.TEXT;
        assertEquals("text", text.getPrimaryToken());
        assertEquals(OutputFormat.TEXT, text.getOutputFormat());
        assertEquals(".txt", text.getFileExtension());

        ConversionOutputType doctags = ConversionOutputType.DOCTAGS;
        assertEquals("doctags", doctags.getPrimaryToken());
        assertEquals(OutputFormat.DOCTAGS, doctags.getOutputFormat());
        assertEquals(".doctags", doctags.getFileExtension());
    }
}
