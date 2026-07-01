package com.bpm.infrastructure;

import com.bpm.domain.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByInstanceId(String instanceId);
    List<Document> findAllByOrderByUpdatedAtDesc();
}
