package com.bpm.domain.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Bài đăng bảng tin nội bộ (Epic 2 — Story 2.1/2.2/2.3/2.7).
 * scope = ALL (toàn công ty) | ORG_UNIT (một phòng, orgUnitId bắt buộc).
 * mediaPaths = JSON mảng descriptor media đã lưu trên đĩa (id/kind/name) — phục vụ qua /api/v1/media/{id}.
 * Conventions: PK UUID 36 ký tự, cột snake_case, thời gian UTC.
 */
@Entity
@Table(name = "social_post", indexes = {
        @Index(name = "ix_post_feed", columnList = "hidden, pinned, created_at"),
        @Index(name = "ix_post_scope", columnList = "scope, org_unit_id")
})
public class Post {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "author_id", length = 36, nullable = false)
    private String authorId;

    @Column(name = "author_name", length = 200, nullable = false)
    private String authorName;

    // KHÔNG dùng @Lob (Hibernate 6 map @Lob String -> TINYTEXT/255 trên MariaDB -> bài dài lỗi 500).
    // length lớn -> MariaDB tạo LONGTEXT, H2 tạo VARCHAR lớn: an toàn đa CSDL.
    @Column(name = "body", nullable = false, length = 100_000_000)
    private String body;

    /** JSON mảng media descriptor: [{"id","kind","name","contentType","size"}]. "[]" nếu không có. */
    @Column(name = "media_paths", length = 100_000_000)
    private String mediaPaths = "[]";

    /** ALL | ORG_UNIT. */
    @Column(name = "scope", length = 16, nullable = false)
    private String scope = "ALL";

    /** Bắt buộc khi scope = ORG_UNIT. */
    @Column(name = "org_unit_id", length = 36)
    private String orgUnitId;

    @Column(name = "topic", length = 100)
    private String topic;

    /**
     * Phân loại bài đăng (toàn công ty): NEWS (Tin tức) | EVENT (Sự kiện) | ANNOUNCEMENT (Thông báo).
     * Nullable + default 'ANNOUNCEMENT' (quy ước H2: cột mới phải nullable để không vỡ ddl-update dữ liệu cũ).
     */
    @Column(name = "category", length = 20, columnDefinition = "varchar(20) default 'ANNOUNCEMENT'")
    private String category = "ANNOUNCEMENT";

    /**
     * "Đối tượng được chúc" — userId (UserAccount) của nhân sự được nhắc tới trong tin chúc mừng
     * (sinh nhật / onboard), để FE nhúng ảnh đại diện vào nội dung bài. NULLABLE (quy ước H2: cột mới
     * phải nullable để không vỡ ddl-update). Null với mọi bài thường (không phải tin chúc mừng).
     */
    @Column(name = "subject_user_id", length = 36)
    private String subjectUserId;

    /** Danh sách userId được @nhắc, cách nhau dấu phẩy. Rỗng nếu không có. */
    @Column(name = "mentions", length = 1000)
    private String mentions = "";

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Post() {
    }

    public Post(String authorId, String authorName, String body, String mediaPaths,
                String scope, String orgUnitId, String topic) {
        this(authorId, authorName, body, mediaPaths, scope, orgUnitId, topic, "", "ANNOUNCEMENT");
    }

    public Post(String authorId, String authorName, String body, String mediaPaths,
                String scope, String orgUnitId, String topic, String mentions) {
        this(authorId, authorName, body, mediaPaths, scope, orgUnitId, topic, mentions, "ANNOUNCEMENT");
    }

    public Post(String authorId, String authorName, String body, String mediaPaths,
                String scope, String orgUnitId, String topic, String mentions, String category) {
        this.id = UUID.randomUUID().toString();
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.mediaPaths = mediaPaths == null || mediaPaths.isBlank() ? "[]" : mediaPaths;
        this.scope = scope;
        this.orgUnitId = "ORG_UNIT".equals(scope) ? orgUnitId : null;
        this.topic = topic;
        this.category = normalizeCategory(category);
        this.mentions = mentions == null ? "" : mentions;
        this.pinned = false;
        this.hidden = false;
        this.createdAt = Instant.now();
    }

    /** Chuẩn hoá phân loại về {NEWS, EVENT, ANNOUNCEMENT}; giá trị lạ/null → ANNOUNCEMENT (mặc định). */
    public static String normalizeCategory(String c) {
        if (c == null) {
            return "ANNOUNCEMENT";
        }
        String v = c.trim().toUpperCase();
        return switch (v) {
            case "NEWS", "EVENT", "ANNOUNCEMENT" -> v;
            default -> "ANNOUNCEMENT";
        };
    }

    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public void setCategory(String category) { this.category = normalizeCategory(category); }
    public void setSubjectUserId(String subjectUserId) { this.subjectUserId = subjectUserId; }

    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public String getMediaPaths() { return mediaPaths; }
    public String getScope() { return scope; }
    public String getOrgUnitId() { return orgUnitId; }
    public String getTopic() { return topic; }
    public String getCategory() { return category == null ? "ANNOUNCEMENT" : category; }
    public String getSubjectUserId() { return subjectUserId; }
    public String getMentions() { return mentions; }
    public boolean isPinned() { return pinned; }
    public boolean isHidden() { return hidden; }
    public Instant getCreatedAt() { return createdAt; }
}
