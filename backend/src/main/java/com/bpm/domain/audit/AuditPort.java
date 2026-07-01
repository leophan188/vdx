package com.bpm.domain.audit;

/**
 * Cổng ghi audit duy nhất (AD-6). Mọi thay đổi trạng thái/dữ liệu phát audit qua cổng này;
 * không feature nào ghi thẳng vào bảng audit. Bảng audit là append-only.
 */
public interface AuditPort {

    /**
     * Ghi một sự kiện audit.
     *
     * @param action  hành động (vd ACCOUNT_CREATED, LOGIN_FAILED)
     * @param objectType  loại đối tượng (vd UserAccount)
     * @param objectId    định danh đối tượng (có thể null nếu chưa xác định, vd login thất bại)
     * @param actor       người thực hiện (username), hoặc "anonymous"
     * @param detail      mô tả ngắn / trước-sau
     */
    void record(String action, String objectType, String objectId, String actor, String detail);
}
