package com.bpm.infrastructure;

import com.bpm.domain.form.FormDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, String> {
    boolean existsByFormKey(String formKey);
}
