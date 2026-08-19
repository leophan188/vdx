package com.bpm.api;

import com.bpm.domain.permission.Feature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiện ích đọc danh tính/quyền từ {@link Authentication} cho tầng API.
 * Gom về MỘT chỗ để mọi màn hiểu "admin" giống nhau — hai định nghĩa lệch nhau ở hai controller
 * là cách âm thầm mở dữ liệu cho người không được xem.
 */
public final class ApiAuth {

    private ApiAuth() {
    }

    /** Tên đăng nhập của người đang thao tác (dùng làm actor khi ghi audit / gán chủ sở hữu). */
    public static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    /** Cấp ADMIN: tài khoản ROLE_ADMIN HOẶC nhóm phân quyền TOÀN QUYỀN (có đủ mọi chức năng FEAT_*). */
    public static boolean isAdmin(Authentication a) {
        if (a == null) {
            return false;
        }
        Set<String> auths = new HashSet<>();
        for (GrantedAuthority ga : a.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                return true;
            }
            auths.add(ga.getAuthority());
        }
        return auths.containsAll(Feature.allAuthorities());
    }
}
