package com.bpm.domain.erp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Chấm công ERP của MỘT người trong MỘT ngày, đã lưu lại theo kỳ (tháng).
 *
 * Vì sao lưu chứ không gọi ERP mỗi lần xem: đối soát là việc đối chiếu hai con số ở một thời điểm và
 * còn phải giải trình lại về sau. Nếu mỗi lần mở bảng lại gọi ERP thì số hôm nay khác số hôm qua (ai
 * đó sửa chấm công muộn) mà không ai biết vì sao lệch. Lưu lại kèm mốc tải để luôn nói được "số này
 * đọc từ ERP lúc nào"; muốn cập nhật thì tải lại kỳ đó, dữ liệu cũ của kỳ bị thay hoàn toàn.
 */
@Entity
@Table(name = "erp_attendance_entry", indexes = {
        @Index(name = "idx_erp_att_period", columnList = "period_key"),
        @Index(name = "idx_erp_att_period_emp", columnList = "period_key,erp_employee_id")
})
public class ErpAttendanceEntry {

    @Id
    private String id = UUID.randomUUID().toString();

    /** Kỳ dạng "yyyy-MM" — mọi thứ trong màn này xoay quanh tháng. */
    @Column(name = "period_key", length = 7, nullable = false)
    private String periodKey;

    @Column(name = "erp_employee_id")
    private long erpEmployeeId;

    @Column(name = "employee_name", length = 300)
    private String employeeName;

    /** Khoá ghép theo TÊN đã chuẩn hoá — dùng để bắt cặp với dòng của khách hàng. */
    @Column(name = "match_key", length = 300)
    private String matchKey;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false)
    private double hours;

    /** Thời điểm tải kỳ này từ ERP — để giải trình con số. */
    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "fetched_by", length = 200)
    private String fetchedBy;

    protected ErpAttendanceEntry() {
    }

    public ErpAttendanceEntry(String periodKey, AttendanceRecord rec, String matchKey, String actor) {
        this.periodKey = periodKey;
        this.erpEmployeeId = rec.employeeErpId();
        this.employeeName = rec.employeeName();
        this.matchKey = matchKey;
        this.workDate = rec.workDate();
        this.hours = rec.workedHours();
        this.fetchedAt = Instant.now();
        this.fetchedBy = actor;
    }

    public String getId() { return id; }
    public String getPeriodKey() { return periodKey; }
    public long getErpEmployeeId() { return erpEmployeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getMatchKey() { return matchKey; }
    public LocalDate getWorkDate() { return workDate; }
    public double getHours() { return hours; }
    public Instant getFetchedAt() { return fetchedAt; }
    public String getFetchedBy() { return fetchedBy; }
}
