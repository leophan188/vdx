package com.bpm.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch sử một lần chạy Import Excel → Báo cáo (Epic 4, FR-D05).
 * Lưu cả file kết quả (.xlsx) dạng byte[] để tải lại (FR-D04). Conventions GĐ1: PK UUID 36 ký tự, cột snake_case, UTC.
 */
@Entity
@Table(name = "report_run")
public class ReportRun {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Khoá mẫu báo cáo (vd CHAM_CONG_OT). */
    @Column(name = "template_key", length = 64, nullable = false)
    private String templateKey;

    @Column(name = "run_by", length = 100, nullable = false)
    private String runBy;

    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt;

    @Column(name = "input_file_name", length = 260)
    private String inputFileName;

    @Lob
    @Column(name = "output_bytes")
    private byte[] outputBytes;

    /** SUCCESS | FAILED. */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    /**
     * Kết quả dạng JSON ({@link ReportResult}) để mở lại trên màn hình từ lịch sử.
     * NULL với các lần chạy cũ (trước khi có tính năng) và với lần chạy FAILED — mọi chỗ đọc phải chịu được null.
     */
    @Lob
    @Column(name = "result_json")
    private String resultJson;

    protected ReportRun() {
    }

    public ReportRun(String templateKey, String runBy, String inputFileName,
                     byte[] outputBytes, String status, String message) {
        this.id = UUID.randomUUID().toString();
        this.templateKey = templateKey;
        this.runBy = runBy;
        this.runAt = Instant.now();
        this.inputFileName = inputFileName;
        this.outputBytes = outputBytes;
        this.status = status;
        this.message = message;
    }

    public String getId() { return id; }
    public String getTemplateKey() { return templateKey; }
    public String getRunBy() { return runBy; }
    public Instant getRunAt() { return runAt; }
    public String getInputFileName() { return inputFileName; }
    public byte[] getOutputBytes() { return outputBytes; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getResultJson() { return resultJson; }

    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public boolean hasOutput() { return outputBytes != null && outputBytes.length > 0; }

    public boolean hasResult() { return resultJson != null && !resultJson.isBlank(); }
}
