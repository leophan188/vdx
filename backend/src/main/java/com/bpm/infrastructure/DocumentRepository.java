package com.bpm.infrastructure;

import com.bpm.domain.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByInstanceId(String instanceId);
    Optional<Document> findFirstByInstanceIdAndStepKey(String instanceId, String stepKey);
    List<Document> findAllByOrderByUpdatedAtDesc();
}
