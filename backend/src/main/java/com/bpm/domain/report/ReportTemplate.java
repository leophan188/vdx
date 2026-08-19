package com.bpm.domain.report;

import java.util.List;

/**
 * Loại tool CỐ ĐỊNH khai báo trong code (Epic 4, FR-D01/D03/D06).
 * Mỗi loại định nghĩa: khoá, tên hiển thị, danh sách cột đầu vào + kiểu dữ liệu + bắt buộc hay tuỳ chọn.
 * Phần đọc/validate file dùng chung ở {@link ExcelReportEngine}; công thức riêng của từng tool nằm ở
 * engine riêng ({@link SunEffortEngine}) để test thuần, lặp lại được.
 *
 * NO_LUC_DU_AN_SUN: Tính toán nỗ lực dự án (Sun) — đọc sheet "Raw normalized" rồi gộp thành
 * 3 bảng kết quả (theo nhân sự · theo dự án · theo cặp nhân sự × dự án).
 */
public enum ReportTemplate {

    NO_LUC_DU_AN_SUN(
            "NO_LUC_DU_AN_SUN",
            "Tính toán nỗ lực dự án (Sun)",
            "Đọc sheet dữ liệu thô (Raw normalized) rồi tổng hợp nỗ lực (MD) và chi phí theo nhân sự, "
                    + "theo dự án và theo từng cặp nhân sự × dự án. Chi phí trong file được đối chiếu với Total MD × Manday.",
            List.of(
                    new Column("Date", ColumnType.DATE),
                    new Column("Email", ColumnType.TEXT),
                    new Column("Họ và tên", ColumnType.TEXT),
                    new Column("Position", ColumnType.TEXT),
                    new Column("Level", ColumnType.TEXT),
                    new Column("Vendor", ColumnType.TEXT),
                    new Column("Project Name", ColumnType.TEXT),
                    new Column("Total MD", ColumnType.NUMBER),
                    new Column("Expense (VNĐ)", ColumnType.NUMBER),
                    new Column("Manday (VNĐ)", ColumnType.NUMBER),
                    Column.optional("MD nhân sự tự khai", ColumnType.NUMBER),
                    Column.optional("Thời gian thực hiện", ColumnType.NUMBER)
            ));

    /** Kiểu dữ liệu cột để validate (FR-D02). */
    public enum ColumnType { TEXT, NUMBER, DATE }

    /**
     * Khai báo một cột đầu vào. {@code required=false} → cột tham khảo: thiếu cột hoặc ô trống đều không báo lỗi.
     * Header khớp không phân biệt hoa/thường, bỏ qua khoảng trắng thừa, và chấp nhận header dài hơn có cùng
     * phần đầu (vd "Thời gian thực hiện (chỉ điền số giờ, không điền ký tự khác)").
     */
    public record Column(String header, ColumnType type, boolean required) {

        public Column(String header, ColumnType type) {
            this(header, type, true);
        }

        public static Column optional(String header, ColumnType type) {
            return new Column(header, type, false);
        }
    }

    private final String key;
    private final String title;
    private final String description;
    private final List<Column> columns;

    ReportTemplate(String key, String title, String description, List<Column> columns) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.columns = columns;
    }

    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }

    /** Toàn bộ cột khai báo (kể cả tuỳ chọn) — dùng khi đọc dữ liệu. */
    public List<Column> getColumns() { return columns; }

    /** Chỉ các cột bắt buộc — dùng khi validate header/kiểu ô (FR-D02). */
    public List<Column> getRequiredColumns() {
        return columns.stream().filter(Column::required).toList();
    }

    /** Tra loại tool theo khoá; ném IllegalArgumentException nếu không tồn tại (FR-D01). */
    public static ReportTemplate byKey(String key) {
        for (ReportTemplate t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Không tìm thấy loại tool: " + key);
    }
}
