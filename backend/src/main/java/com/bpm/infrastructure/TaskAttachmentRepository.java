package com.bpm.infrastructure;

import com.bpm.domain.project.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, String> {

    /** Ảnh đính kèm của một task, mới đính kèm → cũ (uploadedAt desc). */
    List<TaskAttachment> findByTaskIdOrderByUploadedAtDesc(String taskId);

    long countByTaskId(String taskId);

    void deleteByTaskId(String taskId);
}
