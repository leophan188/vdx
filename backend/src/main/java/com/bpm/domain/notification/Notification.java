package com.bpm.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Thông báo in-app gửi tới một người dùng (Epic 4 — Story 4.9). Index theo người + chưa đọc (Story 5.7). */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "ix_notif_recipient_unread", columnList = "recipient_user_id, is_read")
})
public class Notification {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "recipient_user_id", length = 36, nullable = false)
    private String recipientUserId;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "link", length = 200)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean readFlag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String recipientUserId, String type, String title, String body, String link) {
        this.id = UUID.randomUUID().toString();
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.readFlag = false;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        this.readFlag = true;
    }

    public String getId() { return id; }
    public String getRecipientUserId() { return recipientUserId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public boolean isReadFlag() { return readFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
