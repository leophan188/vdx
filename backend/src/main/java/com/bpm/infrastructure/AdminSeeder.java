package com.bpm.infrastructure;

import com.bpm.application.UserAccountService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed một tài khoản ADMIN khởi tạo nếu chưa có (để đăng nhập quản trị lần đầu).
 * Không chạy trong profile test. [ASSUMPTION] đổi mật khẩu mặc định ngay sau lần đăng nhập đầu (vận hành).
 */
@Component
@Profile("!test")
public class AdminSeeder implements CommandLineRunner {

    private final UserAccountRepository repository;
    private final UserAccountService userAccountService;

    public AdminSeeder(UserAccountRepository repository, UserAccountService userAccountService) {
        this.repository = repository;
        this.userAccountService = userAccountService;
    }

    @Override
    public void run(String... args) {
        if (!repository.existsByUsername("admin")) {
            String pw = System.getenv().getOrDefault("BPM_ADMIN_PASSWORD", "Admin@123");
            userAccountService.createAccount("admin", pw, "Quản trị hệ thống", "ADMIN", "system");
        }
    }
}
