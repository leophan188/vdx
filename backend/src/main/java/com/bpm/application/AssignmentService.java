package com.bpm.application;

import com.bpm.domain.assignment.AssignmentPort;
import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.assignment.Delegation;
import com.bpm.domain.assignment.DelegationKind;
import com.bpm.domain.assignment.TaskAssignment;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.domain.position.Substitute;
import com.bpm.infrastructure.DelegationRepository;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.SubstituteRepository;
import com.bpm.infrastructure.TaskAssignmentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lõi phân công (AD-7, AD-14): khi giao việc, resolve người giữ vị trí hiện hành và
 * SNAPSHOT vào `TASK_ASSIGNMENT` (nguồn sự thật), rồi mirror sang Flowable qua AssignmentPort
 * — TẤT CẢ trong một giao dịch.
 *
 * <p>Snapshot bất biến: đổi người giữ vị trí sau đó không sửa các snapshot đã ghi (FR-C05),
 * nên việc đang chạy giữ nguyên người cũ; chỉ việc giao mới mới resolve về người giữ hiện hành.
 */
@Service
public class AssignmentService {

    private final TaskAssignmentRepository assignmentRepo;
    private final PositionRepository positionRepo;
    private final SubstituteRepository substituteRepo;
    private final DelegationRepository delegationRepo;
    private final OrgUnitRepository orgUnitRepo;
    private final UserAccountRepository userRepo;
    private final AssignmentPort assignmentPort;
    private final AuditPort auditPort;

    public AssignmentService(TaskAssignmentRepository assignmentRepo, PositionRepository positionRepo,
                             SubstituteRepository substituteRepo, DelegationRepository delegationRepo,
                             OrgUnitRepository orgUnitRepo, UserAccountRepository userRepo,
                             AssignmentPort assignmentPort, AuditPort auditPort) {
        this.assignmentRepo = assignmentRepo;
        this.positionRepo = positionRepo;
        this.substituteRepo = substituteRepo;
        this.delegationRepo = delegationRepo;
        this.orgUnitRepo = orgUnitRepo;
        this.userRepo = userRepo;
        this.assignmentPort = assignmentPort;
        this.auditPort = auditPort;
    }

    /**
     * Giao một việc cho một vị trí: snapshot người giữ hiện hành + mirror Flowable trong một giao dịch.
     * Vị trí trống → UNASSIGNED + candidate-group đơn vị (không để task mất, FR-C08).
     */
    @Transactional
    public TaskAssignment assignTaskToPosition(String taskId, String positionId, String actor) {
        Position position = positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"));
        if (assignmentRepo.findByTaskId(taskId).isPresent()) {
            throw new IllegalStateException("Việc đã được giao: " + taskId);
        }

        // Người thay thế đang bật được ưu tiên hơn người giữ (giữ vắng — FR-C04); nếu không có thì người giữ.
        String substitute = substituteRepo.findByPositionIdAndActiveTrue(positionId)
                .map(Substitute::getSubstituteUserId).orElse(null);
        String resolved = substitute != null ? substitute : position.getCurrentHolderUserId();
        TaskAssignment snapshot;
        if (resolved != null) {
            snapshot = assignmentRepo.save(TaskAssignment.assigned(taskId, positionId, resolved));
            assignmentPort.mirrorAssignee(taskId, resolved);
            auditPort.record("TASK_ASSIGNED", "TaskAssignment", snapshot.getId(), actor,
                    "task=" + taskId + ", position=" + positionId + ", assignee=" + resolved
                            + (substitute != null ? " (substitute)" : ""));
        } else {
            String orgUnitId = position.getOrgUnitId();
            snapshot = assignmentRepo.save(TaskAssignment.unassigned(taskId, positionId, orgUnitId));
            assignmentPort.mirrorCandidateGroup(taskId, orgUnitId);
            auditPort.record("TASK_UNASSIGNED", "TaskAssignment", snapshot.getId(), actor,
                    "task=" + taskId + ", position=" + positionId + ", candidateGroup=" + orgUnitId);
            alertSuperior(orgUnitId, taskId, actor);
        }
        return snapshot;
    }

    /** Cảnh báo cấp trên (đơn vị cha) khi việc rơi vào hàng đợi trống — không để việc trôi âm thầm (FR-C08). */
    private void alertSuperior(String orgUnitId, String taskId, String actor) {
        String parentId = orgUnitRepo.findById(orgUnitId).map(OrgUnit::getParentId).orElse(null);
        String target = parentId != null ? parentId : orgUnitId; // gốc thì cảnh báo chính đơn vị
        auditPort.record("TASK_UNASSIGNED_ALERT", "OrgUnit", target, actor,
                "task=" + taskId + " vào hàng đợi chưa-có-người-nhận của đơn vị " + orgUnitId);
    }

