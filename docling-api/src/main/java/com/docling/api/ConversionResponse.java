package com.docling.api;

import java.util.List;

/**
 * Pure domain model for conversion response.
 * No JSON annotations - serialization is handled by adapters.
 * Matches the ConvertDocumentResponse from Docling API.
 */
public class ConversionResponse {
    private DocumentResult document;
    private String status;
    private List<String> errors;
    private Double processing_time;  // Match API field name

    // Legacy fields for backward compatibility
    private String taskId;

    public DocumentResult getDocument() {
        return document;
    }

    public void setDocument(DocumentResult document) {
        this.document = document;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public Double getProcessingTime() {
        return processing_time;
    }

    public void setProcessingTime(Double processing_time) {
        this.processing_time = processing_time;
    }

    // Legacy support
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * Alias for getDocument() for backward compatibility.
     */
    public DocumentResult getResult() {
        return document;
    }

    public void setResult(DocumentResult result) {
        this.document = result;
    }
}
