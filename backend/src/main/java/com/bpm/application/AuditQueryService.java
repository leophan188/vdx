package com.bpm.application;

import com.bpm.domain.audit.AuditEvent;
import com.bpm.infrastructure.AuditEventRepository;
import com.bpm.infrastructure.TaskAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Truy vết kiểm toán (AD-6, FR-I01, FR-I02) — CHỈ đọc. Ghi audit đi qua {@code AuditPort}.
 * Không dùng Flowable history (`ACT_HI_*`) làm audit nghiệp vụ — mọi vết FR-I đi qua đây.
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository auditRepo;
    private final TaskAssignmentRepository assignmentRepo;

    public AuditQueryService(AuditEventRepository auditRepo, TaskAssignmentRepository assignmentRepo) {
        this.auditRepo = auditRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /** Vết kiểm toán của một đối tượng (type+id) theo thứ tự thời gian (FR-I01). */
    @Transactional(readOnly = true)
    public List<AuditEvent> trail(String objectType, String objectId) {
        return auditRepo.findByObjectTypeAndObjectIdOrderByCreatedAtAsc(objectType, objectId);
    }

    /** 200 sự kiện kiểm toán gần nhất toàn hệ thống (duyệt nhanh). */
    @Transactional(readOnly = true)
    public List<AuditEvent> recent() {
        return auditRepo.findTop200ByOrderByCreatedAtDesc();
    }

    /**
     * Vết phân công/phê duyệt của một nhiệm vụ theo thời gian (FR-I02): giao · uỷ quyền · chuyển tiếp ·
     * gán tạm · leo thang … Mỗi task có một {@code TaskAssignment}; mọi sự kiện audit cùng objectId của nó.
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> trailForTask(String taskId) {
        return assignmentRepo.findByTaskId(taskId)
                .map(a -> trail("TaskAssignment", a.getId()))
                .orElseGet(List::of);
    }
}
