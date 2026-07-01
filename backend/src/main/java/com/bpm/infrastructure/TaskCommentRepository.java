package com.bpm.infrastructure;

import com.bpm.domain.project.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, String> {

    /** Bình luận của một task, cũ → mới. */
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(String taskId);

    long countByTaskId(String taskId);

    void deleteByTaskId(String taskId);
}
