package com.bpm.api;

import com.bpm.api.dto.CreateUserRequest;
import com.bpm.api.dto.ResetPasswordRequest;
import com.bpm.api.dto.UpdateUserRequest;
import com.bpm.api.dto.UserResponse;
import com.bpm.application.UserAccountService;
import com.bpm.domain.UserAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị tài khoản — chỉ ROLE_ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService service;
    private final com.bpm.infrastructure.EmployeeRepository employeeRepo;

    public UserController(UserAccountService service, com.bpm.infrastructure.EmployeeRepository employeeRepo) {
        this.service = service;
        this.employeeRepo = employeeRepo;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req, Authentication auth) {
        UserAccount acc = service.createAccount(req.username(), req.password(), req.fullName(),
                req.email(), req.phone(), req.role(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(acc));
    }

    @GetMapping
    public List<UserResponse> list() {
        List<UserAccount> accounts = service.listAccounts();
        // Tên hiển thị LẤY THEO HỒ SƠ NHÂN SỰ (QLNS) nếu có liên kết — không dùng tên lưu riêng ở tài khoản.
        java.util.Map<String, String> empNameByUser = new java.util.HashMap<>();
        for (com.bpm.domain.hr.Employee e : employeeRepo.findAll()) {
            if (e.getUserAccountId() != null) {
                empNameByUser.put(e.getUserAccountId(), e.getFullName());
            }
        }
        return accounts.stream()
                .map(a -> UserResponse.from(a, empNameByUser.get(a.getId())))
                .toList();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable String id,
                                               @Valid @RequestBody UpdateUserRequest req,
                                               Authentication auth) {
        UserAccount acc = service.updateAccount(id, req.fullName(), req.email(), req.phone(), req.role(),
                req.roleCode(), actor(auth));
        return ResponseEntity.ok(UserResponse.from(acc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.deleteAccount(id, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<Void> lock(@PathVariable String id, @RequestParam boolean locked, Authentication auth) {
        service.setLocked(id, locked, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable String id,
                                              @Valid @RequestBody ResetPasswordRequest req,
                                              Authentication auth) {
        service.resetPassword(id, req.newPassword(), actor(auth));
        return ResponseEntity.noContent().build();
    }
}
