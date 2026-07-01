package com.bpm.infrastructure;

import com.bpm.domain.hr.HrSheetConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrSheetConfigRepository extends JpaRepository<HrSheetConfig, String> {
}
