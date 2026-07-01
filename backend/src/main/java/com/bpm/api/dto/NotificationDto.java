package com.bpm.api.dto;

import com.bpm.domain.notification.Notification;

import java.time.Instant;

/** Thông báo in-app (Story 4.9). */
public record NotificationDto(String id, String type, String title, String body, String link,
                              boolean read, Instant createdAt) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.isReadFlag(), n.getCreatedAt());
    }
}
