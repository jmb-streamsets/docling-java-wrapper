package com.docling.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Target output kind for Docling conversions.
 */
public enum TargetName {
    INBODY("inbody"),
    ZIP("zip");

    private final String value;

    TargetName(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @JsonCreator
    public static TargetName fromValue(String value) {
        for (TargetName v : TargetName.values()) {
            if (v.value.equals(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}
