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

    /**
     * Cấp ADMIN HỆ THỐNG: tài khoản ROLE_ADMIN, hoặc nhóm phân quyền có chức năng PHÂN QUYỀN.
     *
     * Trước đây tiêu chí là "có ĐỦ mọi chức năng FEAT_*", nhưng nhóm "Admin hệ thống" thực tế chỉ được
     * cấp 23/31 chức năng (không ai gán cho quản trị viên các mục cá nhân như Đăng ký nghỉ, Việc của
     * tôi…), nên họ KHÔNG được coi là admin và bị chặn khỏi những dự án mình không tham gia. Tiêu chí
     * đó còn tự vỡ mỗi lần thêm một chức năng mới vào hệ thống: mọi nhóm đang "toàn quyền" lập tức mất
     * tư cách admin cho tới khi có người vào tick thêm ô mới.
     *
     * Dùng quyền PHÂN QUYỀN làm dấu hiệu vì ai sửa được ma trận phân quyền thì đã có thể tự cấp cho
     * mình mọi chức năng — coi họ là admin không mở thêm rủi ro nào so với thực tế.
     */
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
        return auths.contains(Feature.PERMISSION.authority()) || auths.containsAll(Feature.allAuthorities());
    }
}
