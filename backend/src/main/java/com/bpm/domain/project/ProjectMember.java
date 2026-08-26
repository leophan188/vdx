package com.bpm.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thành viên dự án (liên kết Project ↔ UserAccount). Tên hiển thị denorm khi trả DTO (không lưu cứng).
 */
@Entity
@Table(name = "project_member", indexes = {
        @Index(name = "ix_project_member_project", columnList = "project_id"),
        @Index(name = "ix_project_member_uniq", columnList = "project_id,user_id", unique = true)
})
public class ProjectMember {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    /** UserAccount id. */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_project", length = 20, nullable = false)
    private ProjectRoleInProject roleInProject = ProjectRoleInProject.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    /** Ngày tham gia / rời dự án (phục vụ tính man-day phân bổ). */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** % effort của nhân sự dành cho dự án này (1–100). Mặc định 100%. */
    @Column(name = "effort_pct", nullable = false, columnDefinition = "int default 100")
    private int effortPct = 100;

    /**
     * Thành viên còn được vào dự án hay không. Dùng kiểu bọc và cho phép NULL: cột này thêm sau khi
     * đã có dữ liệu, dòng cũ sẽ mang NULL — coi NULL là ĐANG hoạt động thì không phải backfill,
     * cũng không có cảnh đọc bản ghi cũ là lỗi.
     */
    @Column(name = "active")
    private Boolean active;

    protected ProjectMember() {
    }

    public ProjectMember(String projectId, String userId, ProjectRoleInProject roleInProject) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.userId = userId;
        this.roleInProject = roleInProject;
        this.joinedAt = Instant.now();
    }

    /** Số ngày LÀM VIỆC (T2–T6) trong khoảng [startDate, endDate] (gồm 2 đầu). 0 nếu thiếu ngày. */
    public int workdays() {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    /** Man-day THỰC TẾ = số ngày làm việc × % effort (làm tròn). */
    public int manday() {
        return Math.round(workdays() * Math.max(0, Math.min(100, effortPct)) / 100.0f);
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getUserId() { return userId; }
    public ProjectRoleInProject getRoleInProject() { return roleInProject; }
    public void setRoleInProject(ProjectRoleInProject roleInProject) { this.roleInProject = roleInProject; }
    public Instant getJoinedAt() { return joinedAt; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    /** NULL (dữ liệu cũ) = vẫn đang hoạt động. */
    public boolean isActive() { return active == null || active; }
    public void setActive(boolean value) { this.active = value; }

    public int getEffortPct() { return effortPct; }
    public void setEffortPct(int effortPct) { this.effortPct = effortPct; }
}
