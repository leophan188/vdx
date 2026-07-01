package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.notification.Notification;
import com.bpm.infrastructure.NotificationRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Thông báo in-app (Story 4.9): tạo khi giao việc / cảnh báo + truy vấn theo người dùng. */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final UserAccountRepository userRepo;

    public NotificationService(NotificationRepository repo, UserAccountRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    /** Tạo thông báo cho một người (theo userId). Bỏ qua nếu thiếu người nhận. */
    @Transactional
    public void notify(String recipientUserId, String type, String title, String body, String link) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            return;
        }
        repo.save(new Notification(recipientUserId, type, title, body, link));
    }

    /**
     * Tạo thông báo CHỈ MỘT LẦN cho cặp (người nhận, loại, link). Trả true nếu đã tạo mới,
     * false nếu đã tồn tại (tránh spam khi gọi lặp — vd nhắc onboarding mỗi lần mở trang chủ).
     */
    @Transactional
    public boolean notifyOnce(String recipientUserId, String type, String title, String body, String link) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            return false;
        }
        if (repo.existsByRecipientUserIdAndTypeAndLink(recipientUserId, type, link)) {
            return false;
        }
        repo.save(new Notification(recipientUserId, type, title, body, link));
        return true;
    }

    @Transactional(readOnly = true)
    public List<Notification> mine(String username, int limit) {
        String userId = userId(username);
        if (userId == null) {
            return List.of();
        }
        return repo.findByRecipientUserIdOrderByCreatedAtDesc(userId, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        String userId = userId(username);
        return userId == null ? 0 : repo.countByRecipientUserIdAndReadFlagFalse(userId);
    }

    @Transactional
    public void markRead(String id, String username) {
        String userId = userId(username);
        if (userId == null) {
            return;
        }
        repo.findByIdAndRecipientUserId(id, userId).ifPresent(n -> {
            n.markRead();
            repo.save(n);
        });
    }

    @Transactional
    public void markAllRead(String username) {
        String userId = userId(username);
        if (userId == null) {
            return;
        }
        for (Notification n : repo.findByRecipientUserIdAndReadFlagFalse(userId)) {
            n.markRead();
            repo.save(n);
        }
    }

    private String userId(String username) {
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }
}
