package com.bpm.infrastructure.hr;

import com.bpm.application.EmployeeService;
import com.bpm.domain.hr.HrSheetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Tự động đồng bộ nhân sự từ LINK Google Sheet đã lưu (Việc 3) — khi admin bật "tự đồng bộ".
 * Chạy nền định kỳ, đến hạn theo intervalMinutes thì áp dụng; lỗi chỉ ghi log, không làm sập app.
 */
@Component
public class HrSheetSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(HrSheetSyncScheduler.class);

    private final EmployeeService employeeService;

    public HrSheetSyncScheduler(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Mỗi 5 phút kiểm tra: nếu bật tự đồng bộ và ĐÃ QUA giờ hẹn HÔM NAY mà chưa đồng bộ kể từ giờ đó
     * → chạy 1 lần. Đảm bảo đồng bộ HÀNG NGÀY đúng giờ cấu hình (HH:mm theo múi giờ máy chủ).
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void autoSync() {
        try {
            HrSheetConfig cfg = employeeService.getSheetConfig();
            if (!cfg.isAutoSync() || cfg.getSheetUrl() == null || cfg.getSheetUrl().isBlank()) {
                return;
            }
            ZoneId zone = ZoneId.systemDefault();
            LocalTime at = LocalTime.parse(cfg.getSyncTime()); // "HH:mm"
            Instant todayScheduled = LocalDate.now(zone).atTime(at).atZone(zone).toInstant();
            Instant now = Instant.now();
            if (now.isBefore(todayScheduled)) {
                return; // chưa tới giờ hẹn hôm nay
            }
            Instant last = cfg.getLastSyncAt();
            if (last != null && !last.isBefore(todayScheduled)) {
                return; // đã đồng bộ sau giờ hẹn hôm nay rồi
            }
            log.info("[hr-auto-sync] Đồng bộ tự động hàng ngày ({}) từ link đã lưu…", cfg.getSyncTime());
            employeeService.syncSaved("system-auto");
        } catch (Exception e) {
            log.warn("[hr-auto-sync] Lỗi đồng bộ tự động (bỏ qua, sẽ thử lại sau): {}", e.toString());
        }
    }
}
