package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Xác thực đăng nhập (AD-9). Thành công → lưu SecurityContext vào phiên (HttpSession).
 * Thất bại → audit LOGIN_FAILED và ném ngoại lệ cho controller map sang 401.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuditPort auditPort;

    public AuthService(AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository,
                       AuditPort auditPort) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.auditPort = auditPort;
    }

    public Authentication login(String username, String password,
                                HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return authentication;
        } catch (AuthenticationException ex) {
            // Không lộ tài khoản có tồn tại hay không — thông điệp chung (AC-2).
            auditPort.record("LOGIN_FAILED", "UserAccount", null, username, "reason=" + ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
