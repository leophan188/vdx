package com.bpm.api;

import com.bpm.api.dto.NotificationDto;
import com.bpm.application.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Thông báo in-app của người dùng đăng nhập (Story 4.9). */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @GetMapping
    public List<NotificationDto> mine(Authentication auth) {
        return service.mine(actor(auth), 20).stream().map(NotificationDto::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", service.unreadCount(actor(auth)));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable String id, Authentication auth) {
        service.markRead(id, actor(auth));
    }

    @PostMapping("/read-all")
    public void markAllRead(Authentication auth) {
        service.markAllRead(actor(auth));
    }
}
