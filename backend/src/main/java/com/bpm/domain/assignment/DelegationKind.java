package com.bpm.domain.assignment;

/** Kiểu reassign chủ đích của người giữ việc (FR-C04). */
public enum DelegationKind {
    /** Uỷ quyền — nhờ người khác xử lý thay (thường tạm thời). */
    DELEGATE,
    /** Chuyển tiếp — bàn giao hẳn việc sang người khác. */
    FORWARD
}
