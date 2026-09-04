package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.ErpConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpConfigRepository extends JpaRepository<ErpConfig, String> {
}
