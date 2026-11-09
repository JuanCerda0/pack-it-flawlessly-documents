package dev.noby.documents.service;

import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.PackageItemSnapshot;
import dev.noby.documents.exception.DocumentGenerationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfDocumentGenerator {

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public byte[] generate(DocumentJob job, String number, Instant generatedAt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PageWriter page = new PageWriter(document);
            page.newPage();
            page.text(job.documentType().title(), 18, BOLD, 48, 510);
            page.y -= 12;
            page.text("Numero: " + number, 11, REGULAR, 48, 510);
            page.text("Paquete ID: " + job.packageId(), 11, REGULAR, 48, 510);
            page.text("Generado: " + DATE_FORMAT.format(generatedAt), 11, REGULAR, 48, 510);
            page.text("Estado: " + job.packageSnapshot().status(), 11, REGULAR, 48, 510);
            for (String line : wrap("Nombre: " + job.packageSnapshot().name(), 510, REGULAR, 11)) {
                page.text(line, 11, REGULAR, 48, 510);
            }
            for (String line : wrap("Referencia: " + value(job.packageSnapshot().reference()), 510, REGULAR, 11)) {
                page.text(line, 11, REGULAR, 48, 510);
            }
            page.y -= 14;
            page.tableHeader();

            if (job.packageSnapshot().items().isEmpty()) {
                page.text("Sin productos registrados al emitir este documento.", 10, REGULAR, 48, 510);
            } else {
                for (PackageItemSnapshot item : job.packageSnapshot().items()) {
                    List<String> sku = wrap(value(item.sku()), 110, REGULAR, 10);
                    List<String> name = wrap(item.productName(), 290, REGULAR, 10);
                    int lines = Math.max(sku.size(), name.size());
                    if (page.y - lines * 15 < 58) {
                        page.newPage();
                        page.text(job.documentType().title() + " " + number + " (continuacion)",
                                13, BOLD, 48, 510);
                        page.y -= 12;
                        page.tableHeader();
                    }
                    for (int index = 0; index < lines; index++) {
                        if (index < sku.size()) {
                            page.draw(sku.get(index), 10, REGULAR, 48);
                        }
                        if (index < name.size()) {
                            page.draw(name.get(index), 10, REGULAR, 170);
                        }
                        if (index == 0) {
                            page.draw(Integer.toString(item.quantity()), 10, REGULAR, 480);
                        }
                        page.y -= 15;
                    }
                    page.y -= 4;
                }
            }
            page.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new DocumentGenerationException("No se pudo generar el PDF", error);
        }
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String printable(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static List<String> wrap(String input, float maxWidth, PDType1Font font, int size) throws IOException {
        String text = printable(input);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char character : text.toCharArray()) {
            String candidate = current.toString() + character;
            if (!current.isEmpty() && font.getStringWidth(candidate) * size / 1000f > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("-") : lines;
    }

    private static class PageWriter {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private PageWriter(PDDocument document) { this.document = document; }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 790;
        }

        private void text(String text, int size, PDType1Font font, float x, float width) throws IOException {
            for (String line : wrap(text, width, font, size)) {
                draw(line, size, font, x);
                y -= size + 6;
            }
        }

        private void draw(String text, int size, PDType1Font font, float x) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }

        private void tableHeader() throws IOException {
            draw("SKU", 10, BOLD, 48);
            draw("Producto", 10, BOLD, 170);
            draw("Cantidad", 10, BOLD, 480);
            y -= 20;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
