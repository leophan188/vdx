package com.bpm.domain.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot phân công của một việc — NGUỒN SỰ THẬT cho assignee (AD-14).
 *
 * <p>Lưu người + vị trí <b>tại thời điểm giao</b>. Snapshot là BẤT BIẾN: đổi người giữ vị trí
 * sau đó KHÔNG sửa các bản ghi này (FR-C05) — việc đang chạy vẫn ở người cũ; chỉ việc mới
 * mới resolve về người giữ hiện hành. Flowable assignee chỉ mirror snapshot này qua AssignmentPort.
 */
@Entity
@Table(name = "task_assignment")
public class TaskAssignment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Định danh việc (opaque GĐ1; sau này = Flowable task id). */
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "position_id", length = 36, nullable = false)
    private String positionId;

    /** Người được snapshot lúc giao; null khi vị trí trống (UNASSIGNED). */
    @Column(name = "assignee_user_id", length = 36)
    private String assigneeUserId;

    /** Đơn vị làm candidate-group khi vị trí trống (FR-C08); null khi đã ASSIGNED. */
    @Column(name = "candidate_group_org_unit_id", length = 36)
    private String candidateGroupOrgUnitId;

    /** Người uỷ quyền/chuyển tiếp ngay trước (FR-C04); null nếu chưa từng reassign. */
    @Column(name = "delegated_from_user_id", length = 36)
    private String delegatedFromUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected TaskAssignment() {
    }

    private TaskAssignment(String taskId, String positionId, String assigneeUserId,
                           String candidateGroupOrgUnitId, AssignmentStatus status) {
        this.id = UUID.randomUUID().toString();
        this.taskId = taskId;
        this.positionId = positionId;
        this.assigneeUserId = assigneeUserId;
        this.candidateGroupOrgUnitId = candidateGroupOrgUnitId;
        this.status = status;
        this.assignedAt = Instant.now();
    }

    /** Snapshot khi vị trí có người giữ hiện hành. */
    public static TaskAssignment assigned(String taskId, String positionId, String assigneeUserId) {
        return new TaskAssignment(taskId, positionId, assigneeUserId, null, AssignmentStatus.ASSIGNED);
    }

    /** Snapshot khi vị trí đang trống — giữ candidate-group đơn vị, không để task mất (FR-C08). */
    public static TaskAssignment unassigned(String taskId, String positionId, String orgUnitId) {
        return new TaskAssignment(taskId, positionId, null, orgUnitId, AssignmentStatus.UNASSIGNED);
    }

    /**
     * Reassign chủ đích (uỷ quyền/chuyển tiếp) sang người khác trên việc đang chạy (FR-C04, AD-14).
     * Khác với snapshot bất biến của 1.5: đây là hành động cố ý của người giữ, KHÔNG phải đổi cơ cấu.
     */
    public void reassignTo(String newAssigneeUserId) {
        this.delegatedFromUserId = this.assigneeUserId;
        this.assigneeUserId = newAssigneeUserId;
        this.candidateGroupOrgUnitId = null;
        this.status = AssignmentStatus.ASSIGNED;
    }

    /** Gán tạm một việc đang ở hàng đợi vị trí trống cho một người (FR-C08): UNASSIGNED → ASSIGNED. */
    public void claim(String userId) {
        this.assigneeUserId = userId;
        this.candidateGroupOrgUnitId = null;
        this.status = AssignmentStatus.ASSIGNED;
    }

    /** Leo thang việc trong hàng đợi lên đơn vị cha (FR-C08): vẫn UNASSIGNED, đổi candidate-group. */
    public void escalateTo(String parentOrgUnitId) {
        this.candidateGroupOrgUnitId = parentOrgUnitId;
        this.status = AssignmentStatus.UNASSIGNED;
    }

    /** Hủy việc theo phiên chạy (cascade khi hủy instance — Story 3.9). */
    public void cancel() {
        this.status = AssignmentStatus.CANCELLED;
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getPositionId() { return positionId; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public String getCandidateGroupOrgUnitId() { return candidateGroupOrgUnitId; }
    public String getDelegatedFromUserId() { return delegatedFromUserId; }
    public AssignmentStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
}
