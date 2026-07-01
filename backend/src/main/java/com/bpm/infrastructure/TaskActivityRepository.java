package com.bpm.infrastructure;

import com.bpm.domain.project.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, String> {

    /** Hoạt động của một task, mới → cũ (kiểu Jira). */
    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(String taskId);

    void deleteByTaskId(String taskId);
}
