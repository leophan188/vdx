package com.bpm.domain.erp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Một luồng dữ liệu lấy từ ERP (Odoo): dự án, billable, sơ đồ tổ chức & nhân sự, chấm công, tuyển dụng.
 *
 * Người dùng chỉ có trong tay ĐƯỜNG LINK đang mở trên trình duyệt
 * ({@code https://erp.vmo.dev/web#action=148&model=hr.attendance&view_type=list}), còn thứ API cần là
 * tên MODEL nằm lẫn trong link đó. Nên màn cấu hình nhận link rồi tự tách model ra; ai biết model thì
 * gõ thẳng cũng được. Khoá kết nối (URL gốc, database, tài khoản) dùng CHUNG ở {@link ErpConfig} —
 * mỗi luồng một bộ đăng nhập là cách chắc chắn để năm chỗ khai sai bốn.
 */
@Entity
@Table(name = "erp_integration")
public class ErpIntegration {

    /** Khoá cố định trong code, xem {@link ErpIntegrationKind}. */
    @Id
    @Column(name = "id", length = 50)
    private String id;

    /** Link người dùng dán từ trình duyệt — giữ nguyên để còn mở lại đối chiếu. */
    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    /** Tên model Odoo, tách từ link hoặc gõ tay (vd hr.attendance, project.project). */
    @Column(name = "model_name", length = 200)
    private String modelName;

    /** Có dùng luồng này hay không — khai sẵn nhưng chưa bật thì hệ thống không đụng tới. */
    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_check_status", length = 500)
    private String lastCheckStatus;

    /** Số bản ghi đọc thử được ở lần kiểm tra gần nhất — bằng chứng là link trỏ đúng chỗ. */
    @Column(name = "last_count")
    private Integer lastCount;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    protected ErpIntegration() {
    }

    public ErpIntegration(String id) {
        this.id = id;
    }

    public void update(String linkUrl, String modelName, boolean enabled, String actor) {
        this.linkUrl = trim(linkUrl);
        String model = trim(modelName);
        // Link dán vào thường đã chứa model; chỉ khi không tách được mới cần người dùng gõ tay.
        this.modelName = model == null || model.isBlank() ? modelFromLink(this.linkUrl) : model;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    public void markChecked(String status, Integer count) {
        this.lastCheckAt = Instant.now();
        this.lastCheckStatus = status;
        this.lastCount = count;
    }

    /**
     * Tách {@code model=<tên>} khỏi link Odoo. Tên model nằm sau dấu #, tức phần KHÔNG được gửi lên máy
     * chủ khi mở trang — chỉ có thể lấy bằng cách đọc chính chuỗi người dùng dán vào.
     */
    public static String modelFromLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[#&?]model=([A-Za-z0-9_.]+)").matcher(link);
        return m.find() ? m.group(1) : null;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    public String getId() { return id; }
    public String getLinkUrl() { return linkUrl; }
    public String getModelName() { return modelName; }
    public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
    public Instant getLastCheckAt() { return lastCheckAt; }
    public String getLastCheckStatus() { return lastCheckStatus; }
    public Integer getLastCount() { return lastCount; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
