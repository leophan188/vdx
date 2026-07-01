package com.bpm.infrastructure.auth;

import com.bpm.application.RoleService;
import com.bpm.domain.UserAccount;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Nạp tài khoản + dựng authorities (AD-9): vai trò tài khoản (legacy/bootstrap)
 * ∪ vai trò từ các vị trí đang giữ ∪ quyền của các vai trò đó. Tài khoản LOCKED → disabled.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;
    private final RoleService roleService;
    private final com.bpm.infrastructure.PermissionRoleRepository permissionRoleRepo;

    public AppUserDetailsService(UserAccountRepository repository, RoleService roleService,
                                 com.bpm.infrastructure.PermissionRoleRepository permissionRoleRepo) {
        this.repository = repository;
        this.roleService = roleService;
        this.permissionRoleRepo = permissionRoleRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount acc = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        // Vai trò tài khoản (bootstrap, vd ADMIN seed)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + acc.getRole()));
        // Vai trò + quyền resolve qua các vị trí đang giữ
        for (String roleCode : roleService.roleCodesForUser(acc.getId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        for (String perm : roleService.permissionsForUser(acc.getId())) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        // ===== Phân quyền CHỨC NĂNG (ma trận FEAT_*) =====
        if (acc.isAdmin()) {
            // ADMIN: luôn có MỌI chức năng.
            for (String f : com.bpm.domain.permission.Feature.allAuthorities()) {
                authorities.add(new SimpleGrantedAuthority(f));
            }
        } else if (acc.getRoleCode() != null && !acc.getRoleCode().isBlank()) {
            // Tài khoản đã gán VAI TRÒ PHÂN QUYỀN → cấp authority FEAT_{key} cho từng chức năng của vai trò.
            permissionRoleRepo.findById(acc.getRoleCode()).ifPresent(role -> {
                for (String key : role.getFeatures()) {
                    com.bpm.domain.permission.Feature f = com.bpm.domain.permission.Feature.fromKey(key);
                    if (f != null) {
                        authorities.add(new SimpleGrantedAuthority(f.authority()));
                    }
                }
            });
        } else {
            // Nhân viên thường chưa gán vai trò → bộ chức năng mặc định (cá nhân/cộng tác) để không khoá nhầm.
            for (String f : com.bpm.domain.permission.Feature.staffDefaultAuthorities()) {
                authorities.add(new SimpleGrantedAuthority(f));
            }
        }

        return User.withUsername(acc.getUsername())
                .password(acc.getPasswordHash())
                .disabled(acc.isLocked())
                .authorities(authorities)
                .build();
    }
}
