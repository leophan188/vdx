package com.bpm.domain.position;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Người thay thế cho một vị trí khi người giữ vắng (FR-C04). Tối đa một bản active/vị trí.
 * Khi active, việc MỚI giao theo vị trí resolve về người thay thế (không cướp việc đang chạy).
 */
@Entity
@Table(name = "position_substitute")
public class Substitute {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "position_id", length = 36, nullable = false)
    private String positionId;

    @Column(name = "substitute_user_id", length = 36, nullable = false)
    private String substituteUserId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Substitute() {
    }

    public Substitute(String positionId, String substituteUserId) {
        this.id = UUID.randomUUID().toString();
        this.positionId = positionId;
        this.substituteUserId = substituteUserId;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPositionId() { return positionId; }
    public String getSubstituteUserId() { return substituteUserId; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
    public Instant getCreatedAt() { return createdAt; }
}
