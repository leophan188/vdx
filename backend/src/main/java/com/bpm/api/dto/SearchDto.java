package com.bpm.api.dto;

import java.util.List;

/**
 * Kết quả tìm kiếm toàn cục (quick-jump topbar) — GET /api/v1/search?q=...
 *
 * <p>Gom 3 nguồn theo nhóm; mỗi nhóm chỉ trả khi tài khoản có chức năng tương ứng
 * (ROLE_ADMIN hoặc FEAT_HR/FEAT_PROJECT/FEAT_ACCOUNTS). q rỗng/&lt;2 ký tự → tất cả rỗng.
 */
public record SearchDto(
        List<Item> employees,
        List<Item> projects,
        List<Item> accounts,
        List<Item> posts
) {

    /**
     * Một dòng kết quả. {@code code}/{@code id} dùng để điều hướng (vd dự án → /projects/{id});
     * {@code name} là tên hiển thị đậm, {@code sub} là dòng phụ muted.
     */
    public record Item(String id, String code, String name, String sub) {
    }
}
