package com.bpm.domain.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Đợt phối hợp song song (Story 3.15): chủ trì mời nhiều người cho ý kiến, join khi đủ/quá hạn (chống treo). */
@Entity
@Table(name = "collab_round")
public class CoordinationRound {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "target_type", length = 16, nullable = false)
    private String targetType;

    @Column(name = "target_id", length = 36, nullable = false)
    private String targetId;

    @Column(name = "requester", length = 100, nullable = false)
    private String requester;

    /** Danh sách username người tham gia, ngăn cách dấu phẩy. */
    @Column(name = "participants", length = 2000, nullable = false)
    private String participants;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    /** OPEN | CLOSED */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoordinationRound() {
    }

    public CoordinationRound(String targetType, String targetId, String requester, String participants, Instant deadline) {
        this.id = UUID.randomUUID().toString();
        this.targetType = targetType;
        this.targetId = targetId;
        this.requester = requester;
        this.participants = participants;
        this.deadline = deadline;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public boolean isOverdue() {
        return Instant.now().isAfter(deadline);
    }

    public void close() {
        this.status = "CLOSED";
    }

    public String getId() { return id; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getRequester() { return requester; }
    public String getParticipants() { return participants; }
    public Instant getDeadline() { return deadline; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
