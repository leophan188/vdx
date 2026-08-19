package com.bpm.domain.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lõi tính toán báo cáo (Epic 4) — KHÔNG phụ thuộc Spring để dễ test thuần (cùng input → cùng output, FR-D03).
 * Tách rõ: parse (đọc workbook đã được {@link SafeWorkbookReader} mở an toàn) → validate cột/kiểu (FR-D02)
 * → compute theo mẫu → ghi .xlsx kết quả có trung hoà formula/CSV injection (NFR-09).
 */
public final class ExcelReportEngine {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelReportEngine() {
    }

    /** Một dòng dữ liệu đầu vào đã chuẩn hoá theo cột bắt buộc của mẫu. */
    public record InputRow(int sheetRow, Map<String, Object> values) {
    }

    /** Dòng kết quả gộp OT theo (kỳ, phòng ban, mã NV). */
    public record OtSummaryRow(String period, String orgUnit, String empCode, String empName, double totalHours) {
    }

    // ============================ VALIDATE ============================

    /**
     * Kiểm tra header có đủ cột bắt buộc + từng ô đúng kiểu (FR-D02).
     * Trả về ValidationResult (danh sách lỗi dòng/cột) — không ném khi dữ liệu sai, chỉ ghi nhận lỗi.
     */
    public static ValidationResult validate(ReportTemplate template, Workbook wb) {
        ValidationResult vr = new ValidationResult();
        Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
        if (sheet == null) {
            vr.addFileLevel("File không có sheet dữ liệu.");
            return vr;
        }
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            vr.addFileLevel("Thiếu dòng tiêu đề (header).");
            return vr;
        }

        Map<String, Integer> colIndex = headerIndex(header);
        for (ReportTemplate.Column col : template.getRequiredColumns()) {
            if (resolve(colIndex, col.header()) == null) {
                vr.addFileLevel("Thiếu cột bắt buộc: \"" + col.header() + "\".");
            }
        }
        if (!vr.isValid()) {
            return vr; // header sai → không kiểm tiếp từng dòng
        }

