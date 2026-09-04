package com.bpm.domain.report;

import java.util.List;

/**
 * Loại tool CỐ ĐỊNH khai báo trong code (Epic 4, FR-D01/D03/D06).
 * Mỗi loại định nghĩa: khoá, tên hiển thị, danh sách cột đầu vào + kiểu dữ liệu + bắt buộc hay tuỳ chọn.
 * Phần đọc/validate file dùng chung ở {@link ExcelReportEngine}; công thức riêng của từng tool nằm ở
 * engine riêng ({@link SunEffortEngine}) để test thuần, lặp lại được.
 *
 * CONG_KHACH_HANG: công khách hàng ghi nhận theo tháng — nguồn thứ hai của màn Kiểm soát giờ công.
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
            )),

    /**
     * Công khách hàng ghi nhận, dùng cho màn Kiểm soát giờ công. Kỳ (tháng) KHÔNG nằm trong file mà
     * chọn trên màn hình: file khách hàng gửi theo tháng, ghi thêm cột tháng chỉ tạo thêm một chỗ để
     * sai lệch với thư mục kỳ đang import.
     */
    CONG_KHACH_HANG(
            "CONG_KHACH_HANG",
            "Công khách hàng ghi nhận (theo tháng)",
            "Đọc số công khách hàng ghi nhận cho từng nhân sự trong một tháng, lưu lại theo kỳ rồi "
                    + "đối soát với chấm công đọc từ ERP.",
            List.of(
                    Column.mayBeEmpty("Mã NV", ColumnType.TEXT),
                    Column.key("Họ và tên", ColumnType.TEXT),
                    new Column("Số công", ColumnType.NUMBER),
                    Column.optional("Ghi chú", ColumnType.TEXT)
            ),
            false);

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
    /** Có xuất hiện ở danh sách chọn tool của màn Công cụ hay không. */
    private final boolean standaloneTool;

    ReportTemplate(String key, String title, String description, List<Column> columns) {
        this(key, title, description, columns, true);
    }

    ReportTemplate(String key, String title, String description, List<Column> columns, boolean standaloneTool) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.columns = columns;
        this.standaloneTool = standaloneTool;
    }

    /**
     * Mẫu đứng riêng thành một tool ở màn Công cụ (chọn tool → import → xem kết quả). Mẫu phục vụ một
     * màn khác — như biểu mẫu công khách hàng của màn Kiểm soát giờ công — thì KHÔNG: để nó lọt vào
     * danh sách chọn tool là mời người dùng import file vào một chỗ không xử lý được nó.
     */
    public boolean isStandaloneTool() { return standaloneTool; }

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
