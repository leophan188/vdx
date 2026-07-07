package com.bpm.api;

import com.bpm.api.dto.TaskDto;
import com.bpm.api.dto.WorkflowDto;
import com.bpm.application.WorkflowService;
import com.bpm.domain.workflow.WorkflowInstance;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Khởi tạo, theo dõi & hủy phiên chạy quy trình (Story 3.1/3.3/3.6) — ROLE_ADMIN GĐ1. */
@RestController
@RequestMapping("/api/v1/instances")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<WorkflowDto.InstanceResponse> start(@Valid @RequestBody WorkflowDto.StartRequest req,
                                                              Authentication auth) {
        WorkflowInstance wi = service.start(req.processId(), req.formData(), actor(auth));
        String firstTaskId = service.firstOpenTaskId(wi.getFlowableInstanceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowDto.InstanceResponse.from(wi, firstTaskId));
    }

    @GetMapping
    public List<WorkflowDto.InstanceResponse> byProcess(@RequestParam String processId) {
        return service.byProcess(processId).stream().map(WorkflowDto.InstanceResponse::from).toList();
    }

    /** Danh sách quy trình đã ban hành để người dùng tạo yêu cầu (mọi user đăng nhập). */
    @GetMapping("/startable")
    public List<WorkflowDto.StartableProcess> startable() {
        return service.startable();
    }

    /** Theo dõi: danh sách phiên chạy + bước hiện tại + người giữ (Story 3.3). */
    @GetMapping("/all")
    public List<TaskDto.InstanceListItem> all() {
        return service.instances();
    }

    /** Hồ sơ do tôi tạo (Story 4.4 — mọi user đăng nhập). */
    @GetMapping("/mine")
    public List<TaskDto.InstanceListItem> mine(Authentication auth) {
        return service.myInstances(actor(auth));
    }

    /** Dòng thời gian các bước của một phiên chạy (Story 3.3). */
    @GetMapping("/{id}/timeline")
    public TaskDto.InstanceTimeline timeline(@PathVariable String id) {
        return service.timeline(id);
    }

    /** Tổng quan quy trình theo toàn bộ các bước (trạng thái + người + dữ liệu từng bước). */
    @GetMapping("/{id}/overview")
    public TaskDto.InstanceOverview overview(@PathVariable String id) {
        return service.overview(id);
    }

    /** Hủy phiên chạy đang chạy (Story 3.6). */
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable String id, @RequestBody(required = false) TaskDto.CancelRequest req,
                       Authentication auth) {
        service.cancel(id, req != null ? req.reason() : null, actor(auth));
    }
}
