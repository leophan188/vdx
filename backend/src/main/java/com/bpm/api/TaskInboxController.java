package com.bpm.api;

import com.bpm.api.dto.TaskDto;
import com.bpm.application.WorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hộp thư việc & xử lý việc (Story 3.2/3.4/3.5) — mọi user đăng nhập (không giới hạn ADMIN).
 */
@RestController
@RequestMapping("/api/v1")
public class TaskInboxController {

    private final WorkflowService workflow;

    public TaskInboxController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    /** Việc của tôi. */
    @GetMapping("/inbox")
    public List<TaskDto.InboxItem> inbox(Authentication auth) {
        return workflow.inbox(actor(auth));
    }

    /** Chi tiết một việc. */
    @GetMapping("/tasks/{id}")
    public TaskDto.Detail detail(@PathVariable String id) {
        return workflow.detail(id);
    }

    /** Hoàn thành việc với hành động + dữ liệu form → luồng tiến sang bước kế. */
    @PostMapping("/tasks/{id}/complete")
    public void complete(@PathVariable String id, @RequestBody TaskDto.CompleteRequest req, Authentication auth) {
        workflow.complete(id, req.action(), req.formData(), actor(auth));
    }

    /** Nhận một việc theo vai trò (claim). */
    @PostMapping("/tasks/{id}/claim")
    public void claim(@PathVariable String id, Authentication auth) {
        workflow.claim(id, actor(auth));
    }

    /** Xin gia hạn xử lý việc (cộng giờ vào SLA). */
    @PostMapping("/tasks/{id}/extend")
    public void extend(@PathVariable String id, @RequestBody TaskDto.ExtendRequest req, Authentication auth) {
        workflow.extend(id, req.hours(), req.reason(), actor(auth));
    }
}
