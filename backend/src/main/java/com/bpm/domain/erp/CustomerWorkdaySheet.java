package com.bpm.domain.erp;

import com.bpm.domain.report.ExcelReportEngine;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc/ghi bảng công khách hàng dạng NGANG: mỗi nhân sự một dòng, mỗi ngày trong tháng một cột.
 *
 * Không dùng {@code ReportTemplate} như các tool khác vì số cột ở đây thay đổi theo tháng (28–31),
 * còn ReportTemplate khai cột cố định trong code. Bù lại phải tự nhận diện cột, nên phần nhận diện
 * được viết rộng tay: tiêu đề ngày chấp nhận "1", "01", "1/8", "01/08/2026" hay ô kiểu ngày tháng —
 * bảng chấm công khách hàng gửi mỗi nơi đánh số một kiểu, bắt họ sửa file cho khớp là cách chắc chắn
 * để không ai dùng.
 */
public final class CustomerWorkdaySheet {

    private static final DataFormatter FORMATTER = new DataFormatter();

    /** Tiêu đề cột tên và mã — chấp nhận vài cách viết thường gặp. */
    private static final List<String> NAME_HEADERS = List.of("ho va ten", "ho ten", "nhan su", "ten nhan vien", "ten");
    private static final List<String> CODE_HEADERS = List.of("ma nv", "ma nhan vien", "id", "ma nhan su", "ma");

    private CustomerWorkdaySheet() {
    }

    /** Một ô công đọc được: ai, ngày nào, mấy công. */
    public record Cellule(String empCode, String name, LocalDate date, double days) {
    }

    public record ParseResult(List<Cellule> cells, List<String> problems) {
    }

    /**
     * Đọc file cho một kỳ. Ngày nào không có số thì bỏ qua (ô trống = không đi làm), giá trị không
     * phải số thì ghi vào {@code problems} và bỏ dòng đó — báo rõ hơn là im lặng bỏ qua.
     */
    public static ParseResult read(Workbook wb, YearMonth period) {
        List<Cellule> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        if (sheet == null) {
            problems.add("File không có sheet nào.");
            return new ParseResult(out, problems);
        }

        int headerRow = -1;
        int nameCol = -1;
        int codeCol = -1;
        Map<Integer, LocalDate> dayCols = new LinkedHashMap<>();
        // Dò tối đa 20 dòng đầu để tìm hàng tiêu đề: bảng khách hàng thường có vài dòng tiêu đề lớn,
        // logo hay tên công ty phía trên.
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 20); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int tmpName = -1;
            int tmpCode = -1;
            Map<Integer, LocalDate> tmpDays = new LinkedHashMap<>();
            for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
                String raw = text(row.getCell(c));
                if (raw.isBlank()) {
                    continue;
                }
                String norm = ExcelReportEngine.sanitize(raw).trim();
                String key = normalize(norm);
                if (tmpName < 0 && NAME_HEADERS.contains(key)) {
                    tmpName = c;
                    continue;
                }
                if (tmpCode < 0 && CODE_HEADERS.contains(key)) {
                    tmpCode = c;
                    continue;
                }
                LocalDate d = dayOf(row.getCell(c), norm, period);
                if (d != null) {
                    tmpDays.put(c, d);
                }
            }
            if (tmpName >= 0 && tmpDays.size() >= 3) {
                headerRow = r;
                nameCol = tmpName;
                codeCol = tmpCode;
                dayCols = tmpDays;
                break;
            }
        }
        if (headerRow < 0) {
            problems.add("Không tìm thấy dòng tiêu đề — cần một cột \"Họ và tên\" và các cột ngày "
                    + "(1, 2, 3… hoặc 01/" + String.format("%02d", period.getMonthValue()) + ").");
            return new ParseResult(out, problems);
        }

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String name = text(row.getCell(nameCol)).trim();
            if (name.isBlank()) {
                continue;
            }
            String lower = normalize(name);
            // Dòng tổng cuối bảng không phải nhân sự.
            if (lower.startsWith("tong") || lower.startsWith("total") || lower.startsWith("cong ")) {
                continue;
            }
            String code = codeCol >= 0 ? text(row.getCell(codeCol)).trim() : null;
            for (Map.Entry<Integer, LocalDate> dc : dayCols.entrySet()) {
                Cell cell = row.getCell(dc.getKey());
                Double v = numberOf(cell);
                if (v == null) {
                    String raw = text(cell).trim();
                    if (!raw.isBlank() && !"-".equals(raw) && !"x".equalsIgnoreCase(raw)) {
                        problems.add("Dòng " + (r + 1) + ", ngày " + dc.getValue().getDayOfMonth()
                                + ": \"" + raw + "\" không phải số công.");
                    }
                    continue;
                }
                if (v != 0) {
                    out.add(new Cellule(blankToNull(code), name, dc.getValue(), v));
                }
            }
        }
        return new ParseResult(out, problems);
    }

    /** Biểu mẫu trống cho một kỳ: cột Mã NV, Họ và tên, rồi mỗi ngày trong tháng một cột. */
    public static byte[] writeTemplate(YearMonth period) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Cong " + period);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã NV");
            header.createCell(1).setCellValue("Họ và tên");
            int days = period.lengthOfMonth();
            for (int d = 1; d <= days; d++) {
                header.createCell(1 + d).setCellValue(d);
            }
            sheet.createRow(1).createCell(1).setCellValue("(mỗi dòng một nhân sự; điền 1 hoặc 0.5, để trống nếu không đi làm)");
            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 8000);
            for (int d = 1; d <= days; d++) {
                sheet.setColumnWidth(1 + d, 1400);
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được biểu mẫu: " + e.getMessage(), e);
        }
    }

    /**
     * Ô tiêu đề này có phải một NGÀY trong kỳ không.
     * Nhận: số 1..31, chuỗi "1"/"01", "1/8", "01/08", "01/08/2026", và ô định dạng ngày thật.
     */
    private static LocalDate dayOf(Cell cell, String rawText, YearMonth period) {
        if (cell != null) {
            try {
                if (cell.getCellType() == CellType.NUMERIC
                        && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                    return YearMonth.from(d).equals(period) ? d : null;
                }
            } catch (Exception ignored) {
                // ô hỏng thì rơi xuống nhánh đọc theo chữ
            }
        }
        String t = rawText.trim();
        if (t.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d{1,2})(?:[/\\-.](\\d{1,2}))?(?:[/\\-.](\\d{2,4}))?$").matcher(t);
        if (!m.matches()) {
            return null;
        }
        int day = Integer.parseInt(m.group(1));
        if (day < 1 || day > period.lengthOfMonth()) {
            return null;
        }
        // Có ghi tháng thì phải đúng tháng của kỳ, tránh nhận nhầm cột "Tổng 12" thành ngày 12.
        if (m.group(2) != null && Integer.parseInt(m.group(2)) != period.getMonthValue()) {
            return null;
        }
        return period.atDay(day);
    }

    private static Double numberOf(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.STRING) {
            return ExcelReportEngine.parseNumber(cell.getStringCellValue());
        }
        return null;
    }

    private static String text(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell);
    }

    /** Bỏ dấu + chữ thường để so tiêu đề, giống cách EmployeeFileReader nhận diện cột. */
    private static String normalize(String s) {
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
