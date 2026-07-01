package com.bpm.domain.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Bình luận 1 cấp trên bài bảng tin (Story 2.5). Tái dùng pattern collab Comment GĐ1:
 * tác giả sửa/xoá trong hạn (editableUntil). hidden = ẩn kiểm duyệt (Story 2.7).
 */
@Entity
@Table(name = "social_comment", indexes = {
        @Index(name = "ix_scmt_post", columnList = "post_id, hidden, created_at")
})
public class PostComment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "author_id", length = 36, nullable = false)
    private String authorId;

    @Column(name = "author_name", length = 200, nullable = false)
    private String authorName;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    /** id bình luận cha (reply lồng nhau). null = bình luận gốc. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    /** Danh sách userId được @nhắc, cách nhau dấu phẩy. Rỗng nếu không có. */
    @Column(name = "mentions", length = 1000)
    private String mentions = "";

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "edited", nullable = false)
    private boolean edited;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "editable_until", nullable = false)
    private Instant editableUntil;

    protected PostComment() {
    }

    public PostComment(String postId, String authorId, String authorName, String body, int editWindowMinutes) {
        this(postId, authorId, authorName, body, null, "", editWindowMinutes);
    }

    public PostComment(String postId, String authorId, String authorName, String body,
                       String parentId, String mentions, int editWindowMinutes) {
        this.id = UUID.randomUUID().toString();
        this.postId = postId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.parentId = parentId;
        this.mentions = mentions == null ? "" : mentions;
        this.hidden = false;
        this.edited = false;
        this.createdAt = Instant.now();
        this.editableUntil = this.createdAt.plus(Duration.ofMinutes(editWindowMinutes));
    }

    public boolean isEditable() {
        return Instant.now().isBefore(editableUntil);
    }

    public void edit(String newBody) {
        this.body = newBody;
        this.edited = true;
    }

    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public String getParentId() { return parentId; }
    public String getMentions() { return mentions; }
    public boolean isHidden() { return hidden; }
    public boolean isEdited() { return edited; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditableUntil() { return editableUntil; }
}
