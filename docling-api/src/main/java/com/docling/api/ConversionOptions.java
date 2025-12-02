package com.docling.api;

/**
 * Options for document conversion.
 * Pure POJO - no annotations.
 */
public class ConversionOptions {
    private String ocrEngine;
    private String pdfBackend;
    private Boolean forceOcr;

    public String getOcrEngine() {
        return ocrEngine;
    }

    public void setOcrEngine(String ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    public String getPdfBackend() {
        return pdfBackend;
    }

    public void setPdfBackend(String pdfBackend) {
        this.pdfBackend = pdfBackend;
    }

    public Boolean getForceOcr() {
        return forceOcr;
    }

    public void setForceOcr(Boolean forceOcr) {
        this.forceOcr = forceOcr;
    }
}
