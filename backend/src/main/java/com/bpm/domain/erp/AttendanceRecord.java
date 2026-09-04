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
 * @param workday       NGÀY CÔNG Odoo tự tính (1.0 / 0.5); đây mới là con số dùng để đối soát
 * @param workedHours   số giờ (worked_hours) — giữ lại để tra khi ngày công trông đáng ngờ
 */
public record AttendanceRecord(long employeeErpId, String employeeName, String employeeCode,
                               LocalDate workDate, double workday, double workedHours) {
}
