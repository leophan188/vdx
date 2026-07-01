package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.position.Position;
import com.bpm.domain.role.PositionRole;
import com.bpm.domain.role.Role;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.PositionRoleRepository;
import com.bpm.infrastructure.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Vai trò & phân quyền theo vị trí (AD-9). Quyền của một người = hợp quyền các vai trò
 * gán cho các vị trí người đó đang giữ. Đổi người giữ → kế thừa tự nhiên (không gán lại).
 */
@Service
public class RoleService {

    private final RoleRepository roleRepo;
    private final PositionRoleRepository positionRoleRepo;
    private final PositionRepository positionRepo;
    private final AuditPort auditPort;

    public RoleService(RoleRepository roleRepo, PositionRoleRepository positionRoleRepo,
                       PositionRepository positionRepo, AuditPort auditPort) {
        this.roleRepo = roleRepo;
        this.positionRoleRepo = positionRoleRepo;
        this.positionRepo = positionRepo;
        this.auditPort = auditPort;
    }

    @Transactional
    public Role createRole(String code, String name, Set<String> permissions, String actor) {
        if (roleRepo.existsById(code)) {
            throw new IllegalArgumentException("Mã vai trò đã tồn tại");
        }
        Role r = roleRepo.save(new Role(code, name, permissions));
        auditPort.record("ROLE_CREATED", "Role", code, actor, "name=" + name + ", perms=" + permissions);
        return r;
    }

    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        return roleRepo.findAll();
    }

    /** Cập nhật tên + tập quyền của vai trò (không đổi code). */
    @Transactional
    public Role updateRole(String code, String name, Set<String> permissions, String actor) {
        Role r = roleRepo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại"));
        r.setName(name);
        r.setPermissions(permissions);
        roleRepo.save(r);
        auditPort.record("ROLE_UPDATED", "Role", code, actor, "name=" + name + ", perms=" + permissions);
        return r;
    }

    /** Xóa vai trò — chặn nếu đang gán cho bất kỳ vị trí nào. */
    @Transactional
    public void deleteRole(String code, String actor) {
        if (!roleRepo.existsById(code)) {
            throw new IllegalArgumentException("Vai trò không tồn tại");
        }
        if (positionRoleRepo.existsByRoleCode(code)) {
            throw new IllegalStateException("Vai trò đang được gán cho vị trí — gỡ gán trước khi xóa");
        }
        roleRepo.deleteById(code);
        auditPort.record("ROLE_DELETED", "Role", code, actor, "code=" + code);
    }

    @Transactional
    public void assignRoleToPosition(String positionId, String roleCode, String actor) {
        if (!positionRepo.existsById(positionId)) {
            throw new IllegalArgumentException("Vị trí không tồn tại");
        }
        if (!roleRepo.existsById(roleCode)) {
            throw new IllegalArgumentException("Vai trò không tồn tại");
        }
        positionRoleRepo.save(new PositionRole(positionId, roleCode));
        auditPort.record("POSITION_ROLE_ASSIGNED", "Position", positionId, actor, "role=" + roleCode);
    }

    @Transactional(readOnly = true)
    public List<PositionRole> rolesOfPosition(String positionId) {
        return positionRoleRepo.findByPositionId(positionId);
    }

    /** Quyền hiệu lực của một người: hợp quyền các vai trò gán cho mọi vị trí người đó đang giữ. */
    @Transactional(readOnly = true)
    public Set<String> permissionsForUser(String userId) {
        Set<String> perms = new LinkedHashSet<>();
        for (Position p : positionRepo.findByCurrentHolderUserId(userId)) {
            for (PositionRole pr : positionRoleRepo.findByPositionId(p.getId())) {
                roleRepo.findById(pr.getRoleCode()).ifPresent(r -> perms.addAll(r.getPermissions()));
            }
        }
        return perms;
    }

    /** Tập quyền của một vai trò theo mã (rỗng nếu không có). Dùng cho phân quyền chức năng gán trực tiếp. */
    @Transactional(readOnly = true)
    public Set<String> permissionsOfRole(String code) {
        if (code == null) {
            return new LinkedHashSet<>();
        }
        return roleRepo.findById(code).map(r -> new LinkedHashSet<>(r.getPermissions()))
                .orElseGet(LinkedHashSet::new);
    }

    /** Mã vai trò hiệu lực của một người (từ các vị trí đang giữ). */
    @Transactional(readOnly = true)
    public Set<String> roleCodesForUser(String userId) {
        Set<String> codes = new LinkedHashSet<>();
        for (Position p : positionRepo.findByCurrentHolderUserId(userId)) {
            for (PositionRole pr : positionRoleRepo.findByPositionId(p.getId())) {
                codes.add(pr.getRoleCode());
            }
        }
        return codes;
    }
}
