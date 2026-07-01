package com.bpm.infrastructure;

import com.bpm.domain.document.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, String> {
    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(String documentId);
}
