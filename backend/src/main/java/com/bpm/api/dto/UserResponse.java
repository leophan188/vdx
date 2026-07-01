package com.bpm.api.dto;

import com.bpm.domain.UserAccount;

public record UserResponse(String id, String username, String fullName, String email, String phone,
                           String status, String role, String roleCode) {
    public static UserResponse from(UserAccount a) {
        return from(a, null);
    }

    /** Tên ƯU TIÊN theo HỒ SƠ NHÂN SỰ (QLNS) nếu có liên kết — tránh QLTK lưu tên lệch với QLNS. */
    public static UserResponse from(UserAccount a, String employeeName) {
        String name = (employeeName != null && !employeeName.isBlank()) ? employeeName : a.getFullName();
        return new UserResponse(a.getId(), a.getUsername(), name, a.getEmail(), a.getPhone(),
                a.getStatus().name(), a.getRole(), a.getRoleCode());
    }
}
