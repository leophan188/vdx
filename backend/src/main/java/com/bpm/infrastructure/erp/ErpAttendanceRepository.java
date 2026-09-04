package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.ErpAttendanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ErpAttendanceRepository extends JpaRepository<ErpAttendanceEntry, String> {

    List<ErpAttendanceEntry> findByPeriodKey(String periodKey);

    void deleteByPeriodKey(String periodKey);

    /** Các kỳ đã có dữ liệu, mới nhất trước — để màn hình liệt kê tháng đã tải. */
    @org.springframework.data.jpa.repository.Query(
            "select distinct e.periodKey from ErpAttendanceEntry e order by e.periodKey desc")
    List<String> findPeriods();
}
