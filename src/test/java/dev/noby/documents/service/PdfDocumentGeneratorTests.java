package dev.noby.documents.service;

import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.DocumentType;
import dev.noby.documents.dto.PackageItemSnapshot;
import dev.noby.documents.dto.PackageSnapshot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentGeneratorTests {

    @Test
    void keepsAllProductsWhenPdfNeedsMoreThanOnePage() throws IOException {
        List<PackageItemSnapshot> items = IntStream.rangeClosed(1, 90)
                .mapToObj(index -> new PackageItemSnapshot("SKU-" + index, "Producto " + index, index))
                .toList();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        DocumentJob job = new DocumentJob(UUID.randomUUID(), 1, now, 42L, DocumentType.DISPATCH,
                new PackageSnapshot("Paquete grande", "PF-42", "READY", items));

        byte[] bytes = new PdfDocumentGenerator().generate(job, "DES-90", now);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("SKU-1", "SKU-90", "Producto 90", "DES-90");
        }
    }
}
