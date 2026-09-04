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
 * @param payWorkday    NGÀY CÔNG HƯỞNG LƯƠNG (pay_workday) — con số dùng để đối soát. Khác với
 *                      "ngày công thực tế" (workday): nghỉ phép đã duyệt, WFH, quên chấm công đều
 *                      hưởng lương đủ nhưng thực tế bằng 0, lấy nhầm cột là cả bảng ra 0
 * @param workday       ngày công thực tế — giữ để tra khi hai con số lệch nhau
 * @param workedHours   số giờ (worked_hours) — giữ để tra khi ngày công trông đáng ngờ
 */
public record AttendanceRecord(long employeeErpId, String employeeName, String employeeCode,
                               LocalDate workDate, double payWorkday, double workday, double workedHours) {
}
