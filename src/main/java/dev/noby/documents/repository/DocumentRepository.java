package dev.noby.documents.repository;

import dev.noby.documents.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    boolean existsByEventId(UUID eventId);
    List<DocumentEntity> findByPackageIdOrderByGeneratedAtAscIdAsc(Long packageId);
}
