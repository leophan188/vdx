package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Bình luận trên một công việc dự án (kiểu Jira). Tái dùng pattern {@code PostComment}:
 * tác giả + tên denorm, cờ {@code edited}. Khác bảng tin: KHÔNG giới hạn thời gian sửa —
 * sửa được bởi tác giả; xoá bởi tác giả HOẶC admin (kiểm ở service/controller).
 */
@Entity
@Table(name = "project_task_comment", indexes = {
        @Index(name = "ix_ptcmt_task", columnList = "task_id, created_at")
})
public class TaskComment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "author_id", length = 36, nullable = false)
    private String authorId;

    @Column(name = "author_name", length = 200, nullable = false)
    private String authorName;

    @Column(name = "body", length = 4000, nullable = false)
    private String body;

    @Column(name = "edited", nullable = false)
    private boolean edited;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskComment() {
    }

    public TaskComment(String taskId, String authorId, String authorName, String body) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.edited = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void edit(String newBody) {
        this.body = newBody;
        this.edited = true;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public boolean isEdited() { return edited; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
