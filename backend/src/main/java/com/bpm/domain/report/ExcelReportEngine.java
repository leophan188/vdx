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
                String err = checkCell(cell, col);
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

    private static String checkCell(Cell cell, ReportTemplate.Column col) {
        boolean empty = cell == null || effectiveType(cell) == CellType.BLANK
                || (effectiveType(cell) == CellType.STRING && stringValue(cell).isBlank());
        if (empty) {
            return col.valueRequired() ? "Ô để trống (bắt buộc)." : null;
        }
        switch (col.type()) {
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

    // ============================ ĐỌC DỮ LIỆU ============================

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

    // ============================ WRITE (.xlsx, chống injection) ============================

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

    /**
     * Dòng KHÔNG phải dữ liệu → bỏ qua (không tính, không báo lỗi): dòng rỗng, hoặc dòng mà mọi cột định danh
     * (Requirement.KEY) đều trống — điển hình là dòng tổng cuối bảng chỉ có mỗi ô =SUM(...), hoặc dòng ghi chú.
     * Mẫu không khai báo cột KEY nào thì quay về quy tắc cũ: trống hết các cột bắt buộc mới bỏ qua.
     */
    private static boolean isBlankRow(Row row, Map<String, Integer> colIndex, ReportTemplate template) {
        if (row == null) {
            return true;
        }
        List<ReportTemplate.Column> probe = template.getColumns().stream()
                .filter(ReportTemplate.Column::identity).toList();
        if (probe.isEmpty()) {
            probe = template.getRequiredColumns();
        }
        for (ReportTemplate.Column col : probe) {
            Integer ci = resolve(colIndex, col.header());
            Cell cell = ci == null ? null : row.getCell(ci);
            if (cell != null && effectiveType(cell) != CellType.BLANK && !stringValue(cell).isBlank()) {
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

    /**
     * Kiểu THỰC của ô: với ô công thức trả về kiểu của KẾT QUẢ đã được Excel tính sẵn và lưu trong file.
     * Bảng chấm công thật thường có Total MD / Expense / Manday là công thức (=K2, =H2*J2, =62000000/22),
     * nếu chỉ nhìn getCellType() thì mọi ô đó đều là FORMULA và bị coi là "không phải số".
     */
    private static CellType effectiveType(Cell cell) {
        if (cell == null) {
            return CellType.BLANK;
        }
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    private static String stringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        // formatCellValue KHÔNG kèm evaluator sẽ trả về chính chuỗi công thức → tự đọc kết quả đã tính sẵn.
        if (cell.getCellType() == CellType.FORMULA) {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getRichStringCellValue().getString().trim();
                case NUMERIC -> FORMATTER.formatRawCellContents(cell.getNumericCellValue(),
                        cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString()).trim();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    private static Double numericOrNull(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.STRING) {
            return parseNumber(stringValue(cell));
        }
        return null;
    }

    /**
     * Đọc số từ chuỗi, chấp nhận cách viết Việt Nam (2.818.181,82) lẫn kiểu Anh (2,818,181.82)
     * và khoảng trắng không ngắt mà Excel hay chèn khi dán số.
     */
    public static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace("\u00A0", "").replaceAll("\\s", "");
        if (s.isEmpty()) {
            return null;
        }
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            // dấu xuất hiện SAU cùng là dấu thập phân, dấu còn lại là phân cách nghìn
            s = lastComma > lastDot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (lastComma >= 0) {
            s = s.indexOf(',') == lastComma ? s.replace(',', '.') : s.replace(",", "");
        } else if (lastDot >= 0 && s.indexOf('.') != lastDot) {
            s = s.replace(".", ""); // nhiều dấu chấm → đều là phân cách nghìn
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate dateOrNull(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (type == CellType.STRING) {
            String raw = stringValue(cell);
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
