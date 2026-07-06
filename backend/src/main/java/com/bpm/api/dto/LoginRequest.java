package com.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        /** "Ghi nhớ đăng nhập" — bật → phát cookie remember-me (giữ đăng nhập qua đóng trình duyệt). */
        Boolean rememberMe) {
}
