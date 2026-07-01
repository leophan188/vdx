package com.bpm.domain.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Bình luận kiểu Jira trên tài liệu/hồ sơ (Story 3.13/3.14) — có @mention, sửa được khi còn hạn. */
@Entity
@Table(name = "collab_comment", indexes = {
        @Index(name = "ix_cmt_target", columnList = "target_type, target_id")
})
public class Comment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "target_type", length = 16, nullable = false)
    private String targetType;   // DOCUMENT | INSTANCE

    @Column(name = "target_id", length = 36, nullable = false)
    private String targetId;

    @Column(name = "author", length = 100, nullable = false)
    private String author;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "editable_until", nullable = false)
    private Instant editableUntil;

    @Column(name = "edited", nullable = false)
    private boolean edited;

    protected Comment() {
    }

    public Comment(String targetType, String targetId, String author, String authorName, String body, int editWindowMinutes) {
        this.id = UUID.randomUUID().toString();
        this.targetType = targetType;
        this.targetId = targetId;
        this.author = author;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = Instant.now();
        this.editableUntil = this.createdAt.plus(Duration.ofMinutes(editWindowMinutes));
        this.edited = false;
    }

    public boolean isEditable() {
        return Instant.now().isBefore(editableUntil);
    }

    public void edit(String newBody) {
        this.body = newBody;
        this.edited = true;
    }

    public String getId() { return id; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getAuthor() { return author; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditableUntil() { return editableUntil; }
    public boolean isEdited() { return edited; }
}
