package dev.noby.documents.service;

import dev.noby.documents.dto.DocumentFile;
import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.DocumentSummary;
import dev.noby.documents.dto.DocumentType;
import dev.noby.documents.dto.PackageItemSnapshot;
import dev.noby.documents.dto.PackageSnapshot;
import dev.noby.documents.entity.DocumentEntity;
import dev.noby.documents.exception.DocumentNotFoundException;
import dev.noby.documents.exception.InvalidDocumentJobException;
import dev.noby.documents.mapper.DocumentMapper;
import dev.noby.documents.repository.DocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final PdfDocumentGenerator generator;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public DocumentService(DocumentRepository repository, DocumentMapper mapper, PdfDocumentGenerator generator,
                           JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.generator = generator;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public DocumentJob parse(byte[] body, DocumentType expectedType) {
        DocumentJob job;
        try {
            job = jsonMapper.readValue(new String(body, StandardCharsets.UTF_8), DocumentJob.class);
        } catch (JacksonException error) {
            throw new InvalidDocumentJobException("El mensaje no cumple el contrato JSON de Documentos", error);
        }
        validate(job, expectedType);
        return job;
    }

    @Transactional
    public void record(DocumentJob job) {
        if (repository.existsByEventId(job.eventId())) {
            return;
        }
        Long id = jdbcTemplate.queryForObject("SELECT nextval('documents_id_seq')", Long.class);
        String number = job.documentType().prefix() + "-" + id;
        Instant generatedAt = Instant.now();
        byte[] pdf = generator.generate(job, number, generatedAt);
        repository.saveAndFlush(new DocumentEntity(id, job.eventId(), job.packageId(), job.documentType(),
                number, job.occurredAt(), generatedAt, snapshot(job.packageSnapshot()), pdf));
    }

    public List<DocumentSummary> listByPackage(Long packageId) {
        return repository.findByPackageIdOrderByGeneratedAtAscIdAsc(packageId)
                .stream().map(mapper::toSummary).toList();
    }

    public DocumentFile getFile(Long id) {
        DocumentEntity document = repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        return new DocumentFile(document.getDocumentNumber(), document.getPdfData());
    }

    private Map<String, Object> snapshot(PackageSnapshot source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", source.name());
        result.put("reference", source.reference());
        result.put("status", source.status());
        List<Map<String, Object>> items = new ArrayList<>();
        for (PackageItemSnapshot item : source.items()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("sku", item.sku());
            values.put("productName", item.productName());
            values.put("quantity", item.quantity());
            items.add(values);
        }
        result.put("items", items);
        return result;
    }

    private void validate(DocumentJob job, DocumentType expectedType) {
        if (job == null || job.schemaVersion() != 1 || job.eventId() == null
                || job.occurredAt() == null || job.packageId() == null || job.packageId() <= 0
                || job.documentType() != expectedType || job.packageSnapshot() == null) {
            throw new InvalidDocumentJobException("Faltan campos requeridos, tipo incorrecto o schemaVersion no es 1");
        }
        PackageSnapshot data = job.packageSnapshot();
        if (data.name() == null || data.name().isBlank() || data.name().length() > 120
                || (data.reference() != null && data.reference().length() > 80)
                || !expectedType.expectedStatus().equals(data.status()) || data.items() == null) {
            throw new InvalidDocumentJobException("La instantánea del paquete es inválida");
        }
        for (PackageItemSnapshot item : data.items()) {
            if (item == null || (item.sku() != null && item.sku().length() > 80)
                    || item.productName() == null || item.productName().isBlank()
                    || item.productName().length() > 160 || item.quantity() <= 0) {
                throw new InvalidDocumentJobException("La instantánea contiene un producto inválido");
            }
        }
    }
}
