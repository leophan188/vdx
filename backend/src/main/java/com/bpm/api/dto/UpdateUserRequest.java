package com.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Cập nhật thông tin tài khoản (không đổi username/mật khẩu — đổi MK qua endpoint riêng). */
public record UpdateUserRequest(
        @NotBlank String fullName,
        String email,
        String phone,
        String role,
        String roleCode) {
}
