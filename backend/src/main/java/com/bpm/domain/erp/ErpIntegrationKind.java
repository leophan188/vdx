package com.bpm.domain.erp;

/**
 * Các luồng dữ liệu ERP hệ thống định lấy về. Khai cố định trong code chứ không cho tạo tự do: mỗi
 * luồng còn cần phần xử lý riêng ở tầng nghiệp vụ, một bản ghi cấu hình trỏ tới model lạ thì cũng
 * chẳng ai đọc được.
 *
 * {@code suggestedModel} chỉ là gợi ý điền sẵn — bản Odoo mỗi nơi một khác nên link người dùng dán
 * vào mới là căn cứ cuối cùng.
 */
public enum ErpIntegrationKind {

    PROJECTS("Các dự án", "Danh sách dự án bên ERP", "project.project"),
    BILLABLE("Quản lý billable của dự án", "Phân bổ nhân sự / chi phí tính phí theo dự án", ""),
    ORG_EMPLOYEE("Sơ đồ tổ chức & nhân sự", "Phòng ban, vị trí và hồ sơ nhân sự", "hr.employee"),
    ATTENDANCE("Công nhân sự", "Chấm công theo ngày — đang dùng ở màn Kiểm soát giờ công", "hr.attendance"),
    RECRUITMENT("Tuyển dụng", "Tin tuyển dụng và ứng viên", "hr.applicant");

    private final String label;
    private final String description;
    private final String suggestedModel;

    ErpIntegrationKind(String label, String description, String suggestedModel) {
        this.label = label;
        this.description = description;
        this.suggestedModel = suggestedModel;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String suggestedModel() { return suggestedModel; }
}
