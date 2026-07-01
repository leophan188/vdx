package com.bpm.infrastructure;

import com.bpm.domain.notification.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId, Limit limit);
    long countByRecipientUserIdAndReadFlagFalse(String recipientUserId);
    List<Notification> findByRecipientUserIdAndReadFlagFalse(String recipientUserId);
    Optional<Notification> findByIdAndRecipientUserId(String id, String recipientUserId);

    /** Khử trùng thông báo (vd nhắc onboarding): chỉ tạo nếu chưa có cùng người nhận + loại + link. */
    boolean existsByRecipientUserIdAndTypeAndLink(String recipientUserId, String type, String link);
}
