package com.bpm.infrastructure;

import com.bpm.domain.permission.Feature;
import com.bpm.domain.permission.PermissionRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Seed vài VAI TRÒ PHÂN QUYỀN mẫu (idempotent) vào bảng riêng {@link PermissionRole} — KHÔNG dính
 * danh mục vai trò chức danh. Admin có thể tạo thêm / sửa / xoá tuỳ ý.
 * Dọn các vai trò chức năng cũ (nếu trước đây lỡ tạo trong bảng Role chức danh).
 */
@Component
@Order(50)
public class PermissionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private final PermissionRoleRepository roleRepo;
    private final RoleRepository legacyRoleRepo;

    public PermissionSeeder(PermissionRoleRepository roleRepo, RoleRepository legacyRoleRepo) {
        this.roleRepo = roleRepo;
        this.legacyRoleRepo = legacyRoleRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Dọn các vai trò CHỨC NĂNG từng bị tạo nhầm trong bảng Role chức danh (di trú một lần).
        for (String legacy : new String[]{"QUANTRI", "QUANLY", "NHANSU", "NHANVIEN"}) {
            if (legacyRoleRepo.existsById(legacy) && legacyRoleRepo.findById(legacy)
                    .map(r -> r.getPermissions().stream().anyMatch(p -> p.startsWith(Feature.PREFIX)))
                    .orElse(false)) {
                legacyRoleRepo.deleteById(legacy);
            }
        }

        int created = 0;
        created += ensure("QUANTRI", "Quản trị hệ thống", "Toàn quyền mọi chức năng", all());
        created += ensure("QUANLY", "Quản lý chung", "Dự án, báo cáo, quy trình",
                keys(Feature.SOCIAL, Feature.REQUESTS, Feature.PROJECT, Feature.OT, Feature.DOCS,
                        Feature.REPORTS, Feature.TRACKING, Feature.PROCESS));
        created += ensure("NHANSU", "Nhân sự (HR)", "Quản lý nhân sự + import + báo cáo",
                keys(Feature.SOCIAL, Feature.REQUESTS, Feature.PROJECT, Feature.OT, Feature.DOCS,
                        Feature.HR, Feature.IMPORT, Feature.REPORTS));
        created += ensure("NHANVIEN", "Nhân viên", "Cá nhân & cộng tác (mặc định)",
                keys(Feature.SOCIAL, Feature.REQUESTS, Feature.INBOX, Feature.MYTASKS, Feature.PROJECT, Feature.OT, Feature.DOCS));
        if (created > 0) {
            log.info("PermissionSeeder: tạo {} vai trò phân quyền mẫu", created);
        }
    }

    private int ensure(String code, String name, String desc, Set<String> features) {
        if (roleRepo.existsById(code)) {
            return 0;
        }
        roleRepo.save(new PermissionRole(code, name, desc, features));
        return 1;
    }

    private static Set<String> all() {
        Set<String> s = new LinkedHashSet<>();
        for (Feature f : Feature.values()) {
            s.add(f.name());
        }
        return s;
    }

    private static Set<String> keys(Feature... fs) {
        Set<String> s = new LinkedHashSet<>();
        for (Feature f : fs) {
            s.add(f.name());
        }
        // Vai trò nào có PROJECT → tự thêm MỌI tab dự án (mặc định thấy đủ tab; admin bỏ bớt sau).
        if (s.contains(Feature.PROJECT.name())) {
            for (Feature f : Feature.values()) {
                if ("Dự án — Tab".equals(f.getGroup())) {
                    s.add(f.name());
                }
            }
        }
        return s;
    }
}
