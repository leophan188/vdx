package com.bpm.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản ghi audit append-only (AD-6, NFR-13). Không UPDATE/DELETE.
 * [ASSUMPTION] phân vùng theo thời gian (PARTITION BY created_at) cấu hình ở tầng DDL/vận hành (Story 5.5);
 * entity này chỉ INSERT.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "action", length = 60, nullable = false, updatable = false)
    private String action;

    @Column(name = "object_type", length = 60, updatable = false)
    private String objectType;

    @Column(name = "object_id", length = 36, updatable = false)
    private String objectId;

    @Column(name = "actor", length = 100, nullable = false, updatable = false)
    private String actor;

    @Column(name = "detail", length = 1000, updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String action, String objectType, String objectId, String actor, String detail) {
        this.id = UUID.randomUUID().toString();
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.actor = actor;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    /** Append-only (AD-6, NFR-13): chặn mọi UPDATE/DELETE ở tầng JPA. DB partition/REVOKE → Story 5.5. */
    @PreUpdate
    private void preventUpdate() {
        throw new UnsupportedOperationException("Audit ghi-một-lần: không được UPDATE (AD-6)");
    }

    @PreRemove
    private void preventRemove() {
        throw new UnsupportedOperationException("Audit ghi-một-lần: không được DELETE (AD-6)");
    }

    public String getId() { return id; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
