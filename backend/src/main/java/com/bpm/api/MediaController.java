package com.bpm.api;

import com.bpm.application.MediaStorageService;
import com.bpm.domain.social.PostMedia;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * Phục vụ media bài bảng tin (Story 2.1, NFR-03). /api/v1/media/{id} — CHỈ người có phiên (chặn ở SecurityConfig).
 * Trả inline để trình duyệt hiển thị ảnh/<video> trực tiếp; stream từ đĩa qua PathResource (không nạp hết vào bộ nhớ).
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaStorageService storage;

    public MediaController(MediaStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> serve(@PathVariable String id) {
        PostMedia m = storage.get(id);
        Path path = storage.pathOf(m);
        Resource body = new PathResource(path);
        if (!body.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + m.getId() + "\"")
                // Chống suy diễn loại content khác (an toàn) + cho phép cache phía client.
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .body(body);
    }
}
