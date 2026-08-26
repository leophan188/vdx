package com.bpm;

import com.bpm.api.ApiAuth;
import com.bpm.domain.permission.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ai được coi là ADMIN HỆ THỐNG — quyết định việc xem được dữ liệu của dự án mình không tham gia.
 */
class SystemAdminAccessTest {

    private static Authentication withAuthorities(String... auths) {
        return new UsernamePasswordAuthenticationToken("u", "p",
                List.of(auths).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void accountRoleAdminIsAdmin() {
        assertThat(ApiAuth.isAdmin(withAuthorities("ROLE_ADMIN"))).isTrue();
    }

    /**
     * Nhóm "Admin hệ thống" thực tế KHÔNG được tick hết mọi chức năng (các mục cá nhân như Đăng ký
     * nghỉ, Việc của tôi thường bỏ trống) — vẫn phải là admin.
     */
    @Test
    void permissionFeatureMakesSystemAdmin() {
        Authentication a = withAuthorities(
                Feature.PERMISSION.authority(), Feature.ACCOUNTS.authority(),
                Feature.AUDIT.authority(), Feature.SYSTEM.authority());
        assertThat(ApiAuth.isAdmin(a)).isTrue();
    }

    /** Người dùng thường, kể cả có nhiều chức năng quản lý, KHÔNG phải admin. */
    @Test
    void ordinaryManagerIsNotAdmin() {
        Authentication a = withAuthorities(
                Feature.HR.authority(), Feature.REPORTS.authority(),
                Feature.IMPORT.authority(), Feature.PROJECT.authority());
        assertThat(ApiAuth.isAdmin(a)).isFalse();
        assertThat(ApiAuth.isAdmin(null)).isFalse();
    }

    /** Nhóm được tick ĐỦ mọi chức năng vẫn là admin (giữ tương thích cách hiểu cũ). */
    @Test
    void groupWithEveryFeatureStillAdmin() {
        Authentication a = withAuthorities(Feature.allAuthorities().toArray(new String[0]));
        assertThat(ApiAuth.isAdmin(a)).isTrue();
    }
}
