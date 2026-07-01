package com.bpm.infrastructure.hr;

import com.bpm.domain.report.SafeWorkbookReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc FILE nhân sự (.xlsx qua Apache POI hoặc .csv) → danh sách dòng đã ánh xạ theo TÊN cột tiếng Việt.
 *
 * <p>File thực tế có DÒNG BANNER ("DANH SÁCH NHÂN SỰ…") ở trên cùng → bỏ qua mọi dòng cho tới khi
 * gặp DÒNG HEADER (nhận diện: chứa cả "ID" và "Họ và tên"). Ánh xạ cột theo tên (linh hoạt vị trí),
 * không phụ thuộc thứ tự tuyệt đối.
 *
 * <p>An toàn file (NFR-09): .xlsx đi qua {@link SafeWorkbookReader} (giới hạn dung lượng/zip-bomb/loại file);
 * .csv giới hạn dung lượng + số dòng. Từ chối loại file khác.
 */
@Component
public class EmployeeFileReader {

    /** Giới hạn dung lượng cho .csv (10MB — đồng bộ với SafeWorkbookReader). */
    public static final long MAX_CSV_BYTES = 10L * 1024 * 1024;
    /** Giới hạn số dòng dữ liệu (chống OOM). */
    public static final int MAX_DATA_ROWS = SafeWorkbookReader.MAX_DATA_ROWS;

    /** Trường chuẩn → các biến thể tên cột tiếng Việt chấp nhận (so khớp đã chuẩn hoá). */
    private static final Map<String, List<String>> FIELD_ALIASES = new LinkedHashMap<>();

    static {
        FIELD_ALIASES.put("empCode", List.of("id", "ma nhan vien", "ma nv", "ma nhan su"));
        FIELD_ALIASES.put("status", List.of("trang thai"));
        FIELD_ALIASES.put("fullName", List.of("ho va ten", "ho ten", "ten nhan vien"));
        FIELD_ALIASES.put("jobPosition", List.of("vi tri cong viec", "vi tri"));
        FIELD_ALIASES.put("title", List.of("chuc danh"));
        FIELD_ALIASES.put("deptCode", List.of("ma bo phan", "bo phan"));
        FIELD_ALIASES.put("unit", List.of("don vi"));
        FIELD_ALIASES.put("joinDate", List.of("ngay tham gia", "ngay vao", "ngay vao lam"));
        FIELD_ALIASES.put("birthDate", List.of("ngay sinh"));
        FIELD_ALIASES.put("phone", List.of("so dien thoai", "dien thoai", "sdt"));
        FIELD_ALIASES.put("contractType", List.of("loai hop dong hien tai", "loai hop dong", "hop dong"));
        FIELD_ALIASES.put("bankAccount", List.of("so tai khoan", "stk"));
        FIELD_ALIASES.put("bankName", List.of("ngan hang"));
        FIELD_ALIASES.put("level", List.of("level", "cap bac"));
    }

    /** Một dòng dữ liệu đã ánh xạ. {@code values} theo khoá trường chuẩn; {@code rowNumber} = số dòng gốc (1-based). */
    public record EmployeeRow(int rowNumber, Map<String, String> values) {
        public String get(String field) {
            String v = values.get(field);
            return v == null ? null : v.trim();
        }
    }

    /** Kết quả đọc file: các dòng dữ liệu + tập trường đã tìm thấy header. */
    public record ParsedFile(List<EmployeeRow> rows, List<String> mappedFields) {
    }

