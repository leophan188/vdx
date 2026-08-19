package com.bpm.infrastructure;

import com.bpm.domain.report.ReportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lưu lịch sử lần chạy báo cáo (Epic 4, FR-D05). */
public interface ReportRunRepository extends JpaRepository<ReportRun, String> {

    /** Lịch sử mới nhất trước (chỉ admin được xem toàn bộ). */
    List<ReportRun> findAllByOrderByRunAtDesc();

    /** Lịch sử của riêng một người import — người dùng thường chỉ thấy phần này. */
    List<ReportRun> findByRunByOrderByRunAtDesc(String runBy);
}
