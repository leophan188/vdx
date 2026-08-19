package com.bpm;

import com.bpm.application.ExcelReportService;
import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ReportResult;
import com.bpm.domain.report.ReportRun;
import com.bpm.domain.report.ValidationResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Công cụ Import Excel — chỉ còn một loại tool: Phân bổ chi phí nhân sự (Sun ITS). */
@SpringBootTest
@ActiveProfiles("test")
class ExcelReportServiceTest {

    private static final String SUN = "PHAN_BO_CHI_PHI_SUN_ITS";
    private static final double RATE = 2_818_181.82d;

    @Autowired
    ExcelReportService svc;

    /** Dựng file đầu vào tool Sun; withBadRow = thêm dòng sai kiểu Date/Total MD. */
    private byte[] buildInput(boolean withBadRow) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Raw normalized");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            String[] headers = {"Date", "Email", "Họ và tên", "Position", "Level", "Vendor",
                    "Project Name", "Total MD", "Expense (VNĐ)", "Manday (VNĐ)"};
            Row hr = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                hr.createCell(c).setCellValue(headers[c]);
            }

            // Sơn: 1 MD + 0,5 MD trên HR Platform, 0,5 MD trên MySGR → tổng 2 MD
            row(sheet, dateStyle, 1, LocalDate.of(2026, 7, 1), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                    "HR Platform", 1.0);
            row(sheet, dateStyle, 2, LocalDate.of(2026, 7, 10), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                    "HR Platform", 0.5);
            row(sheet, dateStyle, 3, LocalDate.of(2026, 7, 10), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                    "Nâng cấp MySGR - E360", 0.5);
            // Tùng: 1 MD trên MySGR
            row(sheet, dateStyle, 4, LocalDate.of(2026, 7, 2), "tungdt4@vmogroup.com", "Đinh Thanh Tùng",
                    "Nâng cấp MySGR - E360", 1.0);

            if (withBadRow) {
                Row bad = sheet.createRow(5);
                bad.createCell(0).setCellValue("không-phải-ngày");
                bad.createCell(1).setCellValue("x@vmogroup.com");
                bad.createCell(2).setCellValue("Sai Kiểu");
                bad.createCell(3).setCellValue("DEV");
                bad.createCell(4).setCellValue("Middle");
                bad.createCell(5).setCellValue("VMO");
                bad.createCell(6).setCellValue("HR Platform");
                bad.createCell(7).setCellValue("không-phải-số");
                bad.createCell(8).setCellValue(RATE);
                bad.createCell(9).setCellValue(RATE);
            }

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static void row(Sheet sheet, CellStyle dateStyle, int r, LocalDate date, String email,
                            String name, String project, double md) {
        Row row = sheet.createRow(r);
        Cell d = row.createCell(0);
        d.setCellValue(java.sql.Date.valueOf(date));
        d.setCellStyle(dateStyle);
        row.createCell(1).setCellValue(email);
        row.createCell(2).setCellValue(name);
        row.createCell(3).setCellValue("DEV");
        row.createCell(4).setCellValue("Middle");
        row.createCell(5).setCellValue("VMO");
        row.createCell(6).setCellValue(project);
        row.createCell(7).setCellValue(md);
        row.createCell(8).setCellValue(Math.round(md * RATE * 100.0) / 100.0);
        row.createCell(9).setCellValue(RATE);
    }

    @Test
    void listTemplates_onlyHasSunTool() {
        assertThat(svc.listTemplates()).singleElement()
                .satisfies(t -> {
                    assertThat(t.getKey()).isEqualTo(SUN);
                    assertThat(t.getTitle()).isEqualTo("Phân bổ chi phí nhân sự (Sun ITS)");
                });
    }

    @Test
    void run_sunEffort_sumsPerPersonAndPerProject() throws Exception {
        ReportRun r = svc.run(SUN, buildInput(false), "no-luc.xlsx", "tester");

        assertThat(r.getStatus()).isEqualTo("SUCCESS");
        assertThat(r.hasOutput()).isTrue();
        assertThat(r.hasResult()).isTrue();

        ReportResult result = svc.resultOf(r.getId());
        assertThat(result.tables()).extracting(ReportResult.Table::title)
                .containsExactly("Tổng theo nhân sự", "Tổng theo dự án", "Chi phí theo nhân sự dự án");
        assertThat(result.tables().get(0).rows()).hasSize(2);   // 2 nhân sự
        assertThat(result.tables().get(1).rows()).hasSize(2);   // 2 dự án
        assertThat(result.tables().get(2).rows()).hasSize(3);   // 3 cặp người × dự án
        assertThat(result.warnings()).isEmpty();
        // Tổng nỗ lực = 1 + 0,5 + 0,5 + 1 = 3 MD
        assertThat(result.metrics().get(0).value()).isEqualTo("3");
    }

    /**
     * Vòng khép kín: biểu mẫu người dùng tải về → import lại chạy được ngay,
     * kết quả lưu JSON và đọc lại đúng để hiển thị trên màn hình.
     */
    @Test
    void sampleTemplate_canBeImportedBack() {
        byte[] sample = svc.sampleTemplate(SUN);

        ReportRun r = svc.run(SUN, sample, "bieu-mau.xlsx", "tester");
        assertThat(r.getStatus()).isEqualTo("SUCCESS");

        ReportResult result = svc.resultOf(r.getId());
        assertThat(result).isNotNull();
        // 2 dòng ví dụ của cùng 1 người trên cùng 1 dự án → gộp thành 1 dòng
        assertThat(result.tables().get(0).rows()).hasSize(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void run_isDeterministic_sameInputSameOutput() throws Exception {
        byte[] input = buildInput(false);
        ReportResult first = svc.resultOf(svc.run(SUN, input, "a.xlsx", "tester").getId());
        ReportResult second = svc.resultOf(svc.run(SUN, input, "a.xlsx", "tester").getId());

        assertThat(second.tables()).isEqualTo(first.tables());
        assertThat(second.metrics()).isEqualTo(first.metrics());
    }

    @Test
    void validate_flagsBadTypeRows() throws Exception {
        ValidationResult vr = svc.validate(SUN, buildInput(true), "bad.xlsx");
        assertThat(vr.isValid()).isFalse();
        assertThat(vr.getIssues()).anyMatch(i -> "Date".equals(i.column()));
        assertThat(vr.getIssues()).anyMatch(i -> "Total MD".equals(i.column()));
    }

    @Test
    void run_rejectsNonXlsx() {
        byte[] notExcel = "hello,world".getBytes();
        assertThatThrownBy(() -> svc.run(SUN, notExcel, "data.csv", "tester"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byKey_rejectsRemovedTool() {
        assertThatThrownBy(() -> svc.validate("CHAM_CONG_OT", new byte[]{1}, "x.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy loại tool");
    }

    @Test
    void sanitize_neutralizesFormulaInjection() {
        assertThat(ExcelReportEngine.sanitize("=1+1")).isEqualTo("'=1+1");
        assertThat(ExcelReportEngine.sanitize("@cmd")).isEqualTo("'@cmd");
        assertThat(ExcelReportEngine.sanitize("Bình thường")).isEqualTo("Bình thường");
    }
}
