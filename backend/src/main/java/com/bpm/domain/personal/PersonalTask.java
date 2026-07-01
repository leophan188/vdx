package com.bpm.domain.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Task RIÊNG của một nhân sự (BACKLOG CÁ NHÂN) — KHÔNG thuộc dự án nào.
 * Conventions: PK UUID (chuỗi 36 ký tự), cột snake_case hợp lệ H2, metadata UTC (Instant),
 * deadline dạng LocalDate (nullable). {@code status} dùng giá trị enum
 * {@code com.bpm.domain.project.TaskStatus} (BACKLOG/TODO/IN_PROGRESS/IN_REVIEW/DONE) — lưu String.
 * Task GẮN DỰ ÁN không lưu ở đây mà đồng bộ thẳng vào backlog chung ({@code project_task}).
 */
@Entity
@Table(name = "personal_task", indexes = {
        @Index(name = "ix_personal_task_user", columnList = "user_id")
})
public class PersonalTask {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Chủ sở hữu (UserAccount id). */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** Tên hiển thị chủ sở hữu (snapshot lúc tạo). */
    @Column(name = "user_name", length = 200)
    private String userName;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    /** Ước lượng thời gian (giờ). */
    @Column(name = "estimate_hours")
    private double estimateHours;

    /** Hạn hoàn thành (nullable). */
    @Column(name = "deadline")
    private LocalDate deadline;

    /** Trạng thái — giá trị của TaskStatus. Mặc định TODO. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "TODO";

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PersonalTask() {
    }

    public PersonalTask(String userId, String userName, String title, double estimateHours,
                        LocalDate deadline, String status, String note) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.userName = userName;
        this.title = title;
        this.estimateHours = estimateHours;
        this.deadline = deadline;
        this.status = status;
        this.note = note;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public double getEstimateHours() { return estimateHours; }
    public void setEstimateHours(double estimateHours) { this.estimateHours = estimateHours; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
