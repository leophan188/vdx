package com.bpm.api;

import com.bpm.api.dto.AuditEventDto;
import com.bpm.application.AuditQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Truy vết kiểm toán (AD-6, FR-I01, FR-I02) — chỉ đọc, ROLE_ADMIN (kiểm toán viên GĐ1). */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditEventDto.Response> trail(@RequestParam String objectType, @RequestParam String objectId) {
        return service.trail(objectType, objectId).stream().map(AuditEventDto.Response::from).toList();
    }

    /** 200 sự kiện kiểm toán gần nhất toàn hệ thống (duyệt nhanh, không cần nhập đối tượng). */
    @GetMapping("/recent")
    public List<AuditEventDto.Response> recent() {
        return service.recent().stream().map(AuditEventDto.Response::from).toList();
    }

    @GetMapping("/task/{taskId}")
    public List<AuditEventDto.Response> trailForTask(@PathVariable String taskId) {
        return service.trailForTask(taskId).stream().map(AuditEventDto.Response::from).toList();
    }
}
