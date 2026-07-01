package com.bpm.api;

import com.bpm.api.dto.MeTaskDto;
import com.bpm.application.MeTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LOG BUG/ISSUE CÁ NHÂN — /api/v1/me/bugs (chặn = authenticated ở SecurityConfig, /api/v1/me/** đã đủ).
 * Chủ thể luôn lấy từ Authentication.getName() (không nhận id từ client).
 * Dropdown dự án khi log bug tái dùng GET /api/v1/me/tasks/projects (đã có).
 */
@RestController
@RequestMapping("/api/v1/me/bugs")
public class MeBugController {

    private final MeTaskService service;

    public MeBugController(MeTaskService service) {
        this.service = service;
    }

    /** BUG/ISSUE liên quan tôi: tôi LOG (reporter) HOẶC được giao (assignee), gộp không trùng. */
    @GetMapping("")
    public ResponseEntity<List<MeTaskDto.MyBugView>> list(Authentication auth) {
        return ResponseEntity.ok(service.myBugs(auth.getName()));
    }

    /** Log 1 BUG/ISSUE vào dự án tôi là thành viên; reporter tự set = tôi. */
    @PostMapping("")
    public ResponseEntity<MeTaskDto.CreateResult> log(@RequestBody MeTaskDto.LogBugRequest req,
                                                      Authentication auth) {
        return ResponseEntity.ok(service.logBug(auth.getName(), req));
    }
}
