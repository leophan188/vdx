package com.bpm.infrastructure;

import com.bpm.domain.process.ProcessStatus;
import com.bpm.domain.process.ProcessVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessVersionRepository extends JpaRepository<ProcessVersion, String> {
    List<ProcessVersion> findByProcessIdOrderByVersionDesc(String processId);
    Optional<ProcessVersion> findFirstByProcessIdAndStatusOrderByVersionDesc(String processId, ProcessStatus status);
}
