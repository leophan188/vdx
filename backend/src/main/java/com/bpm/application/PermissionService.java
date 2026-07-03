package com.bpm.application;

import com.bpm.api.dto.PermissionDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.permission.Feature;
import com.bpm.domain.permission.PermissionRole;
import com.bpm.infrastructure.PermissionRoleRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phân quyền CHỨC NĂNG dựa trên VAI TRÒ PHÂN QUYỀN ({@link PermissionRole}) — tách hoàn toàn khỏi
 * vai trò chức danh. Quản trị: tạo/sửa/xoá vai trò, gán chức năng (Feature), gán nhân sự (UserAccount.roleCode).
 * Mọi nhân sự cùng vai trò có quyền NHƯ NHAU.
 */
@Service
public class PermissionService {

    private final PermissionRoleRepository roleRepo;
    private final UserAccountRepository userRepo;
    private final com.bpm.infrastructure.EmployeeRepository employeeRepo;
    private final AuditPort auditPort;

    public PermissionService(PermissionRoleRepository roleRepo, UserAccountRepository userRepo,
                             com.bpm.infrastructure.EmployeeRepository employeeRepo, AuditPort auditPort) {
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.auditPort = auditPort;
    }

    /** Map userAccountId → Employee (resolve chức danh/bộ phận một lần). */
    private Map<String, com.bpm.domain.hr.Employee> employeeByUser() {
        Map<String, com.bpm.domain.hr.Employee> m = new java.util.HashMap<>();
        for (com.bpm.domain.hr.Employee e : employeeRepo.findAll()) {
            if (e.getUserAccountId() != null) {
                m.put(e.getUserAccountId(), e);
            }
        }
        return m;
    }

    private PermissionDto.UserRef toUserRef(UserAccount u, Map<String, com.bpm.domain.hr.Employee> emp) {
        com.bpm.domain.hr.Employee e = emp.get(u.getId());
        return new PermissionDto.UserRef(u.getId(), u.getUsername(), u.getFullName(),
                e == null ? null : e.getJobPosition(), e == null ? null : e.getTitle(),
                e == null ? null : e.getDeptCode(),
                u.getRoleCode(), u.isAdmin());
    }

    // ===== Chức năng (cột) =====

    public List<PermissionDto.FeatureDef> features() {
        List<PermissionDto.FeatureDef> out = new ArrayList<>();
        for (Feature f : Feature.ordered()) {
            out.add(new PermissionDto.FeatureDef(f.name(), f.getLabel(), f.getGroup(), f.isStaffDefault()));
        }
        return out;
    }

    // ===== Vai trò phân quyền (hàng) =====

    @Transactional(readOnly = true)
    public List<PermissionDto.RoleResponse> listRoles() {
        List<PermissionDto.RoleResponse> out = new ArrayList<>();
        for (PermissionRole r : roleRepo.findAll()) {
            out.add(toDto(r));
        }
        return out;
    }

    @Transactional
    public PermissionDto.RoleResponse createRole(String name, String description, String actor) {
        String nm = requireName(name);
        String code = uniqueCode(nm);
        PermissionRole r = new PermissionRole(code, nm, trimOrNull(description), new LinkedHashSet<>());
        roleRepo.save(r);
        auditPort.record("PERM_ROLE_CREATED", "PermissionRole", code, actor, "name=" + nm);
        return toDto(r);
    }

    @Transactional
    public PermissionDto.RoleResponse updateRole(String code, String name, String description,
                                                 List<String> featureKeys, String actor) {
        PermissionRole r = roleRepo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại"));
        r.setName(requireName(name));
        r.setDescription(trimOrNull(description));
        r.setFeatures(normalizeFeatures(featureKeys));
        roleRepo.save(r);
        auditPort.record("PERM_ROLE_UPDATED", "PermissionRole", code, actor,
                "name=" + r.getName() + ", features=" + r.getFeatures());
        return toDto(r);
    }

    @Transactional
    public void deleteRole(String code, String actor) {
        PermissionRole r = roleRepo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại"));
        // Gỡ vai trò khỏi mọi nhân sự đang giữ (về mặc định nhân viên).
        for (UserAccount u : userRepo.findByRoleCode(code)) {
            u.setRoleCode(null);
            userRepo.save(u);
        }
        roleRepo.delete(r);
        auditPort.record("PERM_ROLE_DELETED", "PermissionRole", code, actor, "name=" + r.getName());
    }

    // ===== Nhân sự thuộc vai trò =====

    /** Mọi tài khoản (để chọn gán vào vai trò). */
    @Transactional(readOnly = true)
    public List<PermissionDto.UserRef> allUsers() {
        Map<String, com.bpm.domain.hr.Employee> emp = employeeByUser();
        List<PermissionDto.UserRef> out = new ArrayList<>();
        for (UserAccount u : userRepo.findAll()) {
            out.add(toUserRef(u, emp));
        }
        out.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<PermissionDto.UserRef> members(String code) {
        Map<String, com.bpm.domain.hr.Employee> emp = employeeByUser();
        List<PermissionDto.UserRef> out = new ArrayList<>();
        for (UserAccount u : userRepo.findByRoleCode(code)) {
            out.add(toUserRef(u, emp));
        }
        out.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
        return out;
    }

    /** Gán đúng danh sách nhân sự cho vai trò: ai trong danh sách → roleCode=code; ai đang ở vai trò mà bị bỏ → roleCode=null. */
    @Transactional
    public List<PermissionDto.UserRef> setMembers(String code, List<String> userIds, String actor) {
        if (!roleRepo.existsById(code)) {
            throw new IllegalArgumentException("Vai trò không tồn tại");
        }
        Set<String> wanted = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        // Bỏ gán những người đang ở vai trò nhưng không còn trong danh sách.
        for (UserAccount u : userRepo.findByRoleCode(code)) {
            if (!wanted.contains(u.getId())) {
                u.setRoleCode(null);
                userRepo.save(u);
            }
        }
        // Gán vai trò cho những người được chọn.
        for (String uid : wanted) {
            userRepo.findById(uid).ifPresent(u -> {
                if (!code.equals(u.getRoleCode())) {
                    u.setRoleCode(code);
                    userRepo.save(u);
                }
            });
        }
        auditPort.record("PERM_ROLE_MEMBERS_SET", "PermissionRole", code, actor, "count=" + wanted.size());
        return members(code);
    }

    // ===== helpers =====

    private PermissionDto.RoleResponse toDto(PermissionRole r) {
        return new PermissionDto.RoleResponse(r.getCode(), r.getName(), r.getDescription(),
                new ArrayList<>(r.getFeatures()), (int) userRepo.countByRoleCode(r.getCode()));
    }

    private Set<String> normalizeFeatures(List<String> keys) {
        Set<String> out = new LinkedHashSet<>();
        if (keys != null) {
            for (String k : keys) {
                Feature f = Feature.fromKey(k);
                if (f != null) {
                    out.add(f.name());
                }
            }
        }
        return out;
    }

    private String uniqueCode(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "VAITRO";
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String code = base;
        int n = 1;
        while (roleRepo.existsById(code)) {
            code = base + "_" + (++n);
            if (n > 50) { // an toàn
                code = base + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                break;
            }
        }
        return code;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên vai trò không được trống");
        }
        return name.trim();
    }

    private static String trimOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
