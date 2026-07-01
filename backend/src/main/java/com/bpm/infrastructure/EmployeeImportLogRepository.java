package com.bpm.infrastructure;

import com.bpm.domain.hr.EmployeeImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeImportLogRepository extends JpaRepository<EmployeeImportLog, String> {
    List<EmployeeImportLog> findTop50ByOrderByRunAtDesc();
}
