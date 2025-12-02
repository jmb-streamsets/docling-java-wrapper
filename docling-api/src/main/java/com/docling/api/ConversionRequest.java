package com.docling.api;

import java.util.List;

/**
 * Pure domain model for conversion request.
 * No JSON annotations - serialization is handled by adapters.
 */
public class ConversionRequest {
    private List<String> sources;
    private List<String> toFormats;
    private ConversionOptions options;

    public ConversionRequest() {
    }

    public ConversionRequest(List<String> sources, List<String> toFormats) {
        this.sources = sources;
        this.toFormats = toFormats;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public List<String> getToFormats() {
        return toFormats;
    }

    public void setToFormats(List<String> toFormats) {
        this.toFormats = toFormats;
    }

    public ConversionOptions getOptions() {
        return options;
    }

    public void setOptions(ConversionOptions options) {
        this.options = options;
    }
}
