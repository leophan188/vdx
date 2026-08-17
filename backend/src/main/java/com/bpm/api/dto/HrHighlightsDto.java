package com.bpm.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Điểm nhấn nhân sự cho trang chủ (Việc B + C):
 *  - birthdaysToday: nhân sự đang hoạt động có sinh nhật (ngày/tháng) TRÙNG hôm nay.
 *  - onboardingSoon: nhân sự có joinDate trong TƯƠNG LAI (chưa tới ngày vào) — "còn X ngày Onboard".
 *  - birthdaysThisWeek: sinh nhật trong 7 ngày tới (gồm hôm nay) — tiện hiển thị thêm.
 */
public record HrHighlightsDto(
        List<BirthdayView> birthdaysToday,
        List<BirthdayView> birthdaysThisWeek,
        List<OnboardingView> onboardingSoon,
        List<AnniversaryView> anniversariesToday,
        List<AnniversaryView> anniversariesUpcoming
) {

    /**
     * Nhân sự mừng sinh nhật. {@code inDays} = số ngày tới sinh nhật (0 = hôm nay).
     * {@code userId} = id UserAccount liên kết (để FE lấy ảnh /api/v1/me/avatar/{userId}); null nếu chưa có tài khoản.
     */
    public record BirthdayView(
            String empCode,
            String fullName,
            String deptCode,
            String jobPosition,
            String title,
            String userId,
            int day,
            int month,
            int inDays
    ) {
    }

    /**
     * Nhân sự sắp onboard (chưa tới ngày vào). {@code daysUntil} > 0.
     * {@code userId} = id UserAccount liên kết (ảnh đại diện), null nếu chưa có tài khoản.
     */
    public record OnboardingView(
            String empCode,
            String fullName,
            String deptCode,
            String jobPosition,
            String title,
            String userId,
            LocalDate joinDate,
            int daysUntil
    ) {
    }

    /**
     * TRI ÂN THÂM NIÊN — kỷ niệm ngày vào làm, chỉ tính từ TRÒN 1 NĂM trở lên.
     *
     * {@code years} = số năm gắn bó (≥ 1). {@code inDays} = số ngày tới kỷ niệm (0 = hôm nay).
     * Người vào làm đúng hôm nay có years = 0 nên KHÔNG vào đây — đó là sự kiện onboard,
     * hai thứ khác nhau và không được lẫn.
     */
    public record AnniversaryView(
            String empCode,
            String fullName,
            String deptCode,
            String jobPosition,
            String title,
            String userId,
            LocalDate joinDate,
            int years,
            int inDays
    ) {
    }
}
