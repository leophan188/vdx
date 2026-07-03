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
 * Nhật ký dự án — ghi TAY các buổi làm việc với KHÁCH HÀNG (khác tab "Log" tự động sinh từ hoạt động task).
 * Mỗi bản ghi: ngày làm việc, phân loại (Demo/Khảo sát/UAT/…), nhân sự team tham gia, người phía khách hàng
 * (text tự do), nội dung, kết luận.
 *
 * <p>Quy ước H2/MariaDB (ddl-update): mọi cột MỚI đều NULLABLE trừ id/projectId; thời gian metadata UTC.
 * KHÔNG dùng @Lob cho content/conclusion — Hibernate 6 map @Lob String → TINYTEXT/255 trên MariaDB gây lỗi
 * bài dài (bài học ở {@code Post.body}); dùng length lớn để MariaDB tạo cột LONGTEXT/MEDIUMTEXT, H2 chấp nhận.
 */
@Entity
@Table(name = "project_diary", indexes = {
        @Index(name = "ix_prjdiary_project", columnList = "project_id, work_date")
})
public class ProjectDiary {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "work_date")
    private LocalDate workDate;

    /** Demo / Khảo sát / UAT / Nghiệm thu / Đào tạo / Họp-trao đổi / Hỗ trợ / Khác. */
    @Column(name = "category", length = 40)
    private String category;

    /** Danh sách userId nhân sự team tham gia, nối bằng dấu phẩy (vd "u1,u2"). */
    @Column(name = "team_user_ids", length = 1000)
    private String teamUserIds;

    /** Người phía khách hàng — text tự do (khách không có trong hệ thống). */
    @Column(name = "client_contacts", length = 500)
    private String clientContacts;

    // KHÔNG @Lob — length lớn để MariaDB tạo LONGTEXT, H2 tạo VARCHAR lớn.
    @Column(name = "content", length = 100_000)
    private String content;

    @Column(name = "conclusion", length = 100_000)
    private String conclusion;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ProjectDiary() {
    }

    public ProjectDiary(String projectId, LocalDate workDate, String category, String teamUserIds,
                        String clientContacts, String content, String conclusion,
                        String createdBy, String createdByName) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.workDate = workDate;
        this.category = category;
        this.teamUserIds = teamUserIds;
        this.clientContacts = clientContacts;
        this.content = content;
        this.conclusion = conclusion;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Cập nhật dấu thời gian sửa. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getCategory() { return category; }
    public String getTeamUserIds() { return teamUserIds; }
    public String getClientContacts() { return clientContacts; }
    public String getContent() { return content; }
    public String getConclusion() { return conclusion; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedByName() { return createdByName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public void setCategory(String category) { this.category = category; }
    public void setTeamUserIds(String teamUserIds) { this.teamUserIds = teamUserIds; }
    public void setClientContacts(String clientContacts) { this.clientContacts = clientContacts; }
    public void setContent(String content) { this.content = content; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
}
