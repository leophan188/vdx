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
 * Dự án (module Quản lý dự án — mini-Jira).
 * Conventions: PK UUID (chuỗi 36 ký tự), cột snake_case, metadata UTC (Instant),
 * ngày nghiệp vụ LocalDate. Mã {@code code} ngắn duy nhất (vd "PRJ") dùng sinh code task "PRJ-12".
 */
@Entity
@Table(name = "project", indexes = {
        @Index(name = "ix_project_code", columnList = "code", unique = true)
})
public class Project {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Mã ngắn duy nhất (vd "PRJ") — tiền tố sinh code task. */
    @Column(name = "code", length = 30, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Chủ dự án (UserAccount id). */
    @Column(name = "owner_user_id", length = 36)
    private String ownerUserId;

    /** Ngân sách dự án (VND) — nullable. */
    @Column(name = "budget")
    private Long budget;

    /** Nỗ lực KẾ HOẠCH (man-month) — nhập tay; so sánh với nỗ lực THỰC TẾ (Σ man-day thành viên / 22). */
    @Column(name = "planned_effort_mm")
    private Double plannedEffortMm;

    /** Bộ đếm sinh seq task trong dự án (số task đã tạo). */
    @Column(name = "task_seq", nullable = false)
    private int taskSeq = 0;

    /**
     * ID của dự án bên ERP (project.project). Có giá trị nghĩa là dự án này được chọn đồng bộ từ ERP;
     * null là dự án khai tay trong PlanX.
     *
     * Khớp bằng ID chứ không bằng mã hay tên: mã dự án bên ERP có thể sửa, tên thì sửa thường xuyên,
     * còn ID thì không đổi — dùng tên để khớp là mỗi lần khách đổi tên dự án lại sinh ra một bản ghi mới.
     */
    @Column(name = "erp_project_id")
    private Long erpProjectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Project() {
    }

    public Project(String code, String name, String ownerUserId) {
        this.id = UUID.randomUUID().toString();
        this.code = code;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.status = ProjectStatus.PLANNING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void apply(String name, String description, ProjectStatus status,
                      LocalDate startDate, LocalDate dueDate, String ownerUserId, Long budget,
                      Double plannedEffortMm) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.ownerUserId = ownerUserId;
        this.budget = budget;
        this.plannedEffortMm = plannedEffortMm;
        touch();
    }

    /** Cấp seq mới cho task (sinh code "CODE-seq") + chạm updatedAt. */
    public int nextSeq() {
        this.taskSeq += 1;
        touch();
        return this.taskSeq;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public Double getPlannedEffortMm() { return plannedEffortMm; }
    public void setPlannedEffortMm(Double plannedEffortMm) { this.plannedEffortMm = plannedEffortMm; }
    public Long getErpProjectId() {
        return erpProjectId;
    }

    public void setErpProjectId(Long erpProjectId) {
        this.erpProjectId = erpProjectId;
    }

    public int getTaskSeq() { return taskSeq; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