        int firstData = sheet.getFirstRowNum() + 1;
        int dataRows = 0;
        for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, colIndex, template)) {
                continue;
            }
            dataRows++;
            for (ReportTemplate.Column col : template.getRequiredColumns()) {
                Cell cell = row.getCell(resolve(colIndex, col.header()));
                String err = checkCell(cell, col.type());
                if (err != null) {
                    vr.add(r + 1, col.header(), err); // r 0-based → hiển thị 1-based
                }
            }
        }
        vr.setDataRows(dataRows);
        if (dataRows == 0 && vr.isValid()) {
            vr.addFileLevel("File không có dòng dữ liệu nào.");
        }
        return vr;
    }

    private static String checkCell(Cell cell, ReportTemplate.ColumnType type) {
        boolean empty = cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
        if (empty) {
            return "Ô để trống (bắt buộc).";
        }
        switch (type) {
            case NUMBER:
                Double n = numericOrNull(cell);
                if (n == null) {
                    return "Phải là số.";
                }
                if (n < 0) {
                    return "Số không được âm.";
                }
                return null;
            case DATE:
                if (dateOrNull(cell) == null) {
                    return "Phải là ngày hợp lệ.";
                }
                return null;
            case TEXT:
            default:
                return null;
        }
    }

    // ============================ COMPUTE (mẫu CHAM_CONG_OT) ============================

    /**
     * Đọc các dòng dữ liệu đã chuẩn hoá từ sheet đầu tiên theo cột bắt buộc của mẫu.
     * Giả định file đã qua validate hợp lệ; vẫn bỏ qua dòng trống.
     */
    public static List<InputRow> readRows(ReportTemplate template, Workbook wb) {
        List<InputRow> out = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> colIndex = headerIndex(header);
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, colIndex, template)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (ReportTemplate.Column col : template.getColumns()) {
                Integer ci = resolve(colIndex, col.header());
                Cell cell = ci == null ? null : row.getCell(ci); // cột tuỳ chọn có thể không có trong file
                values.put(col.header(), switch (col.type()) {
                    case NUMBER -> numericOrNull(cell);
                    case DATE -> dateOrNull(cell);
                    default -> stringValue(cell);
                });
            }
            out.add(new InputRow(r + 1, values));
        }
        return out;
    }

    /**
     * Công thức mẫu CHAM_CONG_OT: gộp tổng "Số giờ OT" theo khoá (kỳ YYYY-MM, phòng ban, mã NV).
     * Sắp xếp ổn định theo kỳ → phòng ban → mã NV để cùng input cho cùng output (FR-D03).
     */
    public static List<OtSummaryRow> computeOtSummary(List<InputRow> rows) {
        Map<String, OtAccumulator> acc = new LinkedHashMap<>();
        for (InputRow ir : rows) {
            String empCode = String.valueOf(ir.values().get("Mã NV")).trim();
            String empName = String.valueOf(ir.values().get("Họ tên")).trim();
            String orgUnit = String.valueOf(ir.values().get("Phòng ban")).trim();
            LocalDate date = (LocalDate) ir.values().get("Ngày");
            Double hours = (Double) ir.values().get("Số giờ OT");
            String period = date == null ? "" : String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            String key = period + "" + orgUnit + "" + empCode;
            acc.computeIfAbsent(key, k -> new OtAccumulator(period, orgUnit, empCode, empName))
                    .add(hours == null ? 0.0 : hours);
        }
        List<OtSummaryRow> result = new ArrayList<>();
        for (OtAccumulator a : acc.values()) {
            result.add(new OtSummaryRow(a.period, a.orgUnit, a.empCode, a.empName,
                    Math.round(a.total * 100.0) / 100.0));
        }
        result.sort((x, y) -> {
            int c = x.period().compareTo(y.period());
            if (c != 0) return c;
            c = x.orgUnit().compareTo(y.orgUnit());
            if (c != 0) return c;
            return x.empCode().compareTo(y.empCode());
        });
        return result;
    }

    private static final class OtAccumulator {
        final String period;
        final String orgUnit;
        final String empCode;
        final String empName;
        double total;

        OtAccumulator(String period, String orgUnit, String empCode, String empName) {
            this.period = period;
            this.orgUnit = orgUnit;
            this.empCode = empCode;
            this.empName = empName;
        }

        void add(double h) { this.total += h; }
    }

    // ============================ WRITE (.xlsx, chống injection) ============================

    /** Sinh file .xlsx kết quả tổng hợp OT, các ô text đã trung hoà formula/CSV injection (NFR-09). */
    public static byte[] writeOtReport(List<OtSummaryRow> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Tong hop OT");
            String[] headers = {"Kỳ", "Phòng ban", "Mã NV", "Họ tên", "Tổng giờ OT"};
            Row hr = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                hr.createCell(c).setCellValue(headers[c]);
            }
            int r = 1;
            for (OtSummaryRow row : rows) {
                Row dr = sheet.createRow(r++);
                dr.createCell(0).setCellValue(sanitize(row.period()));
                dr.createCell(1).setCellValue(sanitize(row.orgUnit()));
                dr.createCell(2).setCellValue(sanitize(row.empCode()));
                dr.createCell(3).setCellValue(sanitize(row.empName()));
                dr.createCell(4).setCellValue(row.totalHours()); // số → an toàn
            }
            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được file báo cáo: " + e.getMessage(), e);
        }
    }

    /** Kết quả OT dạng trung lập để hiển thị thẳng lên màn hình (FR-D03). */
    public static ReportResult toResult(List<OtSummaryRow> rows) {
        double totalHours = rows.stream().mapToDouble(OtSummaryRow::totalHours).sum();
        List<List<Object>> data = new ArrayList<>();
        for (OtSummaryRow r : rows) {
            data.add(List.of(r.period(), r.orgUnit(), r.empCode(), r.empName(), r.totalHours()));
        }
        List<ReportResult.Metric> metrics = List.of(
                new ReportResult.Metric("Tổng giờ OT", SunEffortEngine.decimal(totalHours)),
                new ReportResult.Metric("Số dòng tổng hợp", String.valueOf(rows.size())));
        List<ReportResult.Table> tables = List.of(new ReportResult.Table("otSummary", "Tổng hợp OT",
                List.of("Kỳ", "Phòng ban", "Mã NV", "Họ tên", "Tổng giờ OT"),
                List.of("TEXT", "TEXT", "TEXT", "TEXT", "NUMBER"),
                data));
        return new ReportResult(metrics, tables, List.of());
    }

    /** Biểu mẫu trống mặc định: một sheet có đúng các cột khai báo của mẫu (dùng cho mẫu chưa có biểu mẫu riêng). */
    public static byte[] writeSampleTemplate(ReportTemplate template) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Dữ liệu");
            Row hr = sheet.createRow(0);
            List<ReportTemplate.Column> cols = template.getColumns();
            for (int c = 0; c < cols.size(); c++) {
                hr.createCell(c).setCellValue(cols.get(c).header());
                sheet.autoSizeColumn(c);
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được biểu mẫu: " + e.getMessage(), e);
        }
    }

    /**
     * Trung hoà formula/CSV injection: ô bắt đầu bằng = + - @ (hoặc tab/CR) được tiền tố dấu nháy đơn
     * để Excel/CSV coi là văn bản, không thực thi (NFR-09).
     */
    public static String sanitize(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        char c0 = s.charAt(0);
        if (c0 == '=' || c0 == '+' || c0 == '-' || c0 == '@' || c0 == '\t' || c0 == '\r') {
            return "'" + s;
        }
        return s;
    }

    // ============================ helpers ============================

    private static Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null) {
                String h = norm(FORMATTER.formatCellValue(cell));
                if (!h.isEmpty() && !idx.containsKey(h)) {
                    idx.put(h, c);
                }
            }
        }
        return idx;
    }

    private static boolean isBlankRow(Row row, Map<String, Integer> colIndex, ReportTemplate template) {
        if (row == null) {
            return true;
        }
        for (ReportTemplate.Column col : template.getRequiredColumns()) {
            Integer ci = resolve(colIndex, col.header());
            Cell cell = ci == null ? null : row.getCell(ci);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !FORMATTER.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tìm chỉ số cột theo header khai báo: khớp chính xác trước, sau đó chấp nhận header trong file
     * DÀI HƠN nhưng cùng phần đầu (vd khai báo "Thời gian thực hiện" khớp
     * "Thời gian thực hiện (chỉ điền số giờ, không điền ký tự khác)"). Trả null nếu không có cột.
     */
    static Integer resolve(Map<String, Integer> colIndex, String header) {
        String want = norm(header);
        Integer exact = colIndex.get(want);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Integer> e : colIndex.entrySet()) {
            if (e.getKey().startsWith(want)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Chuẩn hoá header để so khớp: bỏ khoảng trắng thừa/xuống dòng trong ô, không phân biệt hoa thường. */
    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String stringValue(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private static Double numericOrNull(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue().trim().replace(',', '.');
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate dateOrNull(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue().trim();
            for (String fmt : new String[]{"yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy"}) {
                try {
                    return LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern(fmt));
                } catch (Exception ignored) {
                    // thử định dạng tiếp theo
                }
            }
        }
        return null;
    }
}
