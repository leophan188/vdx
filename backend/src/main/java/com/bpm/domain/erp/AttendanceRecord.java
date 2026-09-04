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
     * Ngày công dùng để ĐỐI SOÁT với bảng công khách hàng: lấy NGUYÊN ngày công hưởng lương.
     *
     * Đã thử trừ phần nghỉ có lương và phải bỏ, vì hai bên không chốt công theo cùng quy tắc nghỉ.
     * Số thật tháng 02/2026 của mã 4233: ngày nghỉ NỬA buổi khách hàng ghi 0,5 (khớp cách trừ), nhưng
     * ngày nghỉ CẢ ngày họ vẫn ghi đủ 1; còn mã 3980 thì ngược lại, ngày ERP không ghi nghỉ nào mà
     * khách hàng chỉ nghiệm thu 0,5. Trừ nghỉ làm ERP tụt xuống dưới bảng nghiệm thu và sinh ra lệch
     * ÂM — thứ vô lý về nghiệp vụ vì bên chấm công không thể ít hơn bên xác nhận.
     *
     * Giữ nguyên ngày công hưởng lương thì ERP luôn là cận trên, và phần chênh còn lại đúng là thứ
     * cần đem đi hỏi. {@link #paidLeave} vẫn được lưu để tra khi cần giải thích một ô lệch.
     */
    public double reconcileDays() {
        return Math.max(0d, payWorkday);
    }
}
