package com.bpm.domain.org;

/**
 * Cổng (SPI) cho phép các feature khác chặn việc xóa một đơn vị đang được dùng,
 * mà không để org phụ thuộc trực tiếp vào feature đó (AD-12). Org định nghĩa port;
 * feature như position implement nó.
 */
public interface OrgUnitUsageGuard {

    /** True nếu đơn vị đang được feature này sử dụng (vd còn vị trí). */
    boolean isUnitInUse(String orgUnitId);

    /** Lý do hiển thị khi chặn xóa. */
    String reason();
}
