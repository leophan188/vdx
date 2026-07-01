package com.bpm.infrastructure;

import com.bpm.domain.report.ReportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lưu lịch sử lần chạy báo cáo (Epic 4, FR-D05). */
public interface ReportRunRepository extends JpaRepository<ReportRun, String> {

    /** Lịch sử mới nhất trước. */
    List<ReportRun> findAllByOrderByRunAtDesc();
}
