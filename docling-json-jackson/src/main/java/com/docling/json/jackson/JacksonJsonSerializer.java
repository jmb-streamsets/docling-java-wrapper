package com.docling.json.jackson;

import com.docling.spi.JsonSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Jackson implementation of JsonSerializer.
 * Provides JSON serialization using the Jackson library.
 * <p>
 * This implementation is registered via ServiceLoader and will be
 * auto-discovered if Jackson is on the classpath.
 */
public class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper mapper;

    public JacksonJsonSerializer() {
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Handle snake_case field names from the Docling API (e.g., md_content, json_content)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // Ignore unknown properties in responses to be forward-compatible
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public JacksonJsonSerializer(ObjectMapper customMapper) {
        this.mapper = customMapper;
    }

    @Override
    public <T> String toJson(T object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            throw new JsonSerializationException("Failed to serialize to JSON", e);
        }
    }

    @Override
    public <T> void toJson(T object, OutputStream output) {
        try {
            mapper.writeValue(output, object);
        } catch (IOException e) {
            throw new JsonSerializationException("Failed to serialize to JSON", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new JsonSerializationException("Failed to deserialize from JSON", e);
        }
    }

    @Override
    public <T> T fromJson(InputStream input, Class<T> type) {
        try {
            return mapper.readValue(input, type);
        } catch (IOException e) {
            throw new JsonSerializationException("Failed to deserialize from JSON", e);
        }
    }

    @Override
    public String getName() {
        return "Jackson";
    }

    /**
     * Get the underlying ObjectMapper for advanced configuration.
     */
    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    /**
     * Exception thrown when JSON serialization/deserialization fails.
     */
    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
