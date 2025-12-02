package com.docling.model;

/**
 * Simple wrapper used by generated chunking endpoints.
 */
public class ChunkingMaxTokens {
    private Integer value;

    public ChunkingMaxTokens() {
    }

    public ChunkingMaxTokens(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public ChunkingMaxTokens setValue(Integer value) {
        this.value = value;
        return this;
    }

    @Override
    public String toString() {
        return value == null ? "" : value.toString();
    }

    public String toUrlQueryString(String prefix) {
        if (value == null) {
            return "";
        }
        if (prefix == null) {
            prefix = "";
        }
        return prefix + "=" + value;
    }
}
