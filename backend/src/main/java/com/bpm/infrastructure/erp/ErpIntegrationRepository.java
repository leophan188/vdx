package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.ErpIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpIntegrationRepository extends JpaRepository<ErpIntegration, String> {
}
