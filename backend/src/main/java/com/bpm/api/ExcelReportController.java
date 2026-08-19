package com.bpm.api;

import com.bpm.application.ExcelReportService;
import com.bpm.domain.report.ReportResult;
import com.bpm.domain.report.ReportRun;
import com.bpm.domain.report.ReportTemplate;
import com.bpm.domain.report.ValidationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Công cụ Import Excel → Báo cáo (Epic 4, UX-DR7) — ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/excel-reports")
public class ExcelReportController {

    private final ExcelReportService service;

    public ExcelReportController(ExcelReportService service) {
        this.service = service;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    // ===== DTO =====

    /** required = file phải có cột này; valueRequired = từng ô không được để trống. */
    public record ColumnDto(String header, String type, boolean required, boolean valueRequired) {
    }

    public record TemplateDto(String key, String title, String description, List<ColumnDto> requiredColumns,
                              List<ColumnDto> columns) {
        static TemplateDto of(ReportTemplate t) {
            return new TemplateDto(t.getKey(), t.getTitle(), t.getDescription(),
                    t.getRequiredColumns().stream()
                            .map(c -> new ColumnDto(c.header(), c.type().name(), true, c.valueRequired())).toList(),
                    t.getColumns().stream()
                            .map(c -> new ColumnDto(c.header(), c.type().name(), c.required(), c.valueRequired()))
                            .toList());
        }
    }

    public record IssueDto(int row, String column, String message) {
    }

    public record ValidationDto(boolean valid, int dataRows, List<IssueDto> issues) {
        static ValidationDto of(ValidationResult vr) {
            return new ValidationDto(vr.isValid(), vr.getDataRows(),
                    vr.getIssues().stream().map(i -> new IssueDto(i.row(), i.column(), i.message())).toList());
        }
    }

    public record RunDto(String id, String templateKey, String runBy, String runAt,
                         String inputFileName, String status, String message, boolean hasOutput,
                         boolean hasResult, ReportResult result) {
        /** Bản tóm tắt cho bảng lịch sử — không kèm dữ liệu kết quả để danh sách nhẹ. */
        static RunDto of(ReportRun r) {
            return of(r, null);
        }

        static RunDto of(ReportRun r, ReportResult result) {
            return new RunDto(r.getId(), r.getTemplateKey(), r.getRunBy(), r.getRunAt().toString(),
                    r.getInputFileName(), r.getStatus(), r.getMessage(), r.hasOutput(), r.hasResult(), result);
        }
    }

    // ===== endpoints =====

    /** Danh sách mẫu báo cáo cố định (FR-D01). */
    @GetMapping("/templates")
    public List<TemplateDto> templates() {
        return service.listTemplates().stream().map(TemplateDto::of).toList();
    }

    /** Kiểm tra định dạng file đầu vào theo mẫu (FR-D02 + NFR-09). */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidationDto validate(@RequestParam("templateKey") String templateKey,
                                  @RequestParam("file") MultipartFile file) throws IOException {
        return ValidationDto.of(service.validate(templateKey, file.getBytes(), file.getOriginalFilename()));
    }

    /** Chạy tính toán → trả kết quả để hiển thị ngay + lưu lịch sử (FR-D03/D05/D06). */
    @PostMapping(value = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunDto run(@RequestParam("templateKey") String templateKey,
                      @RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        ReportRun r = service.run(templateKey, file.getBytes(), file.getOriginalFilename(), actor(auth));
        return RunDto.of(r, service.resultOf(r.getId()));
    }

    /** Mở lại kết quả của một lần chạy cũ trên màn hình; 404 nếu lần chạy đó không lưu kết quả. */
    @GetMapping("/{id}/result")
    public ResponseEntity<ReportResult> result(@PathVariable String id) {
        ReportResult result = service.resultOf(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    /** Tải biểu mẫu Excel trống của một loại tool để người dùng điền rồi import lại. */
    @GetMapping("/templates/{key}/sample")
    public ResponseEntity<byte[]> sample(@PathVariable String key) {
        ReportTemplate template = ReportTemplate.byKey(key);
        String fname = "bieu-mau-" + template.getKey().toLowerCase() + ".xlsx";
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fname + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.sampleTemplate(key));
    }

    /** Lịch sử lần chạy (FR-D05). */
    @GetMapping("/history")
    public List<RunDto> history() {
        return service.history().stream().map(RunDto::of).toList();
    }

    /** Tải file kết quả .xlsx (FR-D04). */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        ReportRun r = service.download(id);
        String fname = "bao-cao-" + r.getTemplateKey().toLowerCase() + ".xlsx";
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fname + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(r.getOutputBytes());
    }
}
