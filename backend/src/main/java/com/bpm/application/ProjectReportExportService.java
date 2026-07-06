package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Xuất báo cáo ngày/tuần ra Excel (.xlsx) và Word (.docx) — CÙNG nội dung, format đẹp để gửi khách hàng.
 * Bố cục chung: Tiêu đề · Kỳ báo cáo · Tổng quan (chỉ số) · 4 khối (Đã hoàn thành / Trễ hạn / Đang làm / Sắp làm)
 * với bảng cùng cột: Mã · Loại · Công việc · Thuộc (Epic/Story) · Trạng thái · Người thực hiện · Hạn · % HT.
 */
@Service
public class ProjectReportExportService {

    private static final byte[] BRAND = {(byte) 0x1E, (byte) 0x50, (byte) 0xA0}; // xanh chủ đạo
    private static final byte[] HEADER_BG = {(byte) 0xDC, (byte) 0xE6, (byte) 0xF7}; // xanh nhạt
    private static final byte[] LABEL_BG = {(byte) 0xF0, (byte) 0xF3, (byte) 0xF7}; // xám nhạt
    private static final byte[] WHITE = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final String BRAND_HEX = "1E50A0";
    private static final String HEADER_HEX = "DCE6F7";
    private static final String LABEL_HEX = "F0F3F7";

    private static final String[] COLS =
            {"Mã", "Loại", "Công việc", "Thuộc (Epic/Story)", "Trạng thái", "Người thực hiện", "Hạn", "% HT"};

    private record Section(String title, List<ProjectDto.ReportTaskItem> rows) {}

    private List<Section> sections(ProjectDto.PeriodReportResponse r) {
        return List.of(
                new Section("Đã hoàn thành", r.done()),
                new Section("Trễ hạn", r.overdue()),
                new Section("Đang làm", r.inProgress()),
                new Section("Sắp làm", r.upcoming()));
    }

    /** Một dòng dữ liệu công việc — DÙNG CHUNG cho cả Excel lẫn Word (đảm bảo giống nhau). */
    private String[] rowOf(ProjectDto.ReportTaskItem t) {
        return new String[]{
                nz(t.code()), typeLabel(t.type()), nz(t.title()), nz(t.parentPath()),
                statusLabel(t.status()), nz(t.assigneeName()), nz(t.dueDate()), Math.round(t.progressPct()) + "%"};
    }

    /** Chỉ số tổng quan — dùng chung. */
    private List<String[]> overviewRows(ProjectDto.ReportOverview ov) {
        return List.of(
                new String[]{"Tiến độ hoàn thành", Math.round(ov.completionPct()) + "%"},
                new String[]{"Tổng công việc", String.valueOf(ov.totalTasks())},
                new String[]{"Đã hoàn thành", String.valueOf(ov.doneTasks())},
                new String[]{"Quá hạn", String.valueOf(ov.overdueCount())},
                new String[]{"Lỗi (bug)", String.valueOf(ov.bugCount())},
                new String[]{"Ước lượng (đã xong / tổng)", ov.doneEstimate() + " / " + ov.totalEstimate() + " h"});
    }

    // ===================== EXCEL =====================
    public byte[] toXlsx(ProjectDto.PeriodReportResponse r, String projectName) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFCellStyle title = style(wb, true, 16, WHITE, BRAND, false, HorizontalAlignment.CENTER);
            XSSFCellStyle subtitle = style(wb, false, 11, null, null, false, HorizontalAlignment.CENTER);
            XSSFCellStyle section = style(wb, true, 12, WHITE, BRAND, false, HorizontalAlignment.LEFT);
            XSSFCellStyle header = style(wb, true, 10, null, HEADER_BG, true, HorizontalAlignment.CENTER);
            XSSFCellStyle cell = style(wb, false, 10, null, null, true, HorizontalAlignment.LEFT);
            XSSFCellStyle center = style(wb, false, 10, null, null, true, HorizontalAlignment.CENTER);
            XSSFCellStyle ovLabel = style(wb, true, 10, null, LABEL_BG, true, HorizontalAlignment.LEFT);
            XSSFCellStyle ovValue = style(wb, false, 10, null, null, true, HorizontalAlignment.LEFT);

            XSSFSheet sh = wb.createSheet("Báo cáo");
            int last = COLS.length - 1;
            int rr = 0;
            merged(sh, rr, last, "BÁO CÁO DỰ ÁN", title, 28); rr++;
            merged(sh, rr, last, projectName, subtitle, 18); rr++;
            merged(sh, rr, last, "Kỳ báo cáo: " + r.periodLabel(), subtitle, 16); rr++;
            rr++; // dòng trống

