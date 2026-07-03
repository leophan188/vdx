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
        List<OnboardingView> onboardingSoon
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
}
