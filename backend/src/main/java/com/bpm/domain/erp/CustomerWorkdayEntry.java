package com.bpm.domain.erp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Số công khách hàng ghi nhận cho MỘT người trong MỘT kỳ (tháng), đọc từ file Excel khách hàng gửi.
 *
 * Lưu theo kỳ và giữ lại tên file lẫn người import: khi hai bên lệch nhau, câu hỏi đầu tiên luôn là
 * "số này lấy từ bản gửi ngày nào". Import lại cùng một kỳ sẽ THAY toàn bộ dữ liệu kỳ đó — bản gửi
 * sau của khách hàng là bản có hiệu lực, cộng dồn hai lần import là cách tạo ra số sai không ai lần ra.
 */
@Entity
@Table(name = "customer_workday_entry", indexes = {
        @Index(name = "idx_cust_wd_period", columnList = "period_key")
})
public class CustomerWorkdayEntry {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "period_key", length = 7, nullable = false)
    private String periodKey;

    /** Mã nhân sự trong file khách hàng (có thể trống — nhiều khách hàng chỉ gửi tên). */
    @Column(name = "emp_code", length = 100)
    private String empCode;

    @Column(name = "employee_name", length = 300)
    private String employeeName;

    /** Tên đã chuẩn hoá để bắt cặp với dòng ERP. */
    @Column(name = "match_key", length = 300)
    private String matchKey;

    /**
     * NGÀY trong tháng mà số công này thuộc về. Null = dòng tổng cả tháng (dữ liệu nhập theo mẫu cũ);
     * bảng ngang chỉ vẽ được từ những dòng có ngày.
     */
    @Column(name = "work_date")
    private java.time.LocalDate workDate;

    /** Số công khách hàng ghi nhận (đơn vị NGÀY công: 1 hoặc 0,5). */
    @Column(name = "days", nullable = false)
    private double days;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(name = "imported_by", length = 200)
    private String importedBy;

    protected CustomerWorkdayEntry() {
    }

    public CustomerWorkdayEntry(String periodKey, java.time.LocalDate workDate, String empCode,
                                String employeeName, String matchKey, double days, String note,
                                String sourceFile, String actor) {
        this.periodKey = periodKey;
        this.workDate = workDate;
        this.empCode = empCode;
        this.employeeName = employeeName;
        this.matchKey = matchKey;
        this.days = days;
        this.note = note;
        this.sourceFile = sourceFile;
        this.importedAt = Instant.now();
        this.importedBy = actor;
    }

    public String getId() { return id; }
    public String getPeriodKey() { return periodKey; }
    public String getEmpCode() { return empCode; }
    public String getEmployeeName() { return employeeName; }
    public String getMatchKey() { return matchKey; }
    public java.time.LocalDate getWorkDate() { return workDate; }
    public double getDays() { return days; }
    public String getNote() { return note; }
    public String getSourceFile() { return sourceFile; }
    public Instant getImportedAt() { return importedAt; }
    public String getImportedBy() { return importedBy; }
}
