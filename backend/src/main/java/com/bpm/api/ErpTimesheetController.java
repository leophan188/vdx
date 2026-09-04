package com.bpm.api;

import com.bpm.application.ErpTimesheetService;
import com.bpm.domain.erp.CustomerWorkdayEntry;
import com.bpm.domain.erp.ErpConfig;
import com.bpm.domain.erp.WorkdayReconciliation;
import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ReportTemplate;
import com.bpm.domain.report.ValidationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Kiểm soát giờ công (Công cụ): chấm công ERP · công khách hàng · đối soát — cần chức năng FEAT_IMPORT.
 *
 * Cấu hình kết nối chỉ ADMIN được sửa: ở đó có API key vào ERP của công ty.
 */
@RestController
@RequestMapping("/api/v1/erp-timesheet")
public class ErpTimesheetController {

    private final ErpTimesheetService service;

    public ErpTimesheetController(ErpTimesheetService service) {
        this.service = service;
    }

    // ===== DTO =====

    /** Cấu hình trả ra màn hình — KHÔNG kèm API key, chỉ nói đã đặt hay chưa. */
    public record ConfigResponse(String baseUrl, String dbName, String username, boolean apiKeySet,
                                 String lastCheckAt, String lastCheckStatus, String updatedBy) {
        static ConfigResponse of(ErpConfig c) {
            return new ConfigResponse(c.getBaseUrl(), c.getDbName(), c.getUsername(), c.hasApiKey(),
                    str(c.getLastCheckAt()), c.getLastCheckStatus(), c.getUpdatedBy());
        }
    }

    /** apiKey để trống = giữ nguyên khoá đang có. */
    public record ConfigRequest(String baseUrl, String dbName, String username, String apiKey) {
    }

    public record PersonRow(String name, String empCode, double hours, double days, int dayCount) {
    }

    public record CustomerRow(String name, String empCode, double days, String note,
                              String sourceFile, String importedAt, String importedBy) {
    }

    public record ReconcileRow(String name, String empCode, double erpHours, double erpDays, int erpDayCount,
                               double customerDays, double diffDays, String status, String statusLabel) {
    }

    public record ReconcileResponse(String period, List<ReconcileRow> rows,
                                    WorkdayReconciliation.Summary summary) {
    }

    public record ImportResult(int rows, String message) {
    }

    public record IssueRow(int row, String column, String message) {
    }

    public record ValidateResponse(boolean valid, int dataRows, List<IssueRow> issues) {
    }

    // ===== Cấu hình =====

    @GetMapping("/config")
    public ConfigResponse config() {
        return ConfigResponse.of(service.config());
    }

    @PutMapping("/config")
    public ConfigResponse saveConfig(@RequestBody ConfigRequest req, Authentication auth) {
        requireAdmin(auth);
        return ConfigResponse.of(service.saveConfig(req.baseUrl(), req.dbName(), req.username(),
                req.apiKey(), ApiAuth.actor(auth)));
    }

    @PostMapping("/config/test")
    public ImportResult testConnection(Authentication auth) {
        requireAdmin(auth);
        return new ImportResult(0, service.testConnection(ApiAuth.actor(auth)));
    }

    /** Dò tên database — nhận thẳng thông tin đang gõ trên form, chưa cần lưu. */
    @PostMapping("/config/detect-db")
    public ErpTimesheetService.DbProbe detectDb(@RequestBody ConfigRequest req, Authentication auth) {
        requireAdmin(auth);
        return service.detectDatabase(req.baseUrl(), req.username(), req.apiKey());
    }

    // ===== Nguồn 1: ERP =====

    @GetMapping("/periods")
    public List<String> periods() {
        return service.periods();
    }

    @PostMapping("/erp/sync")
    public ImportResult sync(@RequestParam String period, Authentication auth) {
        int n = service.syncPeriod(period, ApiAuth.actor(auth));
        return new ImportResult(n, "Đã tải " + n + " lần chấm công của kỳ " + period + " từ ERP.");
    }

    @GetMapping("/erp/rows")
    public List<PersonRow> erpRows(@RequestParam String period) {
        return service.erpRows(period).stream()
                .map(r -> new PersonRow(r.name(), null, r.hours(), r.days(), r.dayCount()))
                .toList();
    }

    // ===== Nguồn 2: khách hàng =====

    @PostMapping("/customer/validate")
    public ValidateResponse validateCustomer(@RequestParam("file") MultipartFile file) throws IOException {
        ValidationResult v = service.validateCustomerFile(file.getBytes(), file.getOriginalFilename());
        return new ValidateResponse(v.isValid(), v.getDataRows(),
                v.getIssues().stream().map(i -> new IssueRow(i.row(), i.column(), i.message())).toList());
    }

    @PostMapping("/customer/import")
    public ImportResult importCustomer(@RequestParam String period,
                                       @RequestParam("file") MultipartFile file,
                                       Authentication auth) throws IOException {
        int n = service.importCustomer(period, file.getBytes(), file.getOriginalFilename(),
                ApiAuth.actor(auth));
        return new ImportResult(n, "Đã lưu " + n + " dòng công khách hàng cho kỳ " + period + ".");
    }

    @GetMapping("/customer/rows")
    public List<CustomerRow> customerRows(@RequestParam String period) {
        return service.customerRows(period).stream()
                .map(c -> new CustomerRow(c.getEmployeeName(), c.getEmpCode(), c.getDays(), c.getNote(),
                        c.getSourceFile(), str(c.getImportedAt()), c.getImportedBy()))
                .toList();
    }

    /** Biểu mẫu trống để gửi khách hàng điền. */
    @GetMapping("/customer/template")
    public ResponseEntity<byte[]> customerTemplate() {
        byte[] bytes = ExcelReportEngine.writeSampleTemplate(ReportTemplate.CONG_KHACH_HANG);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-cong-khach-hang.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ===== Đối soát =====

    @GetMapping("/reconcile")
    public ReconcileResponse reconcile(@RequestParam String period) {
        List<WorkdayReconciliation.Row> rows = service.reconcile(period);
        return new ReconcileResponse(period,
                rows.stream().map(r -> new ReconcileRow(r.name(), r.empCode(), r.erpHours(), r.erpDays(),
                        r.erpDaysCount(), r.customerDays(), r.diffDays(),
                        r.status().name(), r.status().label())).toList(),
                WorkdayReconciliation.summarize(rows));
    }

    private static void requireAdmin(Authentication auth) {
        if (!ApiAuth.isAdmin(auth)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chỉ quản trị hệ thống được sửa kết nối ERP.");
        }
    }

    private static String str(Instant i) {
        return i == null ? null : i.toString();
    }
}
