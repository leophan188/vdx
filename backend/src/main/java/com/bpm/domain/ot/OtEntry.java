package com.bpm.domain.ot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Một đăng ký OT của nhân viên (Epic 3, FR-C01). Conventions GĐ1: PK UUID 36 ký tự, cột snake_case, thời gian UTC.
 * Giờ OT tính theo startTime/endTime nếu có; nếu không có thì lấy trường hours nhập tay.
 * periodKey "YYYY-MM" suy từ workDate — dùng để tổng hợp & chốt kỳ.
 */
@Entity
@Table(name = "ot_entry")
public class OtEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Chủ sở hữu đăng ký (userId của UserAccount). */
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** Họ tên tại thời điểm đăng ký (denormalize cho hiển thị/tổng hợp). */
    @Column(name = "user_name", length = 200, nullable = false)
    private String userName;

    /** Phòng/đơn vị của user (suy từ vị trí đang giữ). null nếu chưa giữ vị trí nào. */
    @Column(name = "org_unit_id", length = 36)
    private String orgUnitId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** Số giờ OT (nhập tay hoặc tính từ start/end). Đơn vị: giờ. */
    @Column(name = "hours", nullable = false)
    private double hours;

    /** Lý do / dự án. */
    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "note", length = 1000)
    private String note;

    /** Kỳ "YYYY-MM" suy từ workDate. */
    @Column(name = "period_key", length = 7, nullable = false)
    private String periodKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected OtEntry() {
    }

    public OtEntry(String userId, String userName, String orgUnitId,
                   LocalDate workDate, LocalTime startTime, LocalTime endTime, double hours,
                   String reason, String note) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.userName = userName;
        this.orgUnitId = orgUnitId;
        apply(workDate, startTime, endTime, hours, reason, note);
        this.createdAt = Instant.now();
    }

    /** Cập nhật nội dung đăng ký (Story 3.1, chỉ khi kỳ chưa chốt — kiểm ở service). */
    public void apply(LocalDate workDate, LocalTime startTime, LocalTime endTime, double hours,
                      String reason, String note) {
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.hours = computeHours(workDate, startTime, endTime, hours);
        this.reason = reason;
        this.note = note;
        this.periodKey = periodOf(workDate);
        this.updatedAt = Instant.now();
    }

    /**
     * Giờ OT: ưu tiên start/end (qua nửa đêm cộng 24h), fallback số giờ nhập tay.
     * Ngày T7/CN trừ nghỉ trưa theo giờ bắt đầu: bắt đầu ≤ 08:00 → trừ 1.5h; muộn hơn (vd 08:30) → trừ 1h.
     * Ngày thường không trừ.
     */
    public static double computeHours(LocalDate date, LocalTime start, LocalTime end, double fallback) {
        if (start != null && end != null) {
            double seconds = end.toSecondOfDay() - start.toSecondOfDay();
            if (seconds < 0) {
                seconds += 24 * 60 * 60; // ca qua nửa đêm
            }
            double h = seconds / 3600.0;
            if (date != null && isWeekend(date)) {
                h -= weekendLunch(start); // trừ nghỉ trưa T7/CN theo giờ bắt đầu
            }
            return Math.max(0, Math.round(h * 100.0) / 100.0);
        }
        return Math.max(0, fallback);
    }

    /** Nghỉ trưa T7/CN theo giờ bắt đầu: ≤ 08:00 → 1.5h; muộn hơn (vd 08:30) → 1h. */
    public static double weekendLunch(LocalTime start) {
        return (start != null && !start.isAfter(LocalTime.of(8, 0))) ? 1.5 : 1.0;
    }

    /** T7/CN. */
    public static boolean isWeekend(LocalDate d) {
        java.time.DayOfWeek dow = d.getDayOfWeek();
        return dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
    }

    public static String periodOf(LocalDate d) {
        return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getOrgUnitId() { return orgUnitId; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public double getHours() { return hours; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public String getPeriodKey() { return periodKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
