package com.bpm.api;

import com.bpm.api.dto.MeTaskDto;
import com.bpm.application.MeTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * BACKLOG CÁ NHÂN — /api/v1/me/tasks** (chặn = authenticated ở SecurityConfig, /api/v1/me/** đã đủ).
 * Chủ thể luôn lấy từ Authentication.getName() (không nhận id từ client). deadline dạng ISO yyyy-MM-dd.
 * Task DỰ ÁN của tôi FE lấy riêng qua /api/v1/projects/my-tasks (đã có) — đây chỉ trả task RIÊNG.
 */
@RestController
@RequestMapping("/api/v1/me/tasks")
public class MeTaskController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MeTaskService service;

    public MeTaskController(MeTaskService service) {
        this.service = service;
    }

    /** Task RIÊNG của tôi. */
    @GetMapping("")
    public ResponseEntity<List<MeTaskDto.PersonalTaskView>> list(Authentication auth) {
        return ResponseEntity.ok(service.listPersonal(auth.getName()));
    }

    /** Tạo task riêng (projectId rỗng) hoặc task gắn dự án (đồng bộ vào backlog chung). */
    @PostMapping("")
    public ResponseEntity<MeTaskDto.CreateResult> create(@RequestBody MeTaskDto.CreateRequest req,
                                                         Authentication auth) {
        return ResponseEntity.ok(service.create(auth.getName(), req.title(), req.estimateHours(),
                parseDate(req.deadline()), req.projectId()));
    }

    /** Sửa task cá nhân (chỉ chủ sở hữu). */
    @PutMapping("/{id}")
    public ResponseEntity<MeTaskDto.PersonalTaskView> update(@PathVariable String id,
                                                             @RequestBody MeTaskDto.UpdateRequest req,
                                                             Authentication auth) {
        return ResponseEntity.ok(service.updatePersonal(auth.getName(), id, req.title(),
                req.estimateHours(), parseDate(req.deadline()), req.status()));
    }

    /** Đổi trạng thái task cá nhân. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<MeTaskDto.PersonalTaskView> setStatus(@PathVariable String id,
                                                                @RequestBody MeTaskDto.StatusRequest req,
                                                                Authentication auth) {
        return ResponseEntity.ok(service.setPersonalStatus(auth.getName(), id, req.status()));
    }

    /** Xóa task cá nhân. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id, Authentication auth) {
        service.deletePersonal(auth.getName(), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Dự án tôi là thành viên (dropdown khi thêm task gắn dự án). */
    @GetMapping("/projects")
    public ResponseEntity<List<MeTaskDto.ProjectOption>> myProjects(Authentication auth) {
        return ResponseEntity.ok(service.myProjects(auth.getName()));
    }

    private static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim(), ISO);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ngày sai định dạng yyyy-MM-dd (\"" + v + "\")");
        }
    }
}
