package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ReportResult;
import com.bpm.domain.report.ReportRun;
import com.bpm.domain.report.ReportTemplate;
import com.bpm.domain.report.SafeWorkbookReader;
import com.bpm.domain.report.SunEffortEngine;
import com.bpm.domain.report.ValidationResult;
import com.bpm.infrastructure.ReportRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Công cụ Import Excel → Kết quả (Epic 4). Hiện có một loại tool: Tính toán nỗ lực dự án (Sun).
 * listTemplates (FR-D01) · validate (FR-D02 + an toàn NFR-09) · run (FR-D03/D06) · history (FR-D05) · download (FR-D04)
 * · sampleTemplate (tải biểu mẫu trống) · resultOf (mở lại kết quả trên màn hình).
 * Mọi thao tác run ghi audit qua AuditPort (NFR-06). Phần đọc/validate dùng chung ở {@link ExcelReportEngine}, công thức riêng ở
 * {@link SunEffortEngine} (test thuần).
 */
@Service
public class ExcelReportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelReportService.class);

    private final ReportRunRepository runRepo;
    private final AuditPort auditPort;
    private final ObjectMapper objectMapper;

    public ExcelReportService(ReportRunRepository runRepo, AuditPort auditPort, ObjectMapper objectMapper) {
        this.runRepo = runRepo;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
    }

    /** Kết quả tính toán: file .xlsx để tải về + dữ liệu để hiển thị trên màn hình. */
    private record Computed(byte[] output, ReportResult result) {
    }

    /** Danh sách loại tool cố định (FR-D01). */
    public List<ReportTemplate> listTemplates() {
        return Arrays.asList(ReportTemplate.values());
    }

    /** Kiểm tra định dạng file đầu vào theo mẫu (FR-D02) + an toàn file (NFR-09). Không ghi gì xuống DB. */
    public ValidationResult validate(String templateKey, byte[] bytes, String fileName) {
        ReportTemplate template = ReportTemplate.byKey(templateKey);
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            guardRowCount(wb);
            return ExcelReportEngine.validate(template, wb);
        } catch (Exception e) {
            ValidationResult vr = new ValidationResult();
            vr.addFileLevel(e.getMessage());
            return vr;
        }
    }

    /**
     * Chạy tính toán theo mẫu → sinh .xlsx kết quả + lưu lịch sử (FR-D03/D05/D06).
     * Ném IllegalArgumentException nếu file không hợp lệ (đã có lỗi validate) — service ghi run FAILED.
     */
    @Transactional
    public ReportRun run(String templateKey, byte[] bytes, String fileName, String actor) {
        ReportTemplate template = ReportTemplate.byKey(templateKey);
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            guardRowCount(wb);
            ValidationResult vr = ExcelReportEngine.validate(template, wb);
            if (!vr.isValid()) {
                throw new IllegalArgumentException("File đầu vào không hợp lệ — vui lòng kiểm định dạng và tải lại.");
            }
            Computed computed = compute(template, wb);
            ReportRun runRecord = new ReportRun(template.getKey(), actor, fileName, computed.output(),
                    "SUCCESS", "Tạo báo cáo thành công.");
            runRecord.setResultJson(toJson(computed.result()));
            runRecord = runRepo.save(runRecord);
            auditPort.record("EXCEL_REPORT_RUN", "ReportRun", runRecord.getId(), actor,
                    "template=" + template.getKey() + ", input=" + fileName);
            log.info("[excel-report] {} chạy mẫu {} từ {} → run {}", actor, template.getKey(), fileName, runRecord.getId());
            return runRecord;
        } catch (IllegalArgumentException | SafeWorkbookReader.UnsafeFileException e) {
            ReportRun failed = runRepo.save(new ReportRun(template.getKey(), actor, fileName, null,
                    "FAILED", e.getMessage()));
            auditPort.record("EXCEL_REPORT_RUN_FAILED", "ReportRun", failed.getId(), actor,
                    "template=" + template.getKey() + ", reason=" + e.getMessage());
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi xử lý báo cáo: " + e.getMessage(), e);
        }
    }

    /** Tính toán theo loại tool (mở rộng tool mới ở đây) → file .xlsx + dữ liệu hiển thị. */
    private Computed compute(ReportTemplate template, Workbook wb) {
        if (template == ReportTemplate.NO_LUC_DU_AN_SUN) {
            SunEffortEngine.SunReport report = SunEffortEngine.compute(wb);
            return new Computed(SunEffortEngine.write(report), SunEffortEngine.toResult(report));
        }
        throw new IllegalArgumentException("Chưa hỗ trợ tính toán cho loại tool: " + template.getKey());
    }

    /** Biểu mẫu Excel trống để người dùng tải về điền (đúng cấu trúc từng loại tool). */
    public byte[] sampleTemplate(String templateKey) {
        ReportTemplate template = ReportTemplate.byKey(templateKey);
        if (template == ReportTemplate.NO_LUC_DU_AN_SUN) {
            return SunEffortEngine.writeSampleTemplate();
        }
        return ExcelReportEngine.writeSampleTemplate(template);
    }

    /** Kết quả của một lần chạy để hiển thị lại trên màn hình; null nếu lần chạy cũ/thất bại không có dữ liệu. */
    @Transactional(readOnly = true)
    public ReportResult resultOf(String runId) {
        ReportRun r = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lần chạy: " + runId));
        if (!r.hasResult()) {
            return null;
        }
        try {
            return objectMapper.readValue(r.getResultJson(), ReportResult.class);
        } catch (Exception e) {
            log.warn("[excel-report] không đọc được result_json của run {}: {}", runId, e.getMessage());
            return null;
        }
    }

    private String toJson(ReportResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[excel-report] không ghi được result_json: {}", e.getMessage());
            return null; // kết quả vẫn tải được qua file .xlsx
        }
    }

    /** Lịch sử lần chạy (FR-D05). */
    @Transactional(readOnly = true)
    public List<ReportRun> history() {
        return runRepo.findAllByOrderByRunAtDesc();
    }

    /** Tải lại file kết quả của một lần chạy (FR-D04). */
    @Transactional(readOnly = true)
    public ReportRun download(String runId) {
        ReportRun r = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lần chạy: " + runId));
        if (!r.hasOutput()) {
            throw new IllegalArgumentException("Lần chạy này không có file kết quả (chạy thất bại).");
        }
        return r;
    }

    /** Chống OOM: tổng số dòng trên sheet đầu vượt ngưỡng → từ chối (NFR-09). */
    private void guardRowCount(Workbook wb) {
        if (wb.getNumberOfSheets() == 0) {
            return;
        }
        Sheet sheet = wb.getSheetAt(0);
        int rows = sheet.getLastRowNum() - sheet.getFirstRowNum();
        if (rows > SafeWorkbookReader.MAX_DATA_ROWS) {
            throw new SafeWorkbookReader.UnsafeFileException(
                    "File quá nhiều dòng (>" + SafeWorkbookReader.MAX_DATA_ROWS + ") — bị từ chối để tránh quá tải.");
        }
    }
}
