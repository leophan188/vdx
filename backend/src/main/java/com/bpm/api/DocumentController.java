package com.bpm.api;

import com.bpm.application.DocumentService;
import com.bpm.domain.document.Document;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Tài liệu dự thảo OnlyOffice (Story 3.10). content/callback CÔNG KHAI (OnlyOffice gọi, không có phiên). */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    public record CreateRequest(String name, String instanceId) {
    }

    public record Summary(String id, String name, int version, String instanceId, String updatedAt, String updatedBy,
                          String status, String signedNumber, String signedAt, String signedBy,
                          String archiveNo, String classification, Integer retentionYears, String archivedAt) {
        static Summary of(Document d) {
            return new Summary(d.getId(), d.getName(), d.getVersion(), d.getInstanceId(),
                    d.getUpdatedAt().toString(), d.getUpdatedBy(), d.getStatus(),
                    d.getSignedNumber(), d.getSignedAt() == null ? null : d.getSignedAt().toString(), d.getSignedBy(),
                    d.getArchiveNo(), d.getClassification(), d.getRetentionYears(),
                    d.getArchivedAt() == null ? null : d.getArchivedAt().toString());
        }
    }

    public record SignRequest(String number) {
    }

    public record ArchiveRequest(String archiveNo, String classification, Integer retentionYears) {
    }

    @PostMapping
    public Summary create(@RequestBody CreateRequest req, Authentication auth) {
        return Summary.of(service.create(req.name() == null || req.name().isBlank() ? "Tài liệu mới" : req.name(),
                req.instanceId(), actor(auth)));
    }

    @GetMapping
    public List<Summary> list() {
        return service.list().stream().map(Summary::of).toList();
    }

    @GetMapping("/{id}")
    public Summary get(@PathVariable String id) {
        return Summary.of(service.get(id));
    }

    @GetMapping("/{id}/versions")
    public List<Map<String, Object>> versions(@PathVariable String id) {
        return service.versions(id).stream().map(v -> Map.<String, Object>of(
                "version", v.getVersion(), "savedAt", v.getSavedAt().toString(),
                "savedBy", v.getSavedBy() == null ? "" : v.getSavedBy())).toList();
    }

    /** Config để FE nhúng editor OnlyOffice (đã ký JWT). */
    @GetMapping("/{id}/editor-config")
    public Map<String, Object> editorConfig(@PathVariable String id, Authentication auth) {
        String user = actor(auth);
        return service.editorConfig(id, user, user, true);
    }

    /** OnlyOffice tải nội dung — CÔNG KHAI, xác thực bằng token. */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id, @RequestParam String token) {
        service.verifyContentToken(id, token);
        Document d = service.get(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".docx\"")
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .body(service.content(id));
    }

    /** Ghi nhận ký ban hành (Story 3.16). */
    @PostMapping("/{id}/sign")
    public Summary sign(@PathVariable String id, @RequestBody SignRequest req, Authentication auth) {
        return Summary.of(service.sign(id, req.number(), actor(auth)));
    }

    /** Đóng hồ sơ + lưu trữ (Story 3.17). */
    @PostMapping("/{id}/archive")
    public Summary archive(@PathVariable String id, @RequestBody ArchiveRequest req, Authentication auth) {
        return Summary.of(service.archive(id, req.archiveNo(), req.classification(), req.retentionYears(), actor(auth)));
    }

    /** OnlyOffice gọi khi lưu — CÔNG KHAI. Phải trả {"error":0}. */
    @PostMapping("/{id}/callback")
    public Map<String, Object> callback(@PathVariable String id, @RequestBody JsonNode body) {
        try {
            service.handleCallback(id, body, "onlyoffice");
            return Map.of("error", 0);
        } catch (Exception e) {
            return Map.of("error", 1, "message", String.valueOf(e.getMessage()));
        }
    }
}
