package com.bpm.infrastructure;

import com.bpm.domain.process.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, String> {
    boolean existsByProcessKey(String processKey);
}
