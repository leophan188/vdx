package com.bpm.domain.erp;

import java.time.LocalDate;

/**
 * Một lần chấm công đọc từ ERP, đã quy về giờ Việt Nam.
 *
 * @param employeeErpId id nhân sự bên Odoo — định danh chắc chắn nhất, tên có thể trùng hoặc đổi
 * @param employeeName  tên hiển thị, đã tách phần mã ở đuôi
 * @param employeeCode  MÃ nhân viên tách từ tên hiển thị của Odoo ("Đoàn Đình Đức - 4021" → 4021);
 *                      null nếu bản ghi bên ERP không ghi mã
 * @param workDate      NGÀY làm việc — lấy thẳng trường attendance_date của Odoo
 * @param payWorkday    ngày công hưởng lương (pay_workday) — CHƯA trừ phần nghỉ có lương
 * @param paidLeave     phần nghỉ CÓ LƯƠNG trong ngày (pay_leave_types_num). Nghỉ KHÔNG lương không
 *                      nằm ở đây vì pay_workday đã trừ sẵn nó rồi
 * @param workday       ngày công thực tế theo chấm công — giữ để tra khi các con số lệch nhau
 * @param workedHours   số giờ (worked_hours) — giữ để tra khi ngày công trông đáng ngờ
 */
public record AttendanceRecord(long employeeErpId, String employeeName, String employeeCode,
                               LocalDate workDate, double payWorkday, double paidLeave,
                               double workday, double workedHours) {

    /**
     * Ngày công dùng để ĐỐI SOÁT với bảng công khách hàng: ngày hưởng lương trừ phần nghỉ có lương.
     *
     * Nghỉ nửa buổi có phép cho pay_workday = 1 và nghỉ có lương 0,5 nên còn 0,5 công thực làm, đúng
     * bằng con số khách hàng ghi. Nghỉ không lương thì pay_workday đã là 0,5 và phần nghỉ có lương
     * bằng 0 nên vẫn ra 0,5; trừ cả hai loại nghỉ sẽ ra 0 và tạo ra lệch âm không có thật.
     */
    public double reconcileDays() {
        return Math.max(0d, payWorkday - paidLeave);
    }
}
