package com.bpm.application;

import com.bpm.api.dto.MeDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.social.PostMedia;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service tài khoản của tôi (màn "Tài khoản của tôi"). Người dùng tự xem/sửa hồ sơ, đổi mật khẩu,
 * tải ảnh đại diện — KHÔNG đụng quyền quản trị. Mọi thao tác lấy chủ thể từ Authentication.getName() (username).
 * Tên hiển thị ưu tiên Employee (QLNS): nếu có Employee liên kết thì BỎ QUA fullName gửi lên.
 */
@Service
public class MeService {

    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final MediaStorageService media;
    private final AuditPort auditPort;

    public MeService(UserAccountRepository userRepo, EmployeeRepository employeeRepo,
                     PasswordEncoder passwordEncoder, MediaStorageService media, AuditPort auditPort) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.passwordEncoder = passwordEncoder;
        this.media = media;
        this.auditPort = auditPort;
    }

    private UserAccount require(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
    }

    @Transactional(readOnly = true)
    public MeDto.ProfileResponse profile(String username) {
        UserAccount acc = require(username);
        Employee emp = employeeRepo.findByUserAccountId(acc.getId()).orElse(null);
        boolean linked = emp != null && emp.getFullName() != null && !emp.getFullName().isBlank();
        String fullName = linked ? emp.getFullName() : acc.getFullName();
        return new MeDto.ProfileResponse(acc.getUsername(), fullName, acc.getEmail(), acc.getPhone(),
                acc.getRole(), acc.getRoleCode(), linked, acc.getAvatarPath() != null);
    }

    /** Cập nhật email/phone (và fullName nếu KHÔNG liên kết Employee). Tên do QLNS quản khi đã liên kết. */
    @Transactional
    public MeDto.ProfileResponse updateProfile(String username, String email, String phone, String fullName) {
        UserAccount acc = require(username);
        boolean linked = employeeRepo.findByUserAccountId(acc.getId())
                .map(e -> e.getFullName() != null && !e.getFullName().isBlank()).orElse(false);
        acc.setEmail(trimOrNull(email));
        acc.setPhone(trimOrNull(phone));
        if (!linked && fullName != null && !fullName.isBlank()) {
            acc.setFullName(fullName.trim());
        }
        userRepo.save(acc);
        auditPort.record("ME_PROFILE_UPDATED", "UserAccount", acc.getId(), username,
                "email=" + acc.getEmail() + ", phone=" + acc.getPhone());
        return profile(username);
    }

    /** Đổi mật khẩu của chính mình. Sai mật khẩu hiện tại → 400. newPassword tối thiểu 4 ký tự (quy ước). */
    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        UserAccount acc = require(username);
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, acc.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("Mật khẩu mới phải dài ít nhất 4 ký tự");
        }
        acc.setPasswordHash(passwordEncoder.encode(newPassword));
        // Đã tự đặt mật khẩu riêng → không còn dùng mặc định, gỡ ép đổi.
        acc.setPasswordIsDefault(false);
        userRepo.save(acc);
        auditPort.record("ME_PASSWORD_CHANGED", "UserAccount", acc.getId(), username, "self-service");
    }

    /** Bộ THEME PRESET hợp lệ (skin: màu menu+toolbar+nhấn); giá trị khác → rơi về 'vmo' (mặc định). */
    private static final java.util.Set<String> ACCENTS = java.util.Set.of(
            "vmo", "orange", "loop", "minimal", "navy", "slate", "indigo", "ocean",
            "teal", "emerald", "rose", "violet", "amber", "midnight",
            "blue", "green", "purple");

    /**
     * Lưu tùy chọn giao diện theo tài khoản: accent (orange/blue/green/purple/teal/rose — khác → orange)
     * và mode (light/dark — khác/null → giữ nguyên giá trị cũ). Trả về account đã lưu (không cần phơi DTO).
     */
    @Transactional
    public void updateTheme(String username, String accent, String mode) {
        UserAccount acc = require(username);
        String normAccent = (accent != null && ACCENTS.contains(accent)) ? accent : "orange";
        acc.setThemeAccent(normAccent);
        if ("light".equals(mode) || "dark".equals(mode)) {
            acc.setThemeMode(mode);
        }
        userRepo.save(acc);
        auditPort.record("ME_THEME_UPDATED", "UserAccount", acc.getId(), username,
                "accent=" + normAccent + ", mode=" + acc.getThemeMode());
    }

    /** Lưu ảnh đại diện (chỉ ảnh — MediaStorageService tự allowlist/giới hạn dung lượng) và gắn vào tài khoản. */
    @Transactional
    public MeDto.AvatarResponse uploadAvatar(String username, MultipartFile file) {
        UserAccount acc = require(username);
        PostMedia m = media.store(file, username);
        if (!"IMAGE".equals(m.getKind())) {
            throw new IllegalArgumentException("Ảnh đại diện phải là tệp ảnh");
        }
        acc.setAvatarPath(m.getId());
        userRepo.save(acc);
        auditPort.record("ME_AVATAR_UPDATED", "UserAccount", acc.getId(), username, "mediaId=" + m.getId());
        return new MeDto.AvatarResponse(m.getId(), "/api/v1/me/avatar");
    }

    /** PostMedia của avatar người dùng (theo userAccountId), hoặc null nếu chưa có. */
    @Transactional(readOnly = true)
    public PostMedia avatarMedia(String userAccountId) {
        UserAccount acc = userRepo.findById(userAccountId).orElse(null);
        if (acc == null || acc.getAvatarPath() == null) {
            return null;
        }
        try {
            return media.get(acc.getAvatarPath());
        } catch (IllegalArgumentException e) {
            return null; // media bị xoá khỏi đĩa → coi như chưa có
        }
    }

    /** Avatar của chính người đang đăng nhập. */
    @Transactional(readOnly = true)
    public PostMedia myAvatarMedia(String username) {
        UserAccount acc = userRepo.findByUsername(username).orElse(null);
        return acc == null ? null : avatarMedia(acc.getId());
    }

    public java.nio.file.Path pathOf(PostMedia m) {
        return media.pathOf(m);
    }

    private static String trimOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
