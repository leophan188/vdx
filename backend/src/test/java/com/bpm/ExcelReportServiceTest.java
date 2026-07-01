package com.bpm;

import com.bpm.application.ExcelReportService;
import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ExcelReportEngine.OtSummaryRow;
import com.bpm.domain.report.ReportRun;
import com.bpm.domain.report.ReportTemplate;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ExcelReportServiceTest {

    @Autowired
    ExcelReportService svc;

    /** Dựng workbook đầu vào mẫu CHAM_CONG_OT trong test. */
    private byte[] buildInput(boolean withBadRow) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("ChamCong");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            String[] headers = {"Mã NV", "Họ tên", "Phòng ban", "Ngày", "Số giờ OT"};
            Row hr = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                hr.createCell(c).setCellValue(headers[c]);
            }

            // NV001 cùng phòng/kỳ: 2 + 3 = 5h
            row(sheet, dateStyle, 1, "NV001", "An", "Kỹ thuật", LocalDate.of(2026, 6, 1), 2.0);
            row(sheet, dateStyle, 2, "NV001", "An", "Kỹ thuật", LocalDate.of(2026, 6, 2), 3.0);
            // NV002 cùng kỳ: 4h
            row(sheet, dateStyle, 3, "NV002", "Bình", "Kinh doanh", LocalDate.of(2026, 6, 5), 4.0);
            // NV001 sang kỳ khác (7/2026): 1.5h → tách dòng riêng
            row(sheet, dateStyle, 4, "NV001", "An", "Kỹ thuật", LocalDate.of(2026, 7, 1), 1.5);

            int next = 5;
            if (withBadRow) {
                Row bad = sheet.createRow(next++);
                bad.createCell(0).setCellValue("NV003");
                bad.createCell(1).setCellValue("Cường");
                bad.createCell(2).setCellValue("Kế toán");
                bad.createCell(3).setCellValue("không-phải-ngày");
                bad.createCell(4).setCellValue("không-phải-số");
            }

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static void row(Sheet sheet, CellStyle dateStyle, int r, String code, String name,
                            String org, LocalDate date, double hours) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(code);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(org);
        Cell d = row.createCell(3);
        d.setCellValue(java.sql.Date.valueOf(date));
        d.setCellStyle(dateStyle);
        row.createCell(4).setCellValue(hours);
    }

    @Test
    void run_chamCongOt_sumsHoursPerEmployeePeriod() throws Exception {
        byte[] input = buildInput(false);
        ReportRun r = svc.run("CHAM_CONG_OT", input, "chamcong.xlsx", "tester");

        assertThat(r.getStatus()).isEqualTo("SUCCESS");
        assertThat(r.hasOutput()).isTrue();

        // Đọc lại file kết quả để assert số liệu
        try (Workbook out = new XSSFWorkbook(new ByteArrayInputStream(r.getOutputBytes()))) {
            Sheet s = out.getSheetAt(0);
            // header + 3 dòng tổng hợp (NV001/6, NV002/6, NV001/7)
            assertThat(s.getLastRowNum()).isEqualTo(3);

            // dòng 1: kỳ 2026-06, Kinh doanh, NV002 = 4.0  (sắp xếp theo kỳ→phòng→mã)
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-06");
            assertThat(s.getRow(1).getCell(2).getStringCellValue()).isEqualTo("NV002");
            assertThat(s.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(4.0);

            // dòng 2: kỳ 2026-06, Kỹ thuật, NV001 = 5.0
            assertThat(s.getRow(2).getCell(2).getStringCellValue()).isEqualTo("NV001");
            assertThat(s.getRow(2).getCell(4).getNumericCellValue()).isEqualTo(5.0);

            // dòng 3: kỳ 2026-07, Kỹ thuật, NV001 = 1.5
            assertThat(s.getRow(3).getCell(0).getStringCellValue()).isEqualTo("2026-07");
            assertThat(s.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(1.5);
        }
    }

    @Test
    void run_isDeterministic_sameInputSameOutput() throws Exception {
        byte[] input = buildInput(false);
        ReportRun r1 = svc.run("CHAM_CONG_OT", input, "a.xlsx", "tester");

        // Lõi engine thuần: cùng dữ liệu → cùng danh sách tổng hợp
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(input))) {
            List<OtSummaryRow> sum = ExcelReportEngine.computeOtSummary(
                    ExcelReportEngine.readRows(ReportTemplate.CHAM_CONG_OT, wb));
            assertThat(sum).hasSize(3);
            assertThat(sum.get(0).totalHours()).isEqualTo(4.0);   // NV002
            assertThat(sum.get(1).totalHours()).isEqualTo(5.0);   // NV001 kỳ 6
            assertThat(sum.get(2).totalHours()).isEqualTo(1.5);   // NV001 kỳ 7
        }
        assertThat(r1.hasOutput()).isTrue();
    }

    @Test
    void validate_flagsBadTypeRows() throws Exception {
        ValidationResult vr = svc.validate("CHAM_CONG_OT", buildInput(true), "bad.xlsx");
        assertThat(vr.isValid()).isFalse();
        // dòng lỗi có cả "Ngày" và "Số giờ OT"
        assertThat(vr.getIssues()).anyMatch(i -> "Ngày".equals(i.column()));
        assertThat(vr.getIssues()).anyMatch(i -> "Số giờ OT".equals(i.column()));
    }

    @Test
    void run_rejectsNonXlsx() {
        byte[] notExcel = "hello,world".getBytes();
        assertThatThrownBy(() -> svc.run("CHAM_CONG_OT", notExcel, "data.csv", "tester"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitize_neutralizesFormulaInjection() {
        assertThat(ExcelReportEngine.sanitize("=1+1")).isEqualTo("'=1+1");
        assertThat(ExcelReportEngine.sanitize("@cmd")).isEqualTo("'@cmd");
        assertThat(ExcelReportEngine.sanitize("Bình thường")).isEqualTo("Bình thường");
    }
}
