package com.bpm.domain.position;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Vị trí/chức danh trong một đơn vị (AD-7). Mỗi vị trí tối đa một người giữ hiện hành (AD-14). */
@Entity
@Table(name = "org_position")
public class Position {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "org_unit_id", length = 36, nullable = false)
    private String orgUnitId;

    /** Người giữ hiện hành (null = đang trống). Điểm late-binding cho phân công AD-14. */
    @Column(name = "current_holder_user_id", length = 36)
    private String currentHolderUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Position() {
    }

    public Position(String title, String orgUnitId) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.orgUnitId = orgUnitId;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrgUnitId() { return orgUnitId; }
    public String getCurrentHolderUserId() { return currentHolderUserId; }
    public void setCurrentHolderUserId(String currentHolderUserId) { this.currentHolderUserId = currentHolderUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
