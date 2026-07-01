package com.bpm.domain.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cấu hình đồng bộ nhân sự qua LINK Google Sheet (lưu lại để chủ động tự đồng bộ — Epic 1 GĐ2).
 * Một bản ghi duy nhất (id = "default"): nhớ link để không phải dán lại; bật tự đồng bộ định kỳ.
 */
@Entity
@Table(name = "hr_sheet_config")
public class HrSheetConfig {

    @Id
    private String id = "default";

    @Column(length = 2000)
    private String sheetUrl;

    private boolean fullSync;       // đồng bộ toàn phần (khoá người vắng mặt)
    private boolean autoSync;       // bật tự đồng bộ HÀNG NGÀY
    private String syncTime = "02:00"; // giờ chạy hàng ngày, dạng "HH:mm" (mặc định 02:00)

    private Instant lastSyncAt;
    @Column(length = 500)
    private String lastSyncStatus;

    private Instant updatedAt;
    private String updatedBy;

    public HrSheetConfig() {
    }

    public void update(String sheetUrl, boolean fullSync, boolean autoSync, String syncTime, String actor) {
        this.sheetUrl = sheetUrl;
        this.fullSync = fullSync;
        this.autoSync = autoSync;
        this.syncTime = normalizeTime(syncTime);
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /** Chuẩn hoá "HH:mm" hợp lệ (sai/thiếu → 02:00). */
    private static String normalizeTime(String t) {
        try {
            java.time.LocalTime parsed = java.time.LocalTime.parse((t == null ? "" : t.trim()));
            return String.format("%02d:%02d", parsed.getHour(), parsed.getMinute());
        } catch (Exception e) {
            return "02:00";
        }
    }

    public void markSynced(String status) {
        this.lastSyncAt = Instant.now();
        this.lastSyncStatus = status;
    }

    public String getId() { return id; }
    public String getSheetUrl() { return sheetUrl; }
    public boolean isFullSync() { return fullSync; }
    public boolean isAutoSync() { return autoSync; }
    public String getSyncTime() { return syncTime == null ? "02:00" : syncTime; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
