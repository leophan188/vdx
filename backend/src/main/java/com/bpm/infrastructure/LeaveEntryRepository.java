package com.bpm.infrastructure;

import com.bpm.domain.leave.LeaveEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveEntryRepository extends JpaRepository<LeaveEntry, String> {

    /** Đăng ký nghỉ của một user, từ ngày mới nhất trước (tab "Nghỉ của tôi"). */
    List<LeaveEntry> findByUserIdOrderByFromDateDesc(String userId);

    /** Các đăng ký GIAO với khoảng [from,to]: fromDate <= to AND toDate >= from. */
    List<LeaveEntry> findByFromDateLessThanEqualAndToDateGreaterThanEqual(LocalDate to, LocalDate from);
}
