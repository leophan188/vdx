package com.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName,
        String email,
        String phone,
        String role) {
}