            merged(sh, rr, last, "TỔNG QUAN", section, 20); rr++;
            for (String[] kv : overviewRows(r.overview())) {
                Row row = sh.createRow(rr++);
                put(row, 0, kv[0], ovLabel);
                Cell v = put(row, 1, kv[1], ovValue);
                sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, last));
            }
            rr++;

            for (Section sec : sections(r)) {
                merged(sh, rr, last, sec.title() + " — " + sec.rows().size() + " mục", section, 20); rr++;
                Row h = sh.createRow(rr++);
                for (int c = 0; c < COLS.length; c++) put(h, c, COLS[c], header);
                if (sec.rows().isEmpty()) {
                    Row row = sh.createRow(rr++);
                    put(row, 0, "— Không có —", cell);
                    sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, last));
                } else {
                    for (ProjectDto.ReportTaskItem t : sec.rows()) {
                        Row row = sh.createRow(rr++);
                        String[] vals = rowOf(t);
                        for (int c = 0; c < vals.length; c++) put(row, c, vals[c], (c == 1 || c == 4 || c == 7) ? center : cell);
                    }
                }
                rr++; // trống giữa các khối
            }

            int[] w = {3200, 3200, 15000, 13000, 3800, 6000, 3200, 2600};
            for (int c = 0; c < COLS.length; c++) sh.setColumnWidth(c, w[c]);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Excel báo cáo", e);
        }
    }

    // ===================== WORD =====================
    public byte[] toDocx(ProjectDto.PeriodReportResponse r, String projectName) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            landscape(doc);
            centerRun(doc, "BÁO CÁO DỰ ÁN", true, 18, BRAND_HEX);
            centerRun(doc, projectName, true, 13, "222222");
            centerRun(doc, "Kỳ báo cáo: " + r.periodLabel(), false, 11, "666666");
            blank(doc);

            heading(doc, "TỔNG QUAN");
            XWPFTable ovt = doc.createTable();
            ovt.setWidth("100%");
            boolean first = true;
            for (String[] kv : overviewRows(r.overview())) {
                XWPFTableRow row = first ? ovt.getRow(0) : ovt.createRow();
                first = false;
                cell(row, 0, kv[0], true, LABEL_HEX, 30);
                cell(row, 1, kv[1], false, null, 70);
            }
            blank(doc);

            for (Section sec : sections(r)) {
                heading(doc, sec.title() + " — " + sec.rows().size() + " mục");
                XWPFTable tbl = doc.createTable();
                tbl.setWidth("100%");
                XWPFTableRow hr = tbl.getRow(0);
                for (int c = 0; c < COLS.length; c++) cell(hr, c, COLS[c], true, BRAND_HEX, 0);
                if (sec.rows().isEmpty()) {
                    XWPFTableRow row = tbl.createRow();
                    cell(row, 0, "— Không có —", false, null, 0);
                } else {
                    for (ProjectDto.ReportTaskItem t : sec.rows()) {
                        XWPFTableRow row = tbl.createRow();
                        String[] vals = rowOf(t);
                        for (int c = 0; c < vals.length; c++) cell(row, c, vals[c], false, null, 0);
                    }
                }
                blank(doc);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Word báo cáo", e);
        }
    }

    // ===================== Helpers XLSX =====================
    private static XSSFCellStyle style(XSSFWorkbook wb, boolean bold, int size, byte[] fontColor, byte[] fill,
                                       boolean border, HorizontalAlignment align) {
        XSSFCellStyle st = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(bold);
        f.setFontHeightInPoints((short) size);
        if (fontColor != null) f.setColor(new XSSFColor(fontColor, null));
        st.setFont(f);
        if (fill != null) {
            st.setFillForegroundColor(new XSSFColor(fill, null));
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (border) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
        st.setAlignment(align);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setWrapText(true);
        return st;
    }
    private static Cell put(Row row, int c, String v, XSSFCellStyle st) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        cell.setCellStyle(st);
        return cell;
    }
    private static void merged(XSSFSheet sh, int r, int lastCol, String text, XSSFCellStyle st, int heightPt) {
        Row row = sh.createRow(r);
        row.setHeightInPoints(heightPt);
        put(row, 0, text, st);
        for (int c = 1; c <= lastCol; c++) put(row, c, "", st);
        sh.addMergedRegion(new CellRangeAddress(r, r, 0, lastCol));
    }

    // ===================== Helpers DOCX =====================
    private static void landscape(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageSz pageSz = sectPr.addNewPgSz();
        pageSz.setOrient(STPageOrientation.LANDSCAPE);
        pageSz.setW(BigInteger.valueOf(16840));
        pageSz.setH(BigInteger.valueOf(11900));
    }
    private static void centerRun(XWPFDocument doc, String text, boolean bold, int size, String colorHex) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(size);
        run.setColor(colorHex);
        run.setText(text);
    }
    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(180);
        p.setSpacingAfter(60);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(13);
        run.setColor(BRAND_HEX);
        run.setText(text);
    }
    private static void blank(XWPFDocument doc) {
        doc.createParagraph();
    }
    /** Điền 1 ô bảng: bold + màu nền (shadeHex) + độ rộng % (0 = bỏ qua). Header dùng nền xanh chữ trắng. */
    private static void cell(XWPFTableRow row, int idx, String text, boolean bold, String shadeHex, int widthPct) {
        XWPFTableCell c = row.getCell(idx);
        if (c == null) c = row.addNewTableCell();
        if (shadeHex != null) c.setColor(shadeHex);
        if (widthPct > 0) c.setWidth(widthPct + "%");
        c.removeParagraph(0);
        XWPFParagraph p = c.addParagraph();
        p.setSpacingAfter(0);
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(9);
        if (BRAND_HEX.equals(shadeHex)) run.setColor("FFFFFF"); // header nền xanh → chữ trắng
        run.setText(text == null ? "" : text);
    }

    // ===================== Nhãn =====================
    private static String nz(String s) { return s == null ? "" : s; }
    private static String typeLabel(String t) {
        if (t == null) return "";
        switch (t) {
            case "EPIC": return "Epic";
            case "STORY": return "Story";
            case "TASK": return "Task";
            case "SUBTASK": return "Sub-task";
            case "BUG": return "Bug";
            case "ISSUE": return "Issue";
            default: return t;
        }
    }
    private static String statusLabel(String s) {
        if (s == null) return "";
        switch (s) {
            case "BACKLOG": return "Backlog";
            case "TODO": return "Cần làm";
            case "IN_PROGRESS": return "Đang làm";
            case "IN_REVIEW": return "Kiểm thử";
            case "DONE": return "Hoàn thành";
            default: return s;
        }
    }
}
