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

    /** "https://erp.vmo.dev/" và "https://erp.vmo.dev" phải cho ra cùng một endpoint. */
    private static String trimTrailingSlash(String s) {
        String t = trim(s);
        while (t != null && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

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
