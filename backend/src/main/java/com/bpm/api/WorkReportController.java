package com.bpm.api;

import com.bpm.api.dto.WorkReportDto.WorkReport;
import com.bpm.application.WorkReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Cụm BÁO CÁO CÔNG VIỆC — Dashboard + Report Ngày + Report Tuần (snapshot live).
 * Prefix {@code /api/v1/work-reports}. Chỉ yêu cầu ĐÃ ĐĂNG NHẬP (SecurityConfig mặc định
 * {@code /api/v1/** = authenticated}); phạm vi dữ liệu xử lý trong service theo {@code canSeeAll}.
 *
 * <p>{@code canSeeAll} = ROLE_ADMIN hoặc có authority FEAT_REPORTS → xem mọi dự án + mọi nhân sự;
 * ngược lại chỉ dự án user là thành viên/chủ sở hữu.
 */
@RestController
@RequestMapping("/api/v1/work-reports")
public class WorkReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WorkReportService service;

    public WorkReportController(WorkReportService service) {
        this.service = service;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    /** canSeeAll = ROLE_ADMIN hoặc authority FEAT_REPORTS. */
    private static boolean canSeeAll(Authentication a) {
        if (a == null) {
            return false;
        }
        for (GrantedAuthority ga : a.getAuthorities()) {
            String auth = ga.getAuthority();
            if ("ROLE_ADMIN".equals(auth) || "FEAT_REPORTS".equals(auth)) {
                return true;
            }
        }
        return false;
    }

    // ===================== JSON =====================

    @GetMapping("/dashboard")
    public WorkReport dashboard(Authentication auth) {
        return service.dashboard(actor(auth), canSeeAll(auth));
    }

    @GetMapping("/daily")
    public WorkReport daily(Authentication auth,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(actor(auth), canSeeAll(auth), date);
    }

    @GetMapping("/weekly")
    public WorkReport weekly(Authentication auth,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.weekly(actor(auth), canSeeAll(auth), date);
    }

    // ===================== Xuất Excel =====================

    @GetMapping("/daily/export.xlsx")
    public ResponseEntity<byte[]> exportDaily(Authentication auth,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        WorkReport r = service.daily(actor(auth), canSeeAll(auth), date);
        LocalDate d = date != null ? date : LocalDate.now();
        return xlsx(r, "bao-cao-ngay-" + d.format(FILE_DATE) + ".xlsx");
    }

    @GetMapping("/weekly/export.xlsx")
    public ResponseEntity<byte[]> exportWeekly(Authentication auth,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        WorkReport r = service.weekly(actor(auth), canSeeAll(auth), date);
        LocalDate base = date != null ? date : LocalDate.now();
        return xlsx(r, "bao-cao-tuan-" + base.format(FILE_DATE) + ".xlsx");
    }

    private ResponseEntity<byte[]> xlsx(WorkReport r, String filename) {
        byte[] bytes = service.exportXlsx(r);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX))
                .body(bytes);
    }
}
