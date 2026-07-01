package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Công việc trong dự án — ĐA CẤP cha-con nhiều tầng ({@code parentId} nullable).
 * Code hiển thị = "CODE-seq" (vd "PRJ-12") sinh từ {@code Project.nextSeq()}.
 * "Task lá" = task không có con; dùng cho tính tỷ lệ hoàn thành (theo estimateHours).
 */
@Entity
@Table(name = "project_task", indexes = {
        @Index(name = "ix_project_task_project", columnList = "project_id"),
        @Index(name = "ix_project_task_parent", columnList = "parent_id")
})
public class ProjectTask {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    /** Cha (nullable = task gốc). */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    /** Số tăng dần trong dự án (sinh code "CODE-seq"). */
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private TaskType type = TaskType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TaskStatus status = TaskStatus.BACKLOG;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20, nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;

    /** Người thực hiện (UserAccount id, nullable). */
    @Column(name = "assignee_user_id", length = 36)
    private String assigneeUserId;

    /** Người LOG/tạo công việc (UserAccount id, nullable — đặc biệt quan trọng cho BUG/ISSUE). */
    @Column(name = "reporter_user_id", length = 36)
    private String reporterUserId;

    /** Người KIỂM THỬ (UserAccount id, nullable). Khi task (không phải BUG/ISSUE) chuyển Kiểm thử → bàn giao cho người này. */
    @Column(name = "tester_user_id", length = 36)
    private String testerUserId;

    /** Ước lượng thời gian (giờ). */
    @Column(name = "estimate_hours")
    private double estimateHours;

    /** Thời gian thực tế đã bỏ ra (giờ) — cộng dồn qua log-work. Mặc định 0. */
    @Column(name = "spent_hours", nullable = false, columnDefinition = "double default 0")
    private double spentHours = 0;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Thứ tự sắp xếp trong cùng cấp. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** Màn hình liên quan (cho BUG/ISSUE). */
    @Column(name = "screen", length = 300)
    private String screen;

    // ===== Chi tiết lỗi (chỉ dùng cho BUG/ISSUE) — TẤT CẢ NULLABLE (tránh lỗi H2 ddl-update trên data cũ) =====

    /** Mức độ nghiêm trọng (BUG/ISSUE). Nullable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private BugSeverity severity;

    /** Các bước tái hiện lỗi. Nullable. */
    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce;

    /** Kết quả mong đợi. Nullable. */
    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    /** Kết quả thực tế. Nullable. */
    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    /** Môi trường: trình duyệt/OS/phiên bản. Nullable. */
    @Column(name = "environment", length = 300)
    private String environment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    protected ProjectTask() {
    }

    public ProjectTask(String projectId, int seq, String title, String createdBy) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.seq = seq;
        this.title = title;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Cập nhật toàn bộ trường nghiệp vụ (PUT) — overload cũ, giữ backward-compat (không đụng chi tiết lỗi/tester). */
    public void apply(String parentId, String title, String description, TaskType type, TaskStatus status,
                      TaskPriority priority, String assigneeUserId, double estimateHours,
                      LocalDate startDate, LocalDate dueDate, int orderIndex, String screen) {
        apply(parentId, title, description, type, status, priority, assigneeUserId, estimateHours,
                startDate, dueDate, orderIndex, screen,
                null, null, null, null, null, null);
    }

    /** Cập nhật toàn bộ trường nghiệp vụ (PUT) — bao gồm chi tiết lỗi (BUG/ISSUE) + người kiểm thử. */
    public void apply(String parentId, String title, String description, TaskType type, TaskStatus status,
                      TaskPriority priority, String assigneeUserId, double estimateHours,
                      LocalDate startDate, LocalDate dueDate, int orderIndex, String screen,
                      BugSeverity severity, String stepsToReproduce, String expectedResult,
                      String actualResult, String environment, String testerUserId) {
        this.parentId = parentId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.status = status;
        this.priority = priority;
        this.assigneeUserId = assigneeUserId;
        this.estimateHours = estimateHours;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.orderIndex = orderIndex;
        this.screen = screen;
        this.severity = severity;
        this.stepsToReproduce = stepsToReproduce;
        this.expectedResult = expectedResult;
        this.actualResult = actualResult;
        this.environment = environment;
        this.testerUserId = testerUserId;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(String reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getTesterUserId() { return testerUserId; }
    public void setTesterUserId(String testerUserId) { this.testerUserId = testerUserId; }
    public double getEstimateHours() { return estimateHours; }
    public void setEstimateHours(double estimateHours) { this.estimateHours = estimateHours; }
    public double getSpentHours() { return spentHours; }
    public void setSpentHours(double spentHours) { this.spentHours = spentHours; }
    /** Cộng dồn thời gian thực tế (giờ) + chạm updatedAt. */
    public void addSpentHours(double hours) { this.spentHours += hours; touch(); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public String getScreen() { return screen; }
    public void setScreen(String screen) { this.screen = screen; }
    public BugSeverity getSeverity() { return severity; }
    public void setSeverity(BugSeverity severity) { this.severity = severity; }
    public String getStepsToReproduce() { return stepsToReproduce; }
    public void setStepsToReproduce(String stepsToReproduce) { this.stepsToReproduce = stepsToReproduce; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
}
