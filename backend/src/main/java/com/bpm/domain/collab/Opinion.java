package com.bpm.domain.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Ý kiến phối hợp trên tài liệu/hồ sơ (Story 3.11/3.12/3.14/3.15). Người chủ trì tiếp thu/không tiếp thu. */
@Entity
@Table(name = "collab_opinion", indexes = {
        @Index(name = "ix_op_target", columnList = "target_type, target_id"),
        @Index(name = "ix_op_round", columnList = "round_id")
})
public class Opinion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "target_type", length = 16, nullable = false)
    private String targetType;

    @Column(name = "target_id", length = 36, nullable = false)
    private String targetId;

    /** Thuộc đợt phối hợp nào (nếu là phối hợp song song có chủ trì). */
    @Column(name = "round_id", length = 36)
    private String roundId;

    @Column(name = "author", length = 100, nullable = false)
    private String author;

    @Column(name = "author_name", length = 150)
    private String authorName;

    /** DONG_Y | KHONG_DONG_Y | CO_Y_KIEN */
    @Column(name = "stance", length = 16, nullable = false)
    private String stance;

    @Column(name = "body", length = 2000)
    private String body;

    /** Người chủ trì xử lý: TIEP_THU | KHONG_TIEP_THU | null (chưa xử lý). */
    @Column(name = "resolution", length = 16)
    private String resolution;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "editable_until", nullable = false)
    private Instant editableUntil;

    @Column(name = "edited", nullable = false)
    private boolean edited;

    protected Opinion() {
    }

    public Opinion(String targetType, String targetId, String roundId, String author, String authorName,
                   String stance, String body, int editWindowMinutes) {
        this.id = UUID.randomUUID().toString();
        this.targetType = targetType;
        this.targetId = targetId;
        this.roundId = roundId;
        this.author = author;
        this.authorName = authorName;
        this.stance = stance;
        this.body = body;
        this.createdAt = Instant.now();
        this.editableUntil = this.createdAt.plus(Duration.ofMinutes(editWindowMinutes));
        this.edited = false;
    }

    public boolean isEditable() {
        return resolution == null && Instant.now().isBefore(editableUntil);
    }

    public void edit(String stance, String body) {
        this.stance = stance;
        this.body = body;
        this.edited = true;
    }

    public void resolve(String resolution, String note) {
        this.resolution = resolution;
        this.resolutionNote = note;
    }

    public String getId() { return id; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getRoundId() { return roundId; }
    public String getAuthor() { return author; }
    public String getAuthorName() { return authorName; }
    public String getStance() { return stance; }
    public String getBody() { return body; }
    public String getResolution() { return resolution; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditableUntil() { return editableUntil; }
    public boolean isEdited() { return edited; }
}
