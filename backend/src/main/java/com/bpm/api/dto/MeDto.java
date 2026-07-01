package com.bpm.api.dto;

/**
 * DTO cho màn "Tài khoản của tôi" (tự phục vụ). Tách khỏi UserResponse (quản trị) vì khác mục đích:
 * chỉ phơi thông tin của chính người đang đăng nhập + cờ có/không Employee (khoá ô Họ tên), có/không avatar.
 */
public final class MeDto {

    private MeDto() {}

    /**
     * Hồ sơ của tôi. fullName ưu tiên theo HỒ SƠ NHÂN SỰ (QLNS) nếu có liên kết Employee.
     * linkedEmployee=true → Họ tên do QLNS quản, FE khoá ô (chỉ sửa email/phone).
     */
    public record ProfileResponse(String username, String fullName, String email, String phone,
                                  String role, String roleCode, boolean linkedEmployee, boolean hasAvatar) {
    }

    /** Cập nhật hồ sơ. fullName chỉ áp dụng khi KHÔNG liên kết Employee (server tự bỏ qua nếu có Employee). */
    public record UpdateProfileRequest(String email, String phone, String fullName) {
    }

    /** Đổi mật khẩu của chính mình: cần xác thực mật khẩu hiện tại. */
    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    /** Kết quả upload avatar — trả id media để FE cache-bust nếu cần. */
    public record AvatarResponse(String mediaId, String url) {
    }

    /** Lưu tùy chọn giao diện theo tài khoản: accent (orange/blue/green/purple/teal/rose) + mode (light/dark). */
    public record ThemeRequest(String accent, String mode) {
    }
}
