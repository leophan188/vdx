package com.bpm;

import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ReportResult;
import com.bpm.domain.report.ReportTemplate;
import com.bpm.domain.report.SunEffortEngine;
import com.bpm.domain.report.ValidationResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lõi tool "Tính toán nỗ lực dự án (Sun)" — test thuần, không cần Spring. */
class SunEffortEngineTest {

    private static final double SON_RATE = 2_818_181.82d;
    private static final double TUNG_RATE = 3_090_909.09d;

    /** Dựng một dòng đầu vào như engine đọc được từ file. */
    private static ExcelReportEngine.InputRow row(int sheetRow, LocalDate date, String email, String name,
                                                  String project, double md, double expense, double manday) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("Date", date);
        v.put("Email", email);
        v.put("Họ và tên", name);
        v.put("Position", "DEV");
        v.put("Level", "Middle");
        v.put("Vendor", "VMO");
        v.put("Project Name", project);
        v.put("Total MD", md);
        v.put("Expense (VNĐ)", expense);
        v.put("Manday (VNĐ)", manday);
        return new ExcelReportEngine.InputRow(sheetRow, v);
    }

    /** Sơn: 1 MD HR Platform + 0,5 MD HR Platform + 1 MD MySGR. Tùng: 1 MD MySGR. */
    private static List<ExcelReportEngine.InputRow> sampleInput() {
        List<ExcelReportEngine.InputRow> rows = new ArrayList<>();
        rows.add(row(2, LocalDate.of(2026, 7, 1), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                "HR Platform", 1.0, SON_RATE, SON_RATE));
        rows.add(row(3, LocalDate.of(2026, 7, 10), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                "HR Platform", 0.5, SON_RATE / 2, SON_RATE));
        rows.add(row(4, LocalDate.of(2026, 7, 10), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                "Nâng cấp MySGR - E360", 0.5, SON_RATE / 2, SON_RATE));
        rows.add(row(5, LocalDate.of(2026, 7, 2), "tungdt4@vmogroup.com", "Đinh Thanh Tùng",
                "Nâng cấp MySGR - E360", 1.0, TUNG_RATE, TUNG_RATE));
        return rows;
    }

    @Test
    void compute_sumsPerPersonAndPerProject() {
        SunEffortEngine.SunReport rep = SunEffortEngine.compute(sampleInput());

        assertThat(rep.byPerson()).hasSize(2);
        // sắp xếp theo họ tên: "Đinh Thanh Tùng" < "Đàm Ngọc Sơn" theo compareTo (à/i khác code point)
        SunEffortEngine.PersonTotal son = rep.byPerson().stream()
                .filter(p -> p.name().equals("Đàm Ngọc Sơn")).findFirst().orElseThrow();
        assertThat(son.totalMd()).isEqualTo(2.0);                       // 1 + 0,5 + 0,5
        assertThat(son.expense()).isEqualTo(round2(SON_RATE * 2));

        SunEffortEngine.ProjectTotal hr = rep.byProject().stream()
                .filter(p -> p.project().equals("HR Platform")).findFirst().orElseThrow();
        assertThat(hr.totalMd()).isEqualTo(1.5);
        assertThat(hr.expense()).isEqualTo(round2(SON_RATE * 1.5));
    }

    @Test
    void compute_pairsPersonWithProjectAndKeepsDateRange() {
        SunEffortEngine.SunReport rep = SunEffortEngine.compute(sampleInput());

        assertThat(rep.byPersonProject()).hasSize(3); // Sơn×HR, Sơn×MySGR, Tùng×MySGR
        SunEffortEngine.PersonProjectTotal sonHr = rep.byPersonProject().stream()
                .filter(p -> p.name().equals("Đàm Ngọc Sơn") && p.project().equals("HR Platform"))
                .findFirst().orElseThrow();

        assertThat(sonHr.totalMd()).isEqualTo(1.5);
        assertThat(sonHr.dateRange()).isEqualTo("01/07/2026 – 10/07/2026");
        assertThat(sonHr.manday()).isEqualTo(round2(SON_RATE));   // đơn giá bình quân = expense / MD
        assertThat(sonHr.email()).isEqualTo("sondn3@vmogroup.com");
    }

    @Test
    void compute_warnsWhenExpenseDoesNotMatchMdTimesManday() {
        List<ExcelReportEngine.InputRow> rows = new ArrayList<>(sampleInput());
        rows.add(row(6, LocalDate.of(2026, 7, 3), "sai@vmogroup.com", "Sai Chi Phí",
                "HR Platform", 1.0, 999_999d, SON_RATE));

        SunEffortEngine.SunReport rep = SunEffortEngine.compute(rows);
        assertThat(rep.warnings()).hasSize(1);
        assertThat(rep.warnings().get(0)).contains("Dòng 6").contains("lệch");
    }

    @Test
    void compute_noWarningWhenOnlyRoundingDifference() {
        List<ExcelReportEngine.InputRow> rows = List.of(
                row(2, LocalDate.of(2026, 7, 1), "a@vmogroup.com", "A", "P", 1.0, SON_RATE + 0.4d, SON_RATE));
        assertThat(SunEffortEngine.compute(rows).warnings()).isEmpty();
    }

    @Test
    void toResult_buildsThreeTablesAndMetrics() {
        ReportResult result = SunEffortEngine.toResult(SunEffortEngine.compute(sampleInput()));

        assertThat(result.tables()).extracting(ReportResult.Table::key)
                .containsExactly("byPerson", "byProject", "byPersonProject");
        assertThat(result.tables().get(2).columns()).hasSize(10);
        assertThat(result.tables().get(2).types()).hasSize(10);
        assertThat(result.metrics()).extracting(ReportResult.Metric::label)
                .contains("Tổng nỗ lực (MD)", "Tổng chi phí (VNĐ)", "Số nhân sự", "Số dự án");
        // 3 MD Sơn+Tùng = 3.0 → hiển thị kiểu VN
        assertThat(result.metrics().get(0).value()).isEqualTo("3");
    }

    @Test
    void write_producesFourSheetsInTemplateOrder() throws Exception {
        byte[] out = SunEffortEngine.write(SunEffortEngine.compute(sampleInput()));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            assertThat(wb.getSheetName(0)).isEqualTo("Raw normalized");
            assertThat(wb.getSheetName(1)).isEqualTo("Tổng theo nhân sự");
            assertThat(wb.getSheetName(2)).isEqualTo("Tổng theo dự án");
            assertThat(wb.getSheetName(3)).isEqualTo("Chi phí theo nhân sự dự án");

            Sheet person = wb.getSheetAt(1);
            assertThat(person.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Họ và tên");
            assertThat(person.getLastRowNum()).isEqualTo(2); // header + 2 nhân sự
        }
    }

    @Test
    void sampleTemplate_hasAllTwelveInputColumns() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(SunEffortEngine.writeSampleTemplate()))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            Row header = wb.getSheetAt(0).getRow(0);
            assertThat((int) header.getLastCellNum()).isEqualTo(12);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("Manday (VNĐ)");
            assertThat(header.getCell(11).getStringCellValue()).startsWith("Thời gian thực hiện");
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(2); // header + 2 dòng ví dụ
        }
    }

    /** Biểu mẫu tải về phải import lại được ngay: header dài của cột tuỳ chọn vẫn khớp khai báo ngắn. */
    @Test
    void validate_acceptsSampleTemplateFilledIn() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(SunEffortEngine.writeSampleTemplate()))) {
            ValidationResult vr = ExcelReportEngine.validate(ReportTemplate.NO_LUC_DU_AN_SUN, wb);
            assertThat(vr.isValid()).isTrue();
            assertThat(vr.getDataRows()).isEqualTo(2);
        }
    }

    /** Thiếu cột tuỳ chọn (K/L) không được coi là lỗi. */
    @Test
    void validate_optionalColumnsMayBeAbsent() throws Exception {
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
            Row dr = sheet.createRow(1);
            Cell d = dr.createCell(0);
            d.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 7, 1)));
            d.setCellStyle(dateStyle);
            dr.createCell(1).setCellValue("a@vmogroup.com");
            dr.createCell(2).setCellValue("Nguyễn A");
            dr.createCell(3).setCellValue("DEV");
            dr.createCell(4).setCellValue("Middle");
            dr.createCell(5).setCellValue("VMO");
            dr.createCell(6).setCellValue("HR Platform");
            dr.createCell(7).setCellValue(1.0);
            dr.createCell(8).setCellValue(SON_RATE);
            dr.createCell(9).setCellValue(SON_RATE);
            wb.write(bos);

            try (Workbook reopened = new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()))) {
                assertThat(ExcelReportEngine.validate(ReportTemplate.NO_LUC_DU_AN_SUN, reopened).isValid()).isTrue();
                SunEffortEngine.SunReport rep = SunEffortEngine.compute(reopened);
                assertThat(rep.byPerson()).hasSize(1);
                assertThat(rep.byPerson().get(0).totalMd()).isEqualTo(1.0);
            }
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0d) / 100.0d;
    }
}
