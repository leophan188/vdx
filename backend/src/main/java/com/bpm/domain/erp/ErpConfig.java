package com.bpm.domain.erp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Kết nối tới ERP (Odoo) để đọc chấm công {@code hr.attendance} — một bản ghi duy nhất (id = "default").
 *
 * API key KHÔNG BAO GIỜ được trả ra API: màn hình chỉ biết "đã đặt hay chưa" và gửi lên khi muốn đổi.
 * Cấp cho PlanX một tài khoản/API key RIÊNG chỉ đọc hr.attendance, đừng dùng tài khoản cá nhân — khoá
 * lộ thì chỉ mất quyền đọc chấm công, và thu hồi được mà không ảnh hưởng tới ai.
 */
@Entity
@Table(name = "erp_config")
public class ErpConfig {

    @Id
    private String id = "default";

    /** Gốc URL Odoo, vd https://erp.vmo.dev (không kèm /web/...). */
    @Column(length = 500)
    private String baseUrl;

    /** Tên database Odoo — một máy chủ Odoo có thể phục vụ nhiều database. */
    @Column(length = 200)
    private String dbName;

    @Column(length = 200)
    private String username;

    /** API key hoặc mật khẩu. Chỉ đi VÀO, không đi ra. */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /**
     * Đơn vị trên cây tổ chức ERP mà hệ thống này lấy dữ liệu — mọi luồng chỉ lấy trong phạm vi đó.
     * Lưu cả tên lẫn id: tên để người dùng đọc, id để gọi API. Không lấy toàn công ty vì PlanX là công
     * cụ của một đơn vị; kéo cả 997 nhân sự và 1.827 dự án về rồi lọc sau là tự chuốc dữ liệu rác.
     */
    @Column(name = "org_unit_name", length = 300)
    private String orgUnitName;

    @Column(name = "org_unit_erp_id")
    private Long orgUnitErpId;

    private Instant lastCheckAt;
    @Column(length = 500)
    private String lastCheckStatus;

    private Instant updatedAt;
    private String updatedBy;

    public ErpConfig() {
    }

    /**
     * Cập nhật cấu hình. {@code apiKey} để trống nghĩa là GIỮ NGUYÊN khoá cũ — màn hình không đọc được
     * khoá nên không thể gửi lại, coi trống là xoá thì mỗi lần sửa URL là mất khoá.
     */
    public void update(String baseUrl, String dbName, String username, String apiKey, String actor) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.dbName = trim(dbName);
        this.username = trim(username);
        if (apiKey != null && !apiKey.isBlank()) {
            this.apiKey = apiKey.trim();
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /** Ghi lại đơn vị đã tra được từ ERP. */
    public void setOrgUnit(String name, Long erpId) {
        this.orgUnitName = name == null ? null : name.trim();
        this.orgUnitErpId = erpId;
    }

    public void markChecked(String status) {
        this.lastCheckAt = Instant.now();
        this.lastCheckStatus = status;
    }

    /** Đủ thông tin để gọi ERP hay chưa. */
    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(dbName) && notBlank(username) && notBlank(apiKey);
    }

    public boolean hasApiKey() {
        return notBlank(apiKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * Quy mọi kiểu URL người dùng dán vào về GỐC Odoo.
     * Người dùng gần như luôn copy nguyên thanh địa chỉ đang mở
     * ({@code https://erp.vmo.dev/web#action=148&model=hr.attendance…}); giữ nguyên chuỗi đó thì lời
     * gọi RPC trỏ vào {@code /web#.../jsonrpc} và không bao giờ chạy.
     */
    private static String trimTrailingSlash(String s) {
        String t = trim(s);
        if (t == null) {
            return null;
        }
        int hash = t.indexOf('#');
        if (hash > 0) {
            t = t.substring(0, hash);
        }
        int web = t.indexOf("/web");
        if (web > 0) {
            t = t.substring(0, web);
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public String getOrgUnitName() { return orgUnitName; }
    public Long getOrgUnitErpId() { return orgUnitErpId; }

    public String getId() { return id; }
    public String getBaseUrl() { return baseUrl; }
    public String getDbName() { return dbName; }
    public String getUsername() { return username; }
    public String getApiKey() { return apiKey; }
    public Instant getLastCheckAt() { return lastCheckAt; }
    public String getLastCheckStatus() { return lastCheckStatus; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
