package com.bpm.domain.permission;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Danh mục CHỨC NĂNG cho phân quyền ma trận (vai trò × chức năng).
 * Mỗi feature → một authority "FEAT_{key}" gán cho vai trò; menu/route/endpoint kiểm theo authority này.
 *
 * <p>{@code group}: nhóm hiển thị trên màn Phân quyền. {@code staffDefault}: chức năng MẶC ĐỊNH cho
 * nhân viên thường (cá nhân/cộng tác) — tài khoản non-admin chưa gán vai trò vẫn được các quyền này
 * để không khoá nhầm khi go-live.
 */
public enum Feature {

    // ===== Cá nhân & cộng tác (mặc định cho nhân viên) =====
    INBOX("Việc của tôi", "Cá nhân & cộng tác", true),
    REQUESTS("Hồ sơ của tôi", "Cá nhân & cộng tác", true),
    MYTASKS("Việc dự án của tôi", "Cá nhân & cộng tác", true),
    SOCIAL("Mạng xã hội nội bộ", "Cá nhân & cộng tác", true),
    SOCIAL_POST("Đăng bài mạng xã hội", "Cá nhân & cộng tác", false),
    PROJECT("Quản lý dự án", "Cá nhân & cộng tác", true),
    OT("Đăng ký OT", "Cá nhân & cộng tác", true),
    LEAVE("Đăng ký nghỉ", "Cá nhân & cộng tác", true),
    DOCS("Tài liệu", "Cá nhân & cộng tác", true),

    // ===== Tab trong Quản lý dự án (phân quyền từng tab) — vẫn cần FEAT_PROJECT để vào dự án =====
    PRJ_KANBAN("Dự án · Kanban", "Dự án — Tab", true),
    PRJ_BUGS("Dự án · Bug/Issue", "Dự án — Tab", true),
    PRJ_BACKLOG("Dự án · Backlog", "Dự án — Tab", true),
    PRJ_TIMELINE("Dự án · Timeline", "Dự án — Tab", true),
    PRJ_TIMESHEET("Dự án · Timesheet", "Dự án — Tab", true),
    PRJ_LOG("Dự án · Log hoạt động", "Dự án — Tab", true),
    PRJ_DIARY("Dự án · Nhật ký", "Dự án — Tab", true),
    PRJ_REPORTS("Dự án · Báo cáo ngày/tuần", "Dự án — Tab", true),
    PRJ_MEMBERS("Dự án · Thành viên", "Dự án — Tab", true),

    // ===== Quản lý & quản trị =====
    HR("Quản lý nhân sự", "Quản lý", false),
    IMPORT("Import Excel → Báo cáo", "Quản lý", false),
    REPORTS("Báo cáo & Thống kê", "Quản lý", false),
    TRACKING("Theo dõi tiến trình", "Quản lý", false),
    PROCESS("Quy trình & Biểu mẫu", "Quản lý", false),
    LEAVE_MANAGE("Tổng hợp nghỉ phép", "Quản lý", false),
    OT_MANAGE("Tổng hợp OT", "Quản lý", false),

    // ===== Quản trị hệ thống =====
    ACCOUNTS("Quản lý tài khoản", "Quản trị hệ thống", false),
    CATALOG("Danh mục tổ chức (cơ cấu/vị trí/vai trò)", "Quản trị hệ thống", false),
    AUDIT("Nhật ký kiểm toán", "Quản trị hệ thống", false),
    SYSTEM("Cấu hình & dữ liệu hệ thống", "Quản trị hệ thống", false),
    PERMISSION("Phân quyền chức năng", "Quản trị hệ thống", false);

    /** Tiền tố authority. */
    public static final String PREFIX = "FEAT_";

    private final String label;
    private final String group;
    private final boolean staffDefault;

    Feature(String label, String group, boolean staffDefault) {
        this.label = label;
        this.group = group;
        this.staffDefault = staffDefault;
    }

    public String getLabel() { return label; }
    public String getGroup() { return group; }
    public boolean isStaffDefault() { return staffDefault; }

    /** Authority string, vd "FEAT_PROJECT". */
    public String authority() {
        return PREFIX + name();
    }

    /** Tập authority của TẤT CẢ chức năng (cấp cho ADMIN). */
    public static Set<String> allAuthorities() {
        return Arrays.stream(values()).map(Feature::authority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Authority mặc định cho nhân viên thường (chưa gán vai trò). */
    public static Set<String> staffDefaultAuthorities() {
        return Arrays.stream(values()).filter(Feature::isStaffDefault).map(Feature::authority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Key (vd "PROJECT") của các permission string là FEAT_* trong tập quyền của vai trò. */
    public static Set<String> featureKeysOf(Set<String> permissions) {
        Set<String> out = new LinkedHashSet<>();
        if (permissions == null) {
            return out;
        }
        for (String p : permissions) {
            if (p != null && p.startsWith(PREFIX)) {
                out.add(p.substring(PREFIX.length()));
            }
        }
        return out;
    }

    public static List<Feature> ordered() {
        return Arrays.asList(values());
    }

    /** Parse an toàn key → Feature (null nếu không hợp lệ). */
    public static Feature fromKey(String key) {
        if (key == null) {
            return null;
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
