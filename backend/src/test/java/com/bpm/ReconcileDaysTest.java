package com.bpm;

import com.bpm.domain.erp.AttendanceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày công đối soát = NGUYÊN ngày công hưởng lương của ERP.
 *
 * Quy tắc này đã sai ba lần theo ba kiểu, nên các ca dưới đây lấy thẳng từ dữ liệu thật trên ERP công
 * ty (01–07/2026) để khoá lại: (1) lọc bỏ mọi ngày có xin nghỉ thì mất nửa ngày công thực làm;
 * (2) trừ cả nghỉ không lương là trừ hai lần vì pay_workday đã trừ sẵn; (3) trừ nghỉ có lương thì ERP
 * tụt xuống dưới bảng nghiệm thu của khách hàng, sinh lệch âm — bên chấm công không thể ít hơn bên
 * xác nhận. Khách hàng chốt công theo quy tắc nghỉ riêng của họ, phần chênh chính là thứ cần đối soát.
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
    @DisplayName("Nghỉ nửa buổi CÓ lương: vẫn tính nguyên ngày công hưởng lương")
    void nghiNuaBuoiCoLuong() {
        // 04/02/2026, mã 4233: ERP pay_workday 1.0 với nghỉ sáng 0.5; khách hàng nghiệm thu 0,5.
        // Chênh 0,5 là chênh THẬT cần đem đi hỏi, không phải thứ để công thức tự triệt tiêu.
        assertThat(rec(1.0, 0.5).reconcileDays()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Nghỉ CẢ ngày có lương: vẫn 1 — khách hàng cũng nghiệm thu đủ ngày đó")
    void nghiTronNgayCoLuong() {
        // 12/02/2026, mã 4233: ERP nghỉ cả ngày có lương, khách hàng vẫn ghi 1 công.
        assertThat(rec(1.0, 1.0).reconcileDays()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Nghỉ không lương: pay_workday đã trừ sẵn nên lấy nguyên giá trị đó")
    void nghiKhongLuong() {
        assertThat(rec(0.5, 0).reconcileDays()).isEqualTo(0.5);   // nghỉ nửa buổi không lương
        assertThat(rec(0.0, 0.0).reconcileDays()).isZero();       // nghỉ không lương cả ngày
    }

    @Test
    @DisplayName("Không bao giờ trả số âm")
    void khongAm() {
        assertThat(rec(-1.0, 0).reconcileDays()).isZero();
    }
}
