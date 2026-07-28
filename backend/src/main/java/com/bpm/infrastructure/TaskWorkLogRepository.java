package com.bpm.infrastructure;

import com.bpm.domain.project.TaskWorkLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskWorkLogRepository extends JpaRepository<TaskWorkLog, String> {

    /** Giờ đã ghi trên một task (mới → cũ) — hiện trong chi tiết công việc. */
    List<TaskWorkLog> findByTaskIdOrderByWorkDateDescCreatedAtDesc(String taskId);

    /** Giờ của cả dự án trong khoảng ngày [from, to] — nguồn dựng timesheet. */
    List<TaskWorkLog> findByProjectIdAndWorkDateBetween(String projectId, LocalDate from, LocalDate to);

    /** Toàn bộ giờ của dự án — dùng cho thống kê tổng. */
    List<TaskWorkLog> findByProjectId(String projectId);

    void deleteByTaskId(String taskId);
}
