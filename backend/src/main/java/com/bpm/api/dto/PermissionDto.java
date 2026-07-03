package com.bpm.api.dto;

import java.util.List;

/** DTO phân quyền chức năng — vai trò phân quyền (tách khỏi vai trò chức danh) × chức năng + gán nhân sự. */
public final class PermissionDto {

    private PermissionDto() {
    }

    /** Định nghĩa một chức năng (cột ma trận). */
    public record FeatureDef(String key, String label, String group, boolean staffDefault) {
    }

    /** Một vai trò phân quyền + chức năng đang bật + số nhân sự đang giữ. */
    public record RoleResponse(String code, String name, String description,
                               List<String> features, int memberCount) {
    }

    /** Tạo vai trò mới {name, description}. code sinh tự động. */
    public record CreateRoleRequest(String name, String description) {
    }

    /** Cập nhật vai trò {name, description, features:[key...]}. */
    public record UpdateRoleRequest(String name, String description, List<String> features) {
    }

    /** Một nhân sự (để chọn gán vào vai trò) — kèm chức danh/bộ phận để hiển thị đồng bộ. */
    public record UserRef(String userId, String username, String fullName,
                          String jobPosition, String title, String deptCode, String roleCode, boolean admin) {
    }

    /** Gán danh sách nhân sự cho vai trò {userIds:[...]}. */
    public record SetMembersRequest(List<String> userIds) {
    }
}
