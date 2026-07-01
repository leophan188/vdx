package com.bpm.infrastructure;

import com.bpm.domain.form.FormVersion;
import com.bpm.domain.process.ProcessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormVersionRepository extends JpaRepository<FormVersion, String> {
    List<FormVersion> findByFormIdOrderByVersionDesc(String formId);
    Optional<FormVersion> findFirstByFormIdAndStatusOrderByVersionDesc(String formId, ProcessStatus status);
}
