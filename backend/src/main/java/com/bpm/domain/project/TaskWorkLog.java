package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GIỜ LÀM VIỆC THỰC TẾ trên một task — nền của timesheet.
 *
 * <p>Trước đây hệ thống chỉ cộng dồn vào một ô {@code ProjectTask.spentHours} duy nhất nên không
 * biết AI làm, NGÀY NÀO, và với vai gì → không chấm công được. Mỗi bản ghi ở đây là một lần ghi
 * giờ độc lập, nhiều dòng trên cùng một task/người là bình thường (task bị trả về test lại nhiều
 * lượt, hoặc dev ghi giờ từng ngày).
 *
 * <p>{@code workDate} là NGÀY ĐƯỢC TÍNH CÔNG, tách khỏi {@code createdAt} (lúc bấm ghi): làm hôm
 * qua nhưng sáng nay mới ghi thì công vẫn thuộc hôm qua.
 *
 * <p>Quy ước ddl-update: mọi cột mới đều nullable trừ khoá và các trường bắt buộc.
 */
@Entity
@Table(name = "project_task_work_log", indexes = {
        @Index(name = "ix_ptwl_project_date", columnList = "project_id, work_date"),
        @Index(name = "ix_ptwl_task", columnList = "task_id"),
        @Index(name = "ix_ptwl_user_date", columnList = "user_id, work_date")
})
public class TaskWorkLog {

    /** Vai khi bỏ công: lập trình hay kiểm thử — để tách giờ dev và giờ test của cùng một task. */
    public static final String ROLE_DEV = "DEV";
    public static final String ROLE_TEST = "TEST";

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    /** Người bỏ công (UserAccount id). */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** Tên hiển thị (denorm) để dựng timesheet không phải join lại. */
    @Column(name = "user_name", length = 200)
    private String userName;

    /** DEV / TEST. */
    @Column(name = "role", length = 10, nullable = false)
    private String role;

    /** Ngày được tính công (không phải ngày bấm ghi). */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false)
    private double hours;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    protected TaskWorkLog() {
    }

    public TaskWorkLog(String projectId, String taskId, String userId, String userName,
                       String role, LocalDate workDate, double hours, String note, String createdBy) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.taskId = taskId;
        this.userId = userId;
        this.userName = userName;
        this.role = role;
        this.workDate = workDate;
        this.hours = hours;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTaskId() { return taskId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getRole() { return role; }
    public LocalDate getWorkDate() { return workDate; }
    public double getHours() { return hours; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
