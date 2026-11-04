package dev.noby.documents.exception;

public class InvalidDocumentJobException extends RuntimeException {
    public InvalidDocumentJobException(String message) { super(message); }
    public InvalidDocumentJobException(String message, Throwable cause) { super(message, cause); }
}
