package dev.noby.documents.controller;

import dev.noby.documents.dto.DocumentFile;
import dev.noby.documents.dto.DocumentSummary;
import dev.noby.documents.service.DocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping("/packages/{packageId}/documents")
    public List<DocumentSummary> list(@PathVariable Long packageId) {
        if (packageId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "packageId debe ser positivo");
        }
        return service.listByPackage(packageId);
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id debe ser positivo");
        }
        DocumentFile file = service.getFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.documentNumber() + ".pdf").build().toString())
                .body(file.pdfData());
    }
}
