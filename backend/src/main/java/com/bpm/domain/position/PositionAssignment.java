package com.bpm.domain.position;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch sử nhiệm kỳ giữ vị trí. Nhiệm kỳ hiện hành = endedAt null.
 * Gán người mới sẽ đóng nhiệm kỳ hiện hành (set endedAt) và mở nhiệm kỳ mới (FR-C02).
 */
@Entity
@Table(name = "position_assignment")
public class PositionAssignment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "position_id", length = 36, nullable = false)
    private String positionId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected PositionAssignment() {
    }

    public PositionAssignment(String positionId, String userId) {
        this.id = UUID.randomUUID().toString();
        this.positionId = positionId;
        this.userId = userId;
        this.assignedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPositionId() { return positionId; }
    public String getUserId() { return userId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void end() { this.endedAt = Instant.now(); }
    public boolean isCurrent() { return endedAt == null; }
}
