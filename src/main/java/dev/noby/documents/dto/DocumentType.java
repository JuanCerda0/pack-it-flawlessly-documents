package dev.noby.documents.dto;

public enum DocumentType {
    INTAKE("ING", "Comprobante de ingreso", "PREPARING"),
    DISPATCH("DES", "Documento de despacho", "READY");

    private final String prefix;
    private final String title;
    private final String expectedStatus;

    DocumentType(String prefix, String title, String expectedStatus) {
        this.prefix = prefix;
        this.title = title;
        this.expectedStatus = expectedStatus;
    }

    public String prefix() { return prefix; }
    public String title() { return title; }
    public String expectedStatus() { return expectedStatus; }
}
