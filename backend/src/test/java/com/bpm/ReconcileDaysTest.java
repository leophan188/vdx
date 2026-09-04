package com.bpm;

import com.bpm.domain.erp.AttendanceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày công đối soát = ngày hưởng lương − nghỉ CÓ lương. Bốn ca dưới đây lấy từ dữ liệu thật trên
 * ERP của công ty (tháng 01–07/2026), vì quy tắc này từng sai theo hai hướng khác nhau:
 * lọc bỏ mọi ngày có xin nghỉ thì mất nửa ngày công thực làm, còn trừ cả nghỉ không lương thì trừ hai lần.
 */
class ReconcileDaysTest {

    private static AttendanceRecord rec(double pay, double paidLeave) {
        return new AttendanceRecord(1L, "Nguyễn Văn A", "1234", LocalDate.of(2026, 1, 13),
                pay, paidLeave, 0d, 0d);
    }

    @Test
    @DisplayName("Ngày làm bình thường: 1 công")
    void ngayThuong() {
        assertThat(rec(1.0, 0).reconcileDays()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Nghỉ nửa buổi CÓ lương: còn 0,5 công thực làm")
    void nghiNuaBuoiCoLuong() {
        // 13/01/2026, mã 3647: pay_workday 1.0, pay_leave_types_num 0.5 — khách hàng ghi đúng 0,5.
        assertThat(rec(1.0, 0.5).reconcileDays()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Nghỉ nửa buổi KHÔNG lương: vẫn 0,5 — pay_workday đã trừ sẵn phần không lương")
    void nghiNuaBuoiKhongLuong() {
        // 25/02/2026: pay_workday 0.5, pay_leave_types_num 0. Trừ thêm total_leaves_num (0.5) sẽ ra 0.
        assertThat(rec(0.5, 0).reconcileDays()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Nghỉ trọn ngày: 0 công, dù có lương hay không")
    void nghiTronNgay() {
        assertThat(rec(1.0, 1.0).reconcileDays()).isZero();   // nghỉ phép cả ngày
        assertThat(rec(0.0, 0.0).reconcileDays()).isZero();   // nghỉ không lương cả ngày
    }

    @Test
    @DisplayName("Không bao giờ trả số âm")
    void khongAm() {
        assertThat(rec(0.5, 1.0).reconcileDays()).isZero();
    }
}
