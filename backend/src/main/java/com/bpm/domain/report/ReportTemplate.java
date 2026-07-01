package com.bpm.domain.report;

import java.util.List;

/**
 * Mẫu báo cáo CỐ ĐỊNH khai báo trong code (Epic 4, FR-D01/D03/D06).
 * Mỗi mẫu định nghĩa: khoá, tên hiển thị, danh sách cột đầu vào bắt buộc + kiểu dữ liệu.
 * Bộ công thức nằm trong {@link ExcelReportEngine} (tách khỏi registry để dễ test, lặp lại được).
 *
 * Mẫu đầu tiên = CHAM_CONG_OT: Tổng hợp chấm công / OT.
 * Cột bắt buộc: [Mã NV, Họ tên, Phòng ban, Ngày, Số giờ OT].
 * Công thức = tổng số giờ OT gộp theo nhân viên / phòng ban / kỳ (YYYY-MM).
 */
public enum ReportTemplate {

    CHAM_CONG_OT(
            "CHAM_CONG_OT",
            "Tổng hợp chấm công / OT",
            "Gộp tổng số giờ OT theo nhân viên, phòng ban và kỳ (tháng) từ file chấm công.",
            List.of(
                    new Column("Mã NV", ColumnType.TEXT),
                    new Column("Họ tên", ColumnType.TEXT),
                    new Column("Phòng ban", ColumnType.TEXT),
                    new Column("Ngày", ColumnType.DATE),
                    new Column("Số giờ OT", ColumnType.NUMBER)
            ));

    /** Kiểu dữ liệu cột để validate (FR-D02). */
    public enum ColumnType { TEXT, NUMBER, DATE }

    /** Khai báo một cột đầu vào bắt buộc. */
    public record Column(String header, ColumnType type) {
    }

    private final String key;
    private final String title;
    private final String description;
    private final List<Column> requiredColumns;

    ReportTemplate(String key, String title, String description, List<Column> requiredColumns) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.requiredColumns = requiredColumns;
    }

    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<Column> getRequiredColumns() { return requiredColumns; }

    /** Tra mẫu theo khoá; ném IllegalArgumentException nếu không tồn tại (FR-D01). */
    public static ReportTemplate byKey(String key) {
        for (ReportTemplate t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Không tìm thấy mẫu báo cáo: " + key);
    }
}
