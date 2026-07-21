package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Ảnh đính kèm một công việc dự án (kiểu Jira). File NHỊ PHÂN lưu trên ĐĨA qua
 * {@link com.bpm.application.MediaStorageService} (tái dùng Epic 2); DB chỉ giữ metadata +
 * {@code storageKey} = id của {@code PostMedia} đã lưu (đường dẫn thực do MediaStorageService quản lý).
 * Phục vụ bytes qua endpoint .../attachments/{attId}/content (có phiên).
 */
@Entity
@Table(name = "project_task_attachment", indexes = {
        @Index(name = "ix_ptatt_task", columnList = "task_id, uploaded_at")
})
public class TaskAttachment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    /** Khóa lưu trữ = id của PostMedia (MediaStorageService) — đường dẫn thực được giải qua storage. */
    @Column(name = "storage_key", length = 36, nullable = false)
    private String storageKey;

    /**
     * Ảnh này thuộc BÌNH LUẬN nào (null = ảnh đính kèm chung của task).
     * NULLABLE để bản ghi cũ vẫn hợp lệ dưới ddl-update.
     */
    @Column(name = "comment_id", length = 36)
    private String commentId;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected TaskAttachment() {
    }

    public TaskAttachment(String taskId, String fileName, String contentType, long size,
                          String storageKey, String uploadedBy) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getStorageKey() { return storageKey; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
}
