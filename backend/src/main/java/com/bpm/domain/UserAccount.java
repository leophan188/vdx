package com.bpm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tài khoản nội bộ (GĐ1, không SSO/AD — AD-9).
 * Conventions: PK UUID (chuỗi 36 ký tự), cột snake_case, thời gian UTC.
 * [ASSUMPTION] dùng UUID v4 ở GĐ1; chuyển sang UUID v7 (sắp xếp thời gian) khi đưa generator vào core.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Vai trò đơn giản cho Story 1.1 (ADMIN/USER). RBAC đầy đủ vị trí→vai trò ở Story 1.4. */
    @Column(name = "role", length = 30, nullable = false)
    private String role = "USER";

    /**
     * Vai trò CHỨC NĂNG được gán trực tiếp cho tài khoản (mã Role.code) — quyết định bộ FEAT_* (phân quyền ma trận).
     * Null → nhân viên thường (nhận bộ quyền mặc định). ADMIN bỏ qua, luôn có mọi chức năng.
     */
    @Column(name = "function_role_code", length = 40)
    private String roleCode;

    /** ID media (PostMedia) của ảnh đại diện do người dùng tự tải lên; null → dùng avatar chữ cái. */
    @Column(name = "avatar_path", length = 200)
    private String avatarPath;

    /**
     * Mật khẩu hiện tại có phải MẬT KHẨU MẶC ĐỊNH không (để ép đổi khi đăng nhập).
     * NULLABLE (quy ước H2): null = chưa xác định → coi như KHÔNG ép. Cờ được tính lại mỗi lần login
     * (có raw password) và đặt false ngay khi người dùng tự đổi mật khẩu.
     */
    @Column(name = "password_is_default")
    private Boolean passwordIsDefault;

    /**
     * Bộ màu accent giao diện đã chọn (orange/blue/green/purple/teal/rose).
     * NULLABLE (quy ước H2): null → FE dùng skin mặc định.
     */
    @Column(name = "theme_accent", length = 20)
    private String themeAccent;

    /**
     * Chế độ giao diện đã chọn (light/dark).
     * NULLABLE (quy ước H2): null → FE giữ lựa chọn cục bộ / mặc định light.
     */
    @Column(name = "theme_mode", length = 10)
    private String themeMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, String fullName, String role) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    public Boolean getPasswordIsDefault() { return passwordIsDefault; }
    public void setPasswordIsDefault(Boolean passwordIsDefault) { this.passwordIsDefault = passwordIsDefault; }
    public String getThemeAccent() { return themeAccent; }
    public void setThemeAccent(String themeAccent) { this.themeAccent = themeAccent; }
    public String getThemeMode() { return themeMode; }
    public void setThemeMode(String themeMode) { this.themeMode = themeMode; }
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isLocked() { return status == AccountStatus.LOCKED; }
}
