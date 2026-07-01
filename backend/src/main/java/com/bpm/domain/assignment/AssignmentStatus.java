package com.bpm.domain.assignment;

/** Trạng thái snapshot phân công của một việc (AD-14). */
public enum AssignmentStatus {
    /** Đã resolve về người giữ vị trí tại thời điểm giao. */
    ASSIGNED,
    /** Vị trí đang trống — giữ candidate-group đơn vị, không để task mất (FR-C08). */
    UNASSIGNED,
    /** Việc bị hủy theo phiên chạy (cascade khi hủy instance — Story 3.9). */
    CANCELLED
}
