package com.bpm.application;

import com.bpm.domain.AccountStatus;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Quản trị tài khoản nội bộ (Story 1.1). Mọi thay đổi đi qua AuditPort (AD-6).
 * Mật khẩu luôn băm (BCrypt) trước khi lưu (NFR-04).
 */
@Service
public class UserAccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditPort auditPort;

    public UserAccountService(UserAccountRepository repository, PasswordEncoder passwordEncoder, AuditPort auditPort) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditPort = auditPort;
    }

    @Transactional
    public UserAccount createAccount(String username, String rawPassword, String fullName, String role, String actor) {
        return createAccount(username, rawPassword, fullName, null, null, role, actor);
    }

    @Transactional
    public UserAccount createAccount(String username, String rawPassword, String fullName, String email,
                                     String phone, String role, String actor) {
        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
        }
        validatePassword(rawPassword);
        String resolvedRole = (role == null || role.isBlank()) ? "USER" : role;
        UserAccount acc = new UserAccount(username, passwordEncoder.encode(rawPassword), fullName, resolvedRole);
        acc.setEmail(email);
        acc.setPhone(phone);
        UserAccount saved = repository.save(acc);
        auditPort.record("ACCOUNT_CREATED", "UserAccount", saved.getId(), actor,
                "username=" + username + ", role=" + resolvedRole);
        return saved;
    }

    @Transactional
    public UserAccount updateAccount(String id, String fullName, String email, String phone, String role,
                                     String roleCode, String actor) {
        UserAccount acc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        acc.setFullName(fullName);
        acc.setEmail(email);
        acc.setPhone(phone);
        if (role != null && !role.isBlank()) {
            acc.setRole(role);
        }
        // Vai trò chức năng (phân quyền ma trận): chuỗi rỗng → gỡ gán (về mặc định nhân viên).
        acc.setRoleCode(roleCode == null || roleCode.isBlank() ? null : roleCode.trim());
        UserAccount saved = repository.save(acc);
        auditPort.record("ACCOUNT_UPDATED", "UserAccount", id, actor,
                "username=" + acc.getUsername() + ", roleCode=" + acc.getRoleCode());
        return saved;
    }

    @Transactional
    public void deleteAccount(String id, String actor) {
        UserAccount acc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        repository.delete(acc);
        auditPort.record("ACCOUNT_DELETED", "UserAccount", id, actor, "username=" + acc.getUsername());
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listAccounts() {
        return repository.findAll();
    }

    @Transactional
    public void setLocked(String id, boolean locked, String actor) {
        UserAccount acc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        acc.setStatus(locked ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
        repository.save(acc);
        auditPort.record(locked ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED", "UserAccount", id, actor,
                "username=" + acc.getUsername());
    }

    @Transactional
    public void resetPassword(String id, String newRawPassword, String actor) {
        UserAccount acc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        validatePassword(newRawPassword);
        acc.setPasswordHash(passwordEncoder.encode(newRawPassword));
        repository.save(acc);
        auditPort.record("ACCOUNT_PASSWORD_RESET", "UserAccount", id, actor, "username=" + acc.getUsername());
    }

    /** Chính sách mật khẩu tối thiểu (AC-6). [ASSUMPTION] mức cụ thể chốt sau. */
    private void validatePassword(String raw) {
        if (raw == null || raw.length() < 8) {
            throw new IllegalArgumentException("Mật khẩu phải dài ít nhất 8 ký tự");
        }
    }
}
