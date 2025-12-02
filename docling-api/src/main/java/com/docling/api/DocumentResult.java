package com.docling.api;

/**
 * Document conversion result.
 * Pure POJO matching ExportDocumentResponse from API.
 */
public class DocumentResult {
    private String filename;
    private String md_content;      // Markdown content
    private Object json_content;    // JSON content (DoclingDocument)

    // Legacy/convenience fields
    private String document;
    private String format;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMdContent() {
        return md_content;
    }

    public void setMdContent(String md_content) {
        this.md_content = md_content;
    }

    public Object getJsonContent() {
        return json_content;
    }

    public void setJsonContent(Object json_content) {
        this.json_content = json_content;
    }

    // Legacy support
    public String getDocument() {
        return document != null ? document : md_content;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
