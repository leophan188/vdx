package com.bpm.domain.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một đăng ký NGHỈ của nhân viên. Conventions GĐ1: PK UUID 36 ký tự, cột snake_case, thời gian UTC.
 * Không có phê duyệt: đăng ký xong tính ngay số ngày nghỉ (chỉ đếm T2–T6 trong khoảng).
 */
@Entity
@Table(name = "leave_entry")
public class LeaveEntry {

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

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** Loại nghỉ: "ANNUAL" (phép năm) | "UNPAID" (không lương). */
    @Column(name = "type", length = 16, nullable = false)
    private String type;

    /** Số ngày nghỉ (T2–T6 trong khoảng) — TỰ TÍNH, không cho nhập tay. */
    @Column(name = "days", nullable = false)
    private double days;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LeaveEntry() {
    }

    public LeaveEntry(String userId, String userName, String orgUnitId,
                      LocalDate fromDate, LocalDate toDate, String type, String reason) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.userName = userName;
        this.orgUnitId = orgUnitId;
        apply(fromDate, toDate, type, reason);
        this.createdAt = Instant.now();
    }

    /** Cập nhật nội dung đăng ký; days tính lại từ khoảng ngày. */
    public void apply(LocalDate fromDate, LocalDate toDate, String type, String reason) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.type = normalizeType(type);
        this.days = workdays(fromDate, toDate);
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    /** Chỉ giữ ANNUAL/UNPAID; khác → ANNUAL. */
    public static String normalizeType(String type) {
        return "UNPAID".equalsIgnoreCase(type) ? "UNPAID" : "ANNUAL";
    }

    /** Nhãn loại nghỉ. */
    public static String typeLabel(String type) {
        return "UNPAID".equalsIgnoreCase(type) ? "Không lương" : "Phép năm";
    }

    /** Đếm ngày T2–T6 trong [from,to] (inclusive). from>to → 0. */
    public static int workdays(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getOrgUnitId() { return orgUnitId; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public String getType() { return type; }
    public double getDays() { return days; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
