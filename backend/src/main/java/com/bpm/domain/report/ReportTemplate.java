package com.bpm.domain.report;

import java.util.List;

/**
 * Loại tool CỐ ĐỊNH khai báo trong code (Epic 4, FR-D01/D03/D06).
 * Mỗi loại định nghĩa: khoá, tên hiển thị, danh sách cột đầu vào + kiểu dữ liệu + bắt buộc hay tuỳ chọn.
 * Phần đọc/validate file dùng chung ở {@link ExcelReportEngine}; công thức riêng của từng tool nằm ở
 * engine riêng ({@link SunEffortEngine}) để test thuần, lặp lại được.
 *
 * PHAN_BO_CHI_PHI_SUN_ITS: Phân bổ chi phí nhân sự (Sun ITS) — đọc sheet "Raw normalized" rồi gộp thành
 * 3 bảng kết quả (theo nhân sự · theo dự án · theo cặp nhân sự × dự án).
 */
public enum ReportTemplate {

    PHAN_BO_CHI_PHI_SUN_ITS(
            "PHAN_BO_CHI_PHI_SUN_ITS",
            "Phân bổ chi phí nhân sự (Sun ITS)",
            "Đọc sheet dữ liệu thô (Raw normalized) rồi phân bổ nỗ lực (MD) và chi phí theo nhân sự, "
                    + "theo dự án và theo từng cặp nhân sự × dự án. Chi phí trong file được đối chiếu với "
                    + "Total MD × Manday; dòng tổng cuối bảng được bỏ qua tự động.",
            List.of(
                    Column.key("Date", ColumnType.DATE),
                    Column.mayBeEmpty("Email", ColumnType.TEXT),
                    Column.key("Họ và tên", ColumnType.TEXT),
                    // Thuộc tính mô tả: bảng chấm công thật hay bỏ trống vài dòng → không chặn cả file vì chúng.
                    Column.mayBeEmpty("Position", ColumnType.TEXT),
                    Column.mayBeEmpty("Level", ColumnType.TEXT),
                    Column.mayBeEmpty("Vendor", ColumnType.TEXT),
                    // Trống thì dồn vào nhóm "(Chưa gán dự án)" kèm cảnh báo, xem SunEffortEngine.
                    Column.mayBeEmpty("Project Name", ColumnType.TEXT),
                    new Column("Total MD", ColumnType.NUMBER),
                    new Column("Expense (VNĐ)", ColumnType.NUMBER),
                    new Column("Manday (VNĐ)", ColumnType.NUMBER),
                    Column.optional("MD nhân sự tự khai", ColumnType.NUMBER),
                    Column.optional("Thời gian thực hiện", ColumnType.NUMBER)
            ));

    /** Kiểu dữ liệu cột để validate (FR-D02). */
    public enum ColumnType { TEXT, NUMBER, DATE }

    /** Mức chặt chẽ của một cột — tách rõ "phải có cột" với "ô phải có giá trị". */
    public enum Requirement {
        /**
         * Cột ĐỊNH DANH dòng dữ liệu (Date, Họ và tên): phải có cột, ô phải có giá trị.
         * Dòng mà MỌI cột KEY đều trống thì không phải dữ liệu — điển hình là dòng tổng cuối bảng
         * (chỉ có mỗi ô =SUM(...)) hay dòng ghi chú — nên bị bỏ qua thay vì báo lỗi.
         */
        KEY,
        /** Phải có cột và ô phải có giá trị. */
        REQUIRED,
        /**
         * Phải có cột, nhưng ô để trống thì bỏ qua. Dùng cho thuộc tính mô tả (Position/Level/Vendor…):
         * bảng chấm công thật hay thiếu vài ô, chặn cả file vì chúng thì vô lý khi số liệu vẫn tính đúng.
         */
        MAY_BE_EMPTY,
        /** Cột tham khảo: thiếu cột hoặc ô trống đều không báo lỗi. */
        OPTIONAL
    }

    /**
     * Khai báo một cột đầu vào.
     * Header khớp không phân biệt hoa/thường, bỏ qua khoảng trắng thừa, và chấp nhận header dài hơn có cùng
     * phần đầu (vd "Thời gian thực hiện (chỉ điền số giờ, không điền ký tự khác)").
     */
    public record Column(String header, ColumnType type, Requirement requirement) {

        public Column(String header, ColumnType type) {
            this(header, type, Requirement.REQUIRED);
        }

        public static Column key(String header, ColumnType type) {
            return new Column(header, type, Requirement.KEY);
        }

        public static Column mayBeEmpty(String header, ColumnType type) {
            return new Column(header, type, Requirement.MAY_BE_EMPTY);
        }

        public static Column optional(String header, ColumnType type) {
            return new Column(header, type, Requirement.OPTIONAL);
        }

        /** File bắt buộc phải có cột này. */
        public boolean required() {
            return requirement != Requirement.OPTIONAL;
        }

        /** Ô không được để trống. */
        public boolean valueRequired() {
            return requirement == Requirement.KEY || requirement == Requirement.REQUIRED;
        }

        /** Dùng để nhận biết dòng có phải dữ liệu thật hay không. */
        public boolean identity() {
            return requirement == Requirement.KEY;
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