    /** Hàng đợi "chưa có người nhận" của một đơn vị (FR-C08). */
    @Transactional(readOnly = true)
    public List<TaskAssignment> unassignedQueue(String orgUnitId) {
        return assignmentRepo.findByStatusAndCandidateGroupOrgUnitId(AssignmentStatus.UNASSIGNED, orgUnitId);
    }

    /** Gán tạm một việc trong hàng đợi cho một người (FR-C08): UNASSIGNED → ASSIGNED qua AssignmentPort. */
    @Transactional
    public TaskAssignment claimUnassigned(String taskId, String toUserId, String actor) {
        TaskAssignment a = requireUnassigned(taskId);
        if (!userRepo.existsById(toUserId)) {
            throw new IllegalArgumentException("Tài khoản không tồn tại");
        }
        a.claim(toUserId);
        assignmentRepo.save(a);
        assignmentPort.mirrorAssignee(taskId, toUserId);
        auditPort.record("TASK_CLAIMED", "TaskAssignment", a.getId(), actor,
                "task=" + taskId + ", assignee=" + toUserId);
        return a;
    }

    /** Định tuyến việc trong hàng đợi lên đơn vị cha (FR-C08): vẫn UNASSIGNED, đổi candidate-group. */
    @Transactional
    public TaskAssignment escalateUnassigned(String taskId, String actor) {
        TaskAssignment a = requireUnassigned(taskId);
        String current = a.getCandidateGroupOrgUnitId();
        String parentId = orgUnitRepo.findById(current).map(OrgUnit::getParentId).orElse(null);
        if (parentId == null) {
            throw new IllegalStateException("Đơn vị đã ở gốc, không thể leo thang cao hơn");
        }
        a.escalateTo(parentId);
        assignmentRepo.save(a);
        assignmentPort.mirrorCandidateGroup(taskId, parentId);
        auditPort.record("TASK_ESCALATED", "TaskAssignment", a.getId(), actor,
                "task=" + taskId + ", from=" + current + ", to=" + parentId);
        return a;
    }

    private TaskAssignment requireUnassigned(String taskId) {
        TaskAssignment a = assignmentRepo.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Việc chưa được giao: " + taskId));
        if (a.getStatus() != AssignmentStatus.UNASSIGNED) {
            throw new IllegalStateException("Việc không ở hàng đợi chưa-có-người-nhận: " + taskId);
        }
        return a;
    }

    /**
     * Uỷ quyền/chuyển tiếp một việc đang chạy sang người khác (FR-C04, AD-14).
     * Đi qua cùng AssignmentPort (app + Flowable một giao dịch). Guard: không tự uỷ cho mình (self),
     * không reassign tới ai đã từng giữ việc (chống lặp A→B→A).
     */
    @Transactional
    public TaskAssignment reassignTask(String taskId, String toUserId, DelegationKind kind,
                                       String reason, String actor) {
        TaskAssignment assignment = assignmentRepo.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Việc chưa được giao: " + taskId));
        String from = assignment.getAssigneeUserId();
        if (from == null) {
            throw new IllegalStateException("Việc đang ở hàng đợi vị trí trống, không thể uỷ quyền");
        }
        if (toUserId.equals(from)) {
            throw new IllegalArgumentException("Không thể uỷ quyền/chuyển tiếp cho chính người đang giữ việc");
        }
        if (participantsOf(taskId, from).contains(toUserId)) {
            throw new IllegalStateException("Chuỗi uỷ quyền không được lặp về người đã từng giữ việc");
        }

        delegationRepo.save(new Delegation(taskId, from, toUserId, kind, reason));
        assignment.reassignTo(toUserId);
        assignmentRepo.save(assignment);
        assignmentPort.mirrorAssignee(taskId, toUserId);
        auditPort.record("TASK_" + kind.name(), "TaskAssignment", assignment.getId(), actor,
                "task=" + taskId + ", from=" + from + ", to=" + toUserId
                        + (reason != null ? ", reason=" + reason : ""));
        return assignment;
    }

    /** Tập tất cả người đã từng giữ việc (assignee hiện hành + mọi from/to trong chuỗi uỷ quyền). */
    private Set<String> participantsOf(String taskId, String currentAssignee) {
        Set<String> seen = new HashSet<>();
        seen.add(currentAssignee);
        for (Delegation d : delegationRepo.findByTaskIdOrderByCreatedAtAsc(taskId)) {
            seen.add(d.getFromUserId());
            seen.add(d.getToUserId());
        }
        return seen;
    }

    @Transactional(readOnly = true)
    public Optional<TaskAssignment> byTask(String taskId) {
        return assignmentRepo.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskAssignment> byPosition(String positionId) {
        return assignmentRepo.findByPositionId(positionId);
    }
}
