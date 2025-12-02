package com.docling.spi;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Service Provider Interface for JSON serialization.
 * <p>
 * Implementations provide pluggable JSON serialization using different libraries
 * (Jackson, Gson, Moshi, etc.).
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * Register your implementation in META-INF/services/com.docling.spi.JsonSerializer
 *
 * @see java.util.ServiceLoader
 */
public interface JsonSerializer {

    /**
     * Serialize object to JSON string.
     *
     * @param object the object to serialize
     * @param <T>    object type
     * @return JSON string representation
     */
    <T> String toJson(T object);

    /**
     * Serialize object to JSON output stream.
     *
     * @param object the object to serialize
     * @param output the output stream to write to
     * @param <T>    object type
     */
    <T> void toJson(T object, OutputStream output);

    /**
     * Deserialize JSON string to object.
     *
     * @param json JSON string
     * @param type target class
     * @param <T>  target type
     * @return deserialized object
     */
    <T> T fromJson(String json, Class<T> type);

    /**
     * Deserialize JSON stream to object.
     *
     * @param input JSON input stream
     * @param type  target class
     * @param <T>   target type
     * @return deserialized object
     */
    <T> T fromJson(InputStream input, Class<T> type);

    /**
     * Get the name of this serializer implementation (for debugging/logging).
     *
     * @return serializer name (e.g., "Jackson", "Gson", "Moshi")
     */
    String getName();
}
