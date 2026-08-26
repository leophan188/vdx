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

    /** Hành động sinh ra dòng giờ — cùng vai TEST nhưng ý nghĩa khác hẳn nhau. */
    public static final String ACT_LOG_BUG = "LOG_BUG";       // tester log lỗi mới
    public static final String ACT_HANDOVER = "HANDOVER";     // dev bàn giao sang Kiểm thử
    public static final String ACT_VERIFY_DONE = "VERIFY_DONE"; // chuyển Hoàn thành
    public static final String ACT_REOPEN = "REOPEN";         // kiểm thử chưa đạt, trả về sửa
    public static final String ACT_MANUAL = "MANUAL";         // ghi giờ tay

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

    /**
     * HÀNH ĐỘNG sinh ra dòng giờ này. Chỉ nhìn `role` thì không phân biệt được tester đang
     * LOG LỖI MỚI, TRẢ VỀ SỬA hay DUYỆT XONG — cả ba đều là vai TEST.
     * Kiểu String (không phải enum nguyên thuỷ) nên bản ghi cũ mang NULL vẫn đọc được bình thường.
     */
    @Column(name = "action", length = 20)
    private String action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    protected TaskWorkLog() {
    }

    public TaskWorkLog(String projectId, String taskId, String userId, String userName,
                       String role, LocalDate workDate, double hours, String note, String createdBy) {
        this(projectId, taskId, userId, userName, role, workDate, hours, note, createdBy, null);
    }

    public TaskWorkLog(String projectId, String taskId, String userId, String userName,
                       String role, LocalDate workDate, double hours, String note, String createdBy,
                       String action) {
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
        this.action = action;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTaskId() { return taskId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }

    /** Nắn lại người của dòng giờ khi ghi nhầm (xem ProjectTaskService#changeWorkLogUser). */
    public void reassignTo(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }
    public String getRole() { return role; }
    public LocalDate getWorkDate() { return workDate; }
    public double getHours() { return hours; }
    public String getNote() { return note; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
