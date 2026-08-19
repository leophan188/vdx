package com.bpm.domain.report;

import java.util.List;

/**
 * Kết quả một lần chạy tool, dạng TRUNG LẬP để hiển thị thẳng lên màn hình (FR-D03).
 * Không gắn với loại tool nào → thêm tool mới chỉ cần sinh ReportResult, frontend không phải sửa.
 *
 * metrics: dải số nổi bật (tổng MD, tổng chi phí…) · tables: các bảng kết quả (mỗi bảng = 1 sheet của file .xlsx)
 * · warnings: cảnh báo mềm, không chặn chạy (vd chi phí trong file lệch với Total MD × Manday).
 */
public record ReportResult(List<Metric> metrics, List<Table> tables, List<String> warnings) {

    /** Một ô số nổi bật trên đầu màn kết quả. */
    public record Metric(String label, String value) {
    }

    /**
     * Một bảng kết quả. {@code types} song song với {@code columns}: TEXT | NUMBER | MONEY —
     * frontend dùng để canh phải và định dạng số.
     */
    public record Table(String key, String title, List<String> columns, List<String> types,
                        List<List<Object>> rows) {
    }
}
