package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch sử / dòng hoạt động của một công việc dự án (kiểu Jira "Activity log").
 * Mỗi mutation đáng kể trên task sinh một bản ghi bất biến (append-only): tạo, đổi trạng thái,
 * gán người, sửa, bình luận, đính kèm ảnh, log work. {@code actorName} là tên hiển thị người thao tác
 * (denorm để hiển thị nhanh). {@code detail} mô tả ngắn (vd "TODO → DONE", "+2.5h").
 */
@Entity
@Table(name = "project_task_activity", indexes = {
        @Index(name = "ix_ptact_task", columnList = "task_id, created_at")
})
public class TaskActivity {

    /** Hành động ghi nhận trên task. */
    public static final String CREATED = "CREATED";
    public static final String STATUS = "STATUS";
    public static final String ASSIGN = "ASSIGN";
    public static final String EDIT = "EDIT";
    public static final String COMMENT = "COMMENT";
    public static final String ATTACH = "ATTACH";
    public static final String SPENT = "SPENT";

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    /** Tên hiển thị người thao tác (denorm). */
    @Column(name = "actor_name", length = 200, nullable = false)
    private String actorName;

    /** CREATED / STATUS / ASSIGN / EDIT / COMMENT / ATTACH / SPENT. */
    @Column(name = "action", length = 20, nullable = false)
    private String action;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskActivity() {
    }

    public TaskActivity(String taskId, String projectId, String actorName, String action, String detail) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.projectId = projectId;
        this.actorName = actorName;
        this.action = action;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getProjectId() { return projectId; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
