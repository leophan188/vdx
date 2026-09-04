package com.bpm.infrastructure.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Duration;

/**
 * Cấu hình bảo mật (AD-9, NFR-04). Phiên dựa trên HttpSession; phần đăng nhập do AuthController xử lý
 * để trả envelope lỗi chuẩn. RBAC chi tiết (vị trí→vai trò) ở Story 1.4 — ở đây chỉ chặn /users theo ROLE_ADMIN.
 */
@Configuration
public class SecurityConfig {

    /** Khoá ký token remember-me (đổi được qua biến môi trường; PHẢI ổn định giữa các lần restart). */
    private static final String REMEMBER_KEY =
            System.getenv().getOrDefault("BPM_REMEMBER_KEY", "vdx-remember-me-secret-2026");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * "Ghi nhớ đăng nhập": phát cookie token (VDX_REMEMBER) sống 30 ngày → tự đăng nhập lại
     * sau khi phiên hết hạn / đóng trình duyệt. alwaysRemember=true vì ta CHỦ ĐỘNG gọi loginSuccess
     * chỉ khi người dùng tick ô nhớ (ở AuthController).
     */
    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(REMEMBER_KEY, userDetailsService);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds((int) Duration.ofDays(30).toSeconds());
        services.setCookieName("VDX_REMEMBER");
        return services;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RememberMeServices rememberMeServices) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // Tự đăng nhập lại từ cookie remember-me khi phiên không còn (đóng trình duyệt / hết hạn).
            .rememberMe(rm -> rm.rememberMeServices(rememberMeServices).key(REMEMBER_KEY))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // OnlyOffice (Story 3.10) gọi không có phiên: tải nội dung (kiểm token) + callback (kiểm JWT).
                .requestMatchers("/api/v1/documents/*/content", "/api/v1/documents/*/callback").permitAll()
                // Tự phục vụ tài khoản (hồ sơ/đổi MK/avatar) + HR highlights (sinh nhật/onboarding) — mọi user đăng nhập.
                .requestMatchers("/api/v1/me/**").authenticated()
                .requestMatchers("/api/v1/hr/**").authenticated()
                .requestMatchers("/api/v1/search/**").authenticated()
                // ===== Danh mục tổ chức ĐỌC-CHỈ cho picker chọn người/đơn vị (mọi user đã đăng nhập đọc được;
                //       thao tác GHI vẫn rơi xuống FEAT_CATALOG/FEAT_ACCOUNTS bên dưới) =====
                .requestMatchers(HttpMethod.GET, "/api/v1/org-units/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/positions/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users").authenticated()
                // ===== Phân quyền CHỨC NĂNG (ma trận) — kiểm authority FEAT_* (ADMIN luôn có đủ) =====
                .requestMatchers("/api/v1/permissions/**").hasAuthority("FEAT_PERMISSION")
                .requestMatchers("/api/v1/users/**").hasAuthority("FEAT_ACCOUNTS")
                .requestMatchers("/api/v1/org-units/**").hasAuthority("FEAT_CATALOG")
                .requestMatchers("/api/v1/positions/**").hasAuthority("FEAT_CATALOG")
                .requestMatchers("/api/v1/roles/**").hasAuthority("FEAT_CATALOG")
                .requestMatchers("/api/v1/assignments/**").hasAuthority("FEAT_CATALOG")
                .requestMatchers("/api/v1/audit/**").hasAuthority("FEAT_AUDIT")
                .requestMatchers("/api/v1/processes/**").hasAuthority("FEAT_PROCESS")
                .requestMatchers("/api/v1/forms/**").hasAuthority("FEAT_PROCESS")
                .requestMatchers("/api/v1/dashboard/**").hasAuthority("FEAT_REPORTS")
                .requestMatchers("/api/v1/reports/**").hasAuthority("FEAT_REPORTS")
                .requestMatchers("/api/v1/reminders/**").hasAuthority("FEAT_SYSTEM")
                // ===== GĐ2 — Cổng nội bộ =====
                // Epic 1 Quản lý nhân sự + Import (Epic 4 Import Excel).
                .requestMatchers("/api/v1/employees/**").hasAuthority("FEAT_HR")
                .requestMatchers("/api/v1/excel-reports/**").hasAuthority("FEAT_IMPORT")
                // Kiểm soát giờ công (ERP ↔ khách hàng) nằm trong nhóm Công cụ nên dùng chung chức năng
                // IMPORT; riêng cấu hình kết nối ERP còn chặn thêm ở tầng controller vì chứa API key.
                .requestMatchers("/api/v1/erp-timesheet/**").hasAuthority("FEAT_IMPORT")
                // Cấu hình tích hợp ERP là việc quản trị hệ thống (chứa khoá vào ERP công ty), nên
                // đặt sau FEAT_SYSTEM chứ không dùng chung quyền với màn Công cụ.
                .requestMatchers("/api/v1/erp-integrations/**").hasAuthority("FEAT_SYSTEM")
                .requestMatchers("/api/v1/system/**").hasAuthority("FEAT_SYSTEM")
                // Quản lý dự án (mini-Jira): cần chức năng PROJECT; phân quyền thành viên ở tầng nghiệp vụ.
                .requestMatchers("/api/v1/projects/**").hasAuthority("FEAT_PROJECT")
                // Đăng ký NGHỉ: tổng hợp/xuất = FEAT_LEAVE_MANAGE (đặt TRƯỚC entries); đăng ký cá nhân = FEAT_LEAVE.
                .requestMatchers("/api/v1/leave/summary", "/api/v1/leave/export").hasAuthority("FEAT_LEAVE_MANAGE")
                .requestMatchers("/api/v1/leave/entries", "/api/v1/leave/entries/**").hasAuthority("FEAT_LEAVE")
                // Epic 3 OT: đăng ký của tôi = chức năng OT; tổng hợp theo khoảng/xuất = FEAT_OT_MANAGE (đặt TRƯỚC /ot/**);
                // tổng hợp theo kỳ tháng/chốt kỳ = ADMIN (rule cụ thể TRƯỚC /ot/**).
                .requestMatchers("/api/v1/ot/summary-range", "/api/v1/ot/export-range").hasAuthority("FEAT_OT_MANAGE")
                .requestMatchers("/api/v1/ot/entries", "/api/v1/ot/entries/**").hasAuthority("FEAT_OT")
                .requestMatchers("/api/v1/ot/**").hasRole("ADMIN")
                // Epic 2 Mạng xã hội: ĐĂNG BÀI = chức năng SOCIAL_POST (phân quyền ma trận; ADMIN luôn có);
                // ghim/ẩn/xoá + kiểm duyệt = ADMIN; xem/like/comment = chức năng SOCIAL.
                .requestMatchers("/api/v1/posts/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/posts/*/pin", "/api/v1/posts/*/hidden").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/posts", "/api/v1/posts/media").hasAuthority("FEAT_SOCIAL_POST")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*").hasRole("ADMIN")
                .requestMatchers("/api/v1/posts/**").hasAuthority("FEAT_SOCIAL")
                .requestMatchers("/api/v1/media/**").authenticated()
                // Theo dõi & hủy phiên chạy = quyền "Theo dõi tiến trình" (FEAT_TRACKING; ADMIN luôn có đủ)
                // — khớp featureGuard('TRACKING') ở FE; khởi tạo/danh sách khả dụng = mọi user đăng nhập.
                .requestMatchers(HttpMethod.GET, "/api/v1/instances/all").hasAuthority("FEAT_TRACKING")
                .requestMatchers(HttpMethod.GET, "/api/v1/instances/*/overview").hasAuthority("FEAT_TRACKING")
                .requestMatchers(HttpMethod.POST, "/api/v1/instances/*/cancel").hasAuthority("FEAT_TRACKING")
                .requestMatchers("/api/v1/instances/**").authenticated()
                .anyRequest().authenticated()
            )
            // Trả 401 JSON thay vì redirect form khi chưa xác thực
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                (request, response, authEx) -> response.sendError(401, "Chưa xác thực")));
        return http.build();
    }
}
