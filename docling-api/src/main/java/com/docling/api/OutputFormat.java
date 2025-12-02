package com.docling.api;

/**
 * Output format for document conversion.
 * Pure domain enum - no serialization annotations.
 */
public enum OutputFormat {
    MARKDOWN("md"),
    JSON("json"),
    HTML("html"),
    HTML_SPLIT_PAGE("html_split_page"),
    TEXT("text"),
    DOCTAGS("doctags");

    private final String value;

    OutputFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OutputFormat fromValue(String value) {
        for (OutputFormat format : values()) {
            if (format.value.equals(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown output format: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
