package com.bpm.domain.assignment;

/**
 * Cổng mirror phân công sang Flowable (AD-14).
 *
 * <p>App (`TASK_ASSIGNMENT`) là NGUỒN SỰ THẬT; Flowable assignee/candidate chỉ là bản mirror,
 * và mọi việc set assignee đều đi qua DUY NHẤT cổng này — ghi app + Flowable trong CÙNG giao dịch.
 * Mọi reassign/uỷ quyền/chuyển tiếp/thay thế (FR-C04, Story 1.6) cũng đi qua cổng này.
 *
 * <p>Engine Flowable nối ở Epic 2/3; tới đó chỉ thay adapter, không đụng AssignmentService.
 */
public interface AssignmentPort {

    /** Mirror assignee của việc về một người cụ thể (đã resolve từ vị trí). */
    void mirrorAssignee(String taskId, String assigneeUserId);

    /** Mirror candidate-group (đơn vị) khi vị trí trống — không để task mất khỏi mọi inbox (FR-C08). */
    void mirrorCandidateGroup(String taskId, String orgUnitId);
}
