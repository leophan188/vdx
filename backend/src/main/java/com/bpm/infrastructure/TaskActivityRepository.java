package com.bpm.infrastructure;

import com.bpm.domain.project.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, String> {

    /** Hoạt động của một task, mới → cũ (kiểu Jira). */
    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(String taskId);

    /** Nhật ký hoạt động toàn dự án (mới → cũ), giới hạn 300 dòng gần nhất. */
    List<TaskActivity> findTop300ByProjectIdOrderByCreatedAtDesc(String projectId);

    /**
     * Hoạt động của dự án trong khoảng [from, to) — dùng cho báo cáo "xử lý trong kỳ".
     * Lọc theo THỜI ĐIỂM HOẠT ĐỘNG chứ không theo updated_at của task, vì lúc TẠO task
     * thì updated_at = created_at nên việc vừa log cũng bị tính là đã xử lý.
     */
    List<TaskActivity> findByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String projectId, java.time.Instant from, java.time.Instant to);

    void deleteByTaskId(String taskId);
}
