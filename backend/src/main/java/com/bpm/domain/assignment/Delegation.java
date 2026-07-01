package com.bpm.domain.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Một bước trong chuỗi uỷ quyền/chuyển tiếp của một việc (FR-C04). Append-only — dùng để
 * dựng lại chuỗi và guard chống lặp (không reassign tới ai đã từng giữ việc).
 */
@Entity
@Table(name = "task_delegation")
public class Delegation {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "from_user_id", length = 36, nullable = false)
    private String fromUserId;

    @Column(name = "to_user_id", length = 36, nullable = false)
    private String toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16, nullable = false)
    private DelegationKind kind;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Delegation() {
    }

    public Delegation(String taskId, String fromUserId, String toUserId, DelegationKind kind, String reason) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.kind = kind;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getFromUserId() { return fromUserId; }
    public String getToUserId() { return toUserId; }
    public DelegationKind getKind() { return kind; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
