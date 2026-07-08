package com.bpm.api;

import com.bpm.application.AttachmentService;
import com.bpm.domain.attachment.Attachment;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Tệp đính kèm biểu mẫu. Upload trả metadata (FE nhúng id vào formData); tải xuống theo id.
 * Cả hai chỉ dành cho người CÓ PHIÊN (mặc định authenticated ở SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    /** Metadata trả về sau khi upload — FE lưu dạng JSON vào giá trị trường. */
    public record AttachmentView(String id, String name, long size, String contentType, String url) {
        static AttachmentView of(Attachment a) {
            return new AttachmentView(a.getId(), a.getOriginalName(), a.getSizeBytes(),
                    a.getContentType(), "/api/v1/attachments/" + a.getId());
        }
    }

    @PostMapping
    public AttachmentView upload(@RequestParam("file") MultipartFile file, Authentication auth) {
        String actor = auth != null ? auth.getName() : "system";
        return AttachmentView.of(service.store(file, actor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        Attachment a = service.get(id);
        Path path = service.pathOf(a);
        Resource body = new PathResource(path);
        if (!body.exists()) {
            return ResponseEntity.notFound().build();
        }
        String encoded = URLEncoder.encode(a.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .body(body);
    }
}
