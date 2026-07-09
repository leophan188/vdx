package com.bpm.api;

import com.bpm.api.dto.LoginRequest;
import com.bpm.application.AuthService;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Mật khẩu mặc định cấp cho nhân sự khi tạo tài khoản (quy ước nội bộ). Đăng nhập bằng nó → buộc đổi. */
    private static final String DEFAULT_PASSWORD = "1111";

    private final AuthService authService;
    private final UserAccountRepository accounts;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final RememberMeServices rememberMeServices;

    public AuthController(AuthService authService, UserAccountRepository accounts,
                          EmployeeRepository employees, PasswordEncoder passwordEncoder,
                          RememberMeServices rememberMeServices) {
        this.authService = authService;
        this.accounts = accounts;
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.rememberMeServices = rememberMeServices;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        Authentication auth = authService.login(req.username(), req.password(), request, response);
        // CÓ raw password tại đây → tính lại cờ "đang dùng mật khẩu mặc định" và lưu.
        markDefaultPasswordFlag(auth.getName(), req.password());
        // Tick "Ghi nhớ đăng nhập" → phát cookie remember-me (tự đăng nhập lại sau khi phiên hết).
        if (Boolean.TRUE.equals(req.rememberMe())) {
            rememberMeServices.loginSuccess(request, response, auth);
        }
        return ResponseEntity.ok(principal(auth));
    }

    /**
     * Xác định mật khẩu hiện tại có phải mặc định không (chỉ cho non-admin). Khớp khi:
     * mật khẩu = "1111", HOẶC mật khẩu trùng tên đăng nhập (mẫu cấp tài khoản phổ biến). Lưu cờ để /me đọc lại.
     */
    private void markDefaultPasswordFlag(String username, String rawPassword) {
        UserAccount acc = accounts.findByUsername(username).orElse(null);
        if (acc == null || acc.isAdmin() || rawPassword == null) {
            return; // admin không bao giờ bị ép
        }
        boolean isDefault = passwordEncoder.matches(DEFAULT_PASSWORD, acc.getPasswordHash())
                || passwordEncoder.matches(rawPassword, acc.getPasswordHash()) && rawPassword.equals(username);
        if (!Boolean.valueOf(isDefault).equals(acc.getPasswordIsDefault())) {
            acc.setPasswordIsDefault(isDefault);
            accounts.save(acc);
        }
    }

    /** Trả người dùng đang đăng nhập theo phiên (cookie) — để FE khôi phục menu sau khi F5. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(principal(auth));
    }

    /**
     * Gói thông tin user đăng nhập: username (mã), fullName (tên hiển thị), userId, authorities,
     * + cờ THIẾT LẬP BẮT BUỘC: mustChangePassword (đang dùng MK mặc định và không phải admin) và
     * hasAvatar (đã có ảnh đại diện). FE dùng 2 cờ này để ép người dùng hoàn tất trước khi vào app.
     */
    private Map<String, Object> principal(Authentication auth) {
        UserAccount acc = accounts.findByUsername(auth.getName()).orElse(null);
        boolean mustChange = acc != null && !acc.isAdmin() && Boolean.TRUE.equals(acc.getPasswordIsDefault());
        boolean hasAvatar = acc != null && acc.getAvatarPath() != null;
        Map<String, Object> out = new LinkedHashMap<>();
        // Vị trí công việc + phòng ban lấy từ hồ sơ nhân sự liên kết (nếu có) — hiển thị trên toolbar.
        Employee emp = acc == null ? null : employees.findByUserAccountId(acc.getId()).orElse(null);
        String jobTitle = null;
        if (emp != null) {
            jobTitle = notBlank(emp.getJobPosition()) ? emp.getJobPosition()
                    : (notBlank(emp.getTitle()) ? emp.getTitle() : null);
        }
        // Tên hiển thị ưu tiên hồ sơ nhân sự (khớp màn Tài khoản của tôi).
        String fullName = emp != null && notBlank(emp.getFullName()) ? emp.getFullName()
                : (acc != null ? acc.getFullName() : auth.getName());
        out.put("username", auth.getName());
        out.put("fullName", fullName);
        out.put("userId", acc != null ? acc.getId() : "");
        out.put("authorities", auth.getAuthorities());
        out.put("mustChangePassword", mustChange);
        out.put("hasAvatar", hasAvatar);
        out.put("jobTitle", jobTitle);
        out.put("deptCode", emp != null ? emp.getDeptCode() : null);
        out.put("unitId", emp != null ? emp.getOrgUnitId() : null); // id đơn vị (để điền sẵn trường "chọn đơn vị" theo user)
        out.put("themeAccent", acc != null ? acc.getThemeAccent() : null);
        out.put("themeMode", acc != null ? acc.getThemeMode() : null);
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        // Xoá cookie remember-me để không tự đăng nhập lại sau khi đăng xuất.
        jakarta.servlet.http.Cookie kill = new jakarta.servlet.http.Cookie("VDX_REMEMBER", "");
        kill.setPath("/");
        kill.setMaxAge(0);
        kill.setHttpOnly(true);
        response.addCookie(kill);
        return ResponseEntity.noContent().build();
    }
}
