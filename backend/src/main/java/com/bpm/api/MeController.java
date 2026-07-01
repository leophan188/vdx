package com.bpm.api;

import com.bpm.api.dto.MeDto;
import com.bpm.application.MeService;
import com.bpm.domain.social.PostMedia;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Self-service "Tài khoản của tôi" — /api/v1/me/** (chặn = authenticated ở SecurityConfig).
 * Chủ thể luôn lấy từ Authentication.getName() (username phiên hiện tại) — không nhận id từ client để tránh giả mạo.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService service;

    public MeController(MeService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ResponseEntity<MeDto.ProfileResponse> profile(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.profile(auth.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<MeDto.ProfileResponse> updateProfile(@RequestBody MeDto.UpdateProfileRequest req,
                                                               Authentication auth) {
        return ResponseEntity.ok(service.updateProfile(auth.getName(), req.email(), req.phone(), req.fullName()));
    }

    /** Lưu tùy chọn giao diện (accent + mode) cho chính người đang đăng nhập. */
    @PutMapping("/theme")
    public ResponseEntity<Void> updateTheme(@RequestBody MeDto.ThemeRequest req, Authentication auth) {
        service.updateTheme(auth.getName(), req.accent(), req.mode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody MeDto.ChangePasswordRequest req, Authentication auth) {
        service.changePassword(auth.getName(), req.oldPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/avatar")
    public ResponseEntity<MeDto.AvatarResponse> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                             Authentication auth) {
        return ResponseEntity.ok(service.uploadAvatar(auth.getName(), file));
    }

    /** Ảnh đại diện của chính tôi. 404 nếu chưa có (FE rơi về avatar chữ cái). */
    @GetMapping("/avatar")
    public ResponseEntity<Resource> myAvatar(Authentication auth) {
        return serve(service.myAvatarMedia(auth.getName()));
    }

    /** Ảnh đại diện của một người (topbar/danh sách). Tối thiểu phục vụ trong cùng phiên đăng nhập. */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> userAvatar(@PathVariable String userId) {
        return serve(service.avatarMedia(userId));
    }

    private ResponseEntity<Resource> serve(PostMedia m) {
        if (m == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = service.pathOf(m);
        Resource body = new PathResource(path);
        if (!body.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + m.getId() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .body(body);
    }
}
