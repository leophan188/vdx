package com.bpm.domain.erp;

import java.time.LocalDate;

/**
 * Một lần chấm công đọc từ ERP, đã quy về giờ Việt Nam.
 *
 * @param employeeErpId id nhân sự bên Odoo — định danh chắc chắn nhất, tên có thể trùng hoặc đổi
 * @param employeeName  tên hiển thị, đã tách phần mã ở đuôi
 * @param employeeCode  MÃ nhân viên tách từ tên hiển thị của Odoo ("Đoàn Đình Đức - 4021" → 4021);
 *                      null nếu bản ghi bên ERP không ghi mã
 * @param workDate      NGÀY làm việc theo giờ VN, lấy theo mốc check-in
 * @param workedHours   số giờ Odoo đã tính sẵn (worked_hours) cho lần chấm công đó
 */
public record AttendanceRecord(long employeeErpId, String employeeName, String employeeCode,
                               LocalDate workDate, double workedHours) {
}
