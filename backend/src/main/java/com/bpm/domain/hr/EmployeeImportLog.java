package com.bpm.domain.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Nhật ký một lần import file nhân sự (Epic 1 GĐ2 — FR-A06, NFR-06).
 *
 * <p>Mỗi lần admin bấm "Áp dụng" ghi một bản ghi: ai/khi nào/số thêm-sửa-khoá-bàn giao-lỗi + tên file.
 * Bổ trợ cho audit (AuditPort) để dựng màn "Nhật ký import" nhanh, không phải lọc bảng audit chung.
 */
@Entity
@Table(name = "employee_import_log")
public class EmployeeImportLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt;

    @Column(name = "run_by", length = 100)
    private String runBy;

    @Column(name = "file_name", length = 260)
    private String fileName;

    @Column(name = "added_count", nullable = false)
    private int addedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "locked_count", nullable = false)
    private int lockedCount;

    /** Số người lẽ ra khoá nhưng giữ lại vì đang giữ việc/là người duyệt (FR-A08). */
    @Column(name = "handover_count", nullable = false)
    private int handoverCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "note", length = 500)
    private String note;

    protected EmployeeImportLog() {
    }

    public EmployeeImportLog(String runBy, String fileName, int addedCount, int updatedCount, int lockedCount,
                             int handoverCount, int errorCount, String note) {
        this.id = UUID.randomUUID().toString();
        this.runAt = Instant.now();
        this.runBy = runBy;
        this.fileName = fileName;
        this.addedCount = addedCount;
        this.updatedCount = updatedCount;
        this.lockedCount = lockedCount;
        this.handoverCount = handoverCount;
        this.errorCount = errorCount;
        this.note = note;
    }

    public String getId() { return id; }
    public Instant getRunAt() { return runAt; }
    public String getRunBy() { return runBy; }
    public String getFileName() { return fileName; }
    public int getAddedCount() { return addedCount; }
    public int getUpdatedCount() { return updatedCount; }
    public int getLockedCount() { return lockedCount; }
    public int getHandoverCount() { return handoverCount; }
    public int getErrorCount() { return errorCount; }
    public String getNote() { return note; }
}