    /**
     * Đọc file → các dòng đã ánh xạ.
     *
     * @throws SafeWorkbookReader.UnsafeFileException nếu file sai loại/không an toàn
     * @throws IllegalArgumentException nếu không tìm thấy dòng header (thiếu "ID"/"Họ và tên")
     */
    public ParsedFile read(byte[] bytes, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".csv")) {
            return readCsv(bytes);
        }
        if (lower.endsWith(".xlsx")) {
            return readXlsx(bytes, fileName);
        }
        throw new SafeWorkbookReader.UnsafeFileException("Chỉ chấp nhận file .xlsx hoặc .csv.");
    }

    // ===== XLSX =====

    private ParsedFile readXlsx(byte[] bytes, String fileName) {
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            Sheet sheet = wb.getSheetAt(0);
            int headerRowIdx = -1;
            Map<Integer, String> colToField = null;

            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int maxCol = row.getLastCellNum();
                for (int c = 0; c < maxCol; c++) {
                    Cell cell = row.getCell(c);
                    String v = readCellValue(cell, fmt); // đọc cả ô công thức (cached) để nhận diện tiêu đề đúng
                    cells.add(v == null ? "" : v);
                }
                Map<Integer, String> mapping = tryMapHeader(cells);
                if (mapping != null) {
                    headerRowIdx = row.getRowNum();
                    colToField = mapping;
                    break;
                }
            }
            if (colToField == null) {
                throw new IllegalArgumentException(
                        "Không tìm thấy dòng tiêu đề (cần có cột \"ID\" và \"Họ và tên\").");
            }

            List<EmployeeRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() <= headerRowIdx) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (Map.Entry<Integer, String> e : colToField.entrySet()) {
                    Cell cell = row.getCell(e.getKey());
                    String raw = normalize(e.getValue(), readCellValue(cell, fmt)); // giữ số 0 đầu mã NV / SĐT
                    if (raw != null && !raw.isBlank()) {
                        any = true;
                    }
                    values.put(e.getValue(), raw);
                }
                if (!any) {
                    continue; // bỏ qua dòng trống
                }
                rows.add(new EmployeeRow(row.getRowNum() + 1, values));
                if (rows.size() > MAX_DATA_ROWS) {
                    throw new SafeWorkbookReader.UnsafeFileException(
                            "File vượt giới hạn " + MAX_DATA_ROWS + " dòng dữ liệu.");
                }
            }
            return new ParsedFile(rows, List.copyOf(colToField.values()));
        } catch (IOException e) {
            throw new SafeWorkbookReader.UnsafeFileException("Không đọc được file .xlsx: " + e.getMessage());
        }
    }

    /**
     * Đọc giá trị ô — giữ ngày dạng dd/MM/yyyy, số nguyên không phần thập phân thừa (vd mã/STK/SĐT).
     * Với ô CÔNG THỨC (vd file Google Sheets xuất ra dùng IMPORTRANGE/__xludf.DUMMYFUNCTION) phải lấy
     * GIÁ TRỊ ĐÃ TÍNH (cached) chứ KHÔNG lấy chuỗi công thức — vì DataFormatter không-evaluator trả về công thức.
     */
    /** Giữ số 0 đầu: mã NV 1–3 chữ số → 4 chữ số; SĐT 9 chữ số → thêm "0" đầu. */
    private static String normalize(String field, String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if ("empCode".equals(field) && v.matches("\\d{1,3}")) {
            return String.format("%04d", Integer.parseInt(v));
        }
        if ("phone".equals(field) && v.matches("\\d{9}")) {
            return "0" + v;
        }
        return raw;
    }

    private static String readCellValue(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return null;
        }
        // Quy về kiểu kết quả thực tế: ô công thức → kiểu giá trị đã cache.
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            }
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue();   // chuỗi đã cache cho ô công thức
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.BLANK || type == CellType.ERROR) {
            return "";
        }
        return fmt.formatCellValue(cell);
    }

    // ===== CSV =====

    private ParsedFile readCsv(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new SafeWorkbookReader.UnsafeFileException("File rỗng.");
        }
        if (bytes.length > MAX_CSV_BYTES) {
            throw new SafeWorkbookReader.UnsafeFileException(
                    "File CSV vượt giới hạn " + (MAX_CSV_BYTES / 1024 / 1024) + "MB.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1); // bỏ BOM
        }
        List<List<String>> lines = parseCsv(text);

        int headerIdx = -1;
        Map<Integer, String> colToField = null;
        for (int i = 0; i < lines.size(); i++) {
            Map<Integer, String> mapping = tryMapHeader(lines.get(i));
            if (mapping != null) {
                headerIdx = i;
                colToField = mapping;
                break;
            }
        }
        if (colToField == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy dòng tiêu đề (cần có cột \"ID\" và \"Họ và tên\").");
        }

        List<EmployeeRow> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            Map<String, String> values = new LinkedHashMap<>();
            boolean any = false;
            for (Map.Entry<Integer, String> e : colToField.entrySet()) {
                String raw = normalize(e.getValue(), e.getKey() < cells.size() ? cells.get(e.getKey()) : null);
                if (raw != null && !raw.isBlank()) {
                    any = true;
                }
                values.put(e.getValue(), raw);
            }
            if (!any) {
                continue;
            }
            rows.add(new EmployeeRow(i + 1, values));
            if (rows.size() > MAX_DATA_ROWS) {
                throw new SafeWorkbookReader.UnsafeFileException(
                        "File vượt giới hạn " + MAX_DATA_ROWS + " dòng dữ liệu.");
            }
        }
        return new ParsedFile(rows, List.copyOf(colToField.values()));
    }

    /** Bộ đọc CSV tối giản hỗ trợ ô có dấu ngoặc kép, dấu phẩy lồng và "" thoát. */
    private static List<List<String>> parseCsv(String text) {
        List<List<String>> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cur.add(sb.toString());
                sb.setLength(0);
            } else if (ch == '\n') {
                cur.add(sb.toString());
                sb.setLength(0);
                out.add(cur);
                cur = new ArrayList<>();
            } else if (ch != '\r') {
                sb.append(ch);
            }
        }
        if (sb.length() > 0 || !cur.isEmpty()) {
            cur.add(sb.toString());
            out.add(cur);
        }
        return out;
    }

    // ===== ánh xạ header =====

    /**
     * Thử coi {@code cells} là dòng header. Trả về map cột→trường chuẩn nếu chứa CẢ "ID" và "Họ và tên";
     * null nếu không phải header (vd dòng banner).
     */
    private static Map<Integer, String> tryMapHeader(List<String> cells) {
        Map<Integer, String> mapping = new LinkedHashMap<>();
        for (int c = 0; c < cells.size(); c++) {
            String norm = normalize(cells.get(c));
            if (norm.isEmpty()) {
                continue;
            }
            String field = matchField(norm);
            if (field != null && !mapping.containsValue(field)) {
                mapping.put(c, field);
            }
        }
        if (mapping.containsValue("empCode") && mapping.containsValue("fullName")) {
            return mapping;
        }
        return null;
    }

    private static String matchField(String normalizedHeader) {
        for (Map.Entry<String, List<String>> e : FIELD_ALIASES.entrySet()) {
            for (String alias : e.getValue()) {
                if (normalizedHeader.equals(alias)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** Chuẩn hoá: thường hoá, bỏ dấu tiếng Việt, gộp khoảng trắng. */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd');
        return t.replaceAll("\\s+", " ").trim();
    }
}
