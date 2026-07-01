package com.bpm.api;

import com.bpm.api.dto.PermissionDto;
import com.bpm.application.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phân quyền chức năng — vai trò phân quyền (tách khỏi vai trò chức danh) × chức năng + gán nhân sự.
 * Prefix {@code /api/v1/permissions}. Bảo vệ bằng authority FEAT_PERMISSION (ADMIN luôn có).
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    /** Danh mục chức năng (cột). */
    @GetMapping("/features")
    public List<PermissionDto.FeatureDef> features() {
        return permissionService.features();
    }

    /** Danh sách vai trò phân quyền + chức năng + số nhân sự. */
    @GetMapping("/roles")
    public List<PermissionDto.RoleResponse> roles() {
        return permissionService.listRoles();
    }

    /** Tạo vai trò phân quyền mới. */
    @PostMapping("/roles")
    public PermissionDto.RoleResponse create(@RequestBody PermissionDto.CreateRoleRequest req, Authentication auth) {
        return permissionService.createRole(req.name(), req.description(), actor(auth));
    }

    /** Cập nhật vai trò: tên + mô tả + tập chức năng. */
    @PutMapping("/roles/{code}")
    public PermissionDto.RoleResponse update(@PathVariable String code,
                                             @RequestBody PermissionDto.UpdateRoleRequest req, Authentication auth) {
        return permissionService.updateRole(code, req.name(), req.description(), req.features(), actor(auth));
    }

    /** Xoá vai trò (gỡ khỏi mọi nhân sự đang giữ). */
    @DeleteMapping("/roles/{code}")
    public void delete(@PathVariable String code, Authentication auth) {
        permissionService.deleteRole(code, actor(auth));
    }

    /** Mọi tài khoản (để chọn gán vào vai trò). */
    @GetMapping("/users")
    public List<PermissionDto.UserRef> users() {
        return permissionService.allUsers();
    }

    /** Nhân sự đang thuộc một vai trò. */
    @GetMapping("/roles/{code}/members")
    public List<PermissionDto.UserRef> members(@PathVariable String code) {
        return permissionService.members(code);
    }

    /** Gán danh sách nhân sự cho vai trò. */
    @PutMapping("/roles/{code}/members")
    public List<PermissionDto.UserRef> setMembers(@PathVariable String code,
                                                  @RequestBody PermissionDto.SetMembersRequest req, Authentication auth) {
        return permissionService.setMembers(code, req.userIds(), actor(auth));
    }
}
