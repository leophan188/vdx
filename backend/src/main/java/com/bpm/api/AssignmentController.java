package com.bpm.api;

import com.bpm.api.dto.AssignmentDto;
import com.bpm.application.AssignmentService;
import com.bpm.domain.assignment.TaskAssignment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lõi phân công — giao việc cho vị trí + tra cứu snapshot (AD-14). System-facing GĐ1: chỉ ROLE_ADMIN.
 * Phân công lộ ra người dùng cuối ở Epic 3 (hộp thư "Việc của tôi").
 */
@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<AssignmentDto.Response> assign(@Valid @RequestBody AssignmentDto.AssignRequest req,
                                                         Authentication auth) {
        TaskAssignment a = service.assignTaskToPosition(req.taskId(), req.positionId(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentDto.Response.from(a));
    }

    @PostMapping("/{taskId}/reassign")
    public ResponseEntity<AssignmentDto.Response> reassign(@PathVariable String taskId,
                                                           @Valid @RequestBody AssignmentDto.ReassignRequest req,
                                                           Authentication auth) {
        TaskAssignment a = service.reassignTask(taskId, req.toUserId(), req.kind(), req.reason(), actor(auth));
        return ResponseEntity.ok(AssignmentDto.Response.from(a));
    }

    @GetMapping("/unassigned")
    public List<AssignmentDto.Response> unassignedQueue(@RequestParam String orgUnitId) {
        return service.unassignedQueue(orgUnitId).stream().map(AssignmentDto.Response::from).toList();
    }

    @PostMapping("/{taskId}/claim")
    public ResponseEntity<AssignmentDto.Response> claim(@PathVariable String taskId,
                                                        @Valid @RequestBody AssignmentDto.ClaimRequest req,
                                                        Authentication auth) {
        TaskAssignment a = service.claimUnassigned(taskId, req.toUserId(), actor(auth));
        return ResponseEntity.ok(AssignmentDto.Response.from(a));
    }

    @PostMapping("/{taskId}/escalate")
    public ResponseEntity<AssignmentDto.Response> escalate(@PathVariable String taskId, Authentication auth) {
        TaskAssignment a = service.escalateUnassigned(taskId, actor(auth));
        return ResponseEntity.ok(AssignmentDto.Response.from(a));
    }

    @GetMapping("/by-task/{taskId}")
    public ResponseEntity<AssignmentDto.Response> byTask(@PathVariable String taskId) {
        return service.byTask(taskId)
                .map(AssignmentDto.Response::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
