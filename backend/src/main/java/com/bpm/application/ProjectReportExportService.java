package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Xuất báo cáo ngày/tuần của dự án ra Excel (.xlsx) và Word (.docx) bằng Apache POI. */
@Service
public class ProjectReportExportService {

    private record Section(String title, List<ProjectDto.ReportTaskItem> rows) {}

    private static final String[] COLS =
            {"Mã", "Loại", "Công việc", "Thuộc (Epic/Story)", "Trạng thái", "Người thực hiện", "Hạn", "% HT"};

    private List<Section> sections(ProjectDto.PeriodReportResponse r) {
        return List.of(
                new Section("Đã hoàn thành", r.done()),
                new Section("Trễ hạn", r.overdue()),
                new Section("Đang làm", r.inProgress()),
                new Section("Sắp làm", r.upcoming()));
    }

    // ===================== EXCEL =====================
    public byte[] toXlsx(ProjectDto.PeriodReportResponse r, String projectName) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            ProjectDto.ReportOverview ov = r.overview();

            Sheet s0 = wb.createSheet("Tổng quan");
            int i = 0;
            kv(s0, i++, "Dự án", projectName);
            kv(s0, i++, "Kỳ báo cáo", r.periodLabel());
            kv(s0, i++, "Tiến độ hoàn thành", Math.round(ov.completionPct()) + "%");
            kv(s0, i++, "Tổng task", String.valueOf(ov.totalTasks()));
            kv(s0, i++, "Đã xong", String.valueOf(ov.doneTasks()));
            kv(s0, i++, "Quá hạn", String.valueOf(ov.overdueCount()));
            kv(s0, i++, "Bug", String.valueOf(ov.bugCount()));
            s0.setColumnWidth(0, 6000);
            s0.setColumnWidth(1, 14000);

            for (Section sec : sections(r)) {
                Sheet sh = wb.createSheet(safeSheet(sec.title() + " (" + sec.rows().size() + ")"));
                Row h = sh.createRow(0);
                for (int c = 0; c < COLS.length; c++) {
                    Cell cell = h.createCell(c);
                    cell.setCellValue(COLS[c]);
                    cell.setCellStyle(header);
                }
                int rr = 1;
                for (ProjectDto.ReportTaskItem t : sec.rows()) {
                    Row row = sh.createRow(rr++);
                    row.createCell(0).setCellValue(nz(t.code()));
                    row.createCell(1).setCellValue(typeLabel(t.type()));
                    row.createCell(2).setCellValue(nz(t.title()));
                    row.createCell(3).setCellValue(nz(t.parentPath()));
                    row.createCell(4).setCellValue(statusLabel(t.status()));
                    row.createCell(5).setCellValue(nz(t.assigneeName()));
                    row.createCell(6).setCellValue(nz(t.dueDate()));
                    row.createCell(7).setCellValue(Math.round(t.progressPct()) + "%");
                }
                int[] w = {3000, 3000, 16000, 14000, 3600, 5000, 3000, 2600};
                for (int c = 0; c < COLS.length; c++) sh.setColumnWidth(c, w[c]);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Excel báo cáo", e);
        }
    }

    // ===================== WORD =====================
    public byte[] toDocx(ProjectDto.PeriodReportResponse r, String projectName) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ProjectDto.ReportOverview ov = r.overview();
            title(doc, "BÁO CÁO DỰ ÁN — " + projectName);
            para(doc, "Kỳ báo cáo: " + r.periodLabel(), true);
            para(doc, "Tiến độ hoàn thành: " + Math.round(ov.completionPct()) + "%   |   Tổng task: " + ov.totalTasks()
                    + "   |   Đã xong: " + ov.doneTasks() + "   |   Quá hạn: " + ov.overdueCount()
                    + "   |   Bug: " + ov.bugCount(), false);

            for (Section sec : sections(r)) {
                heading(doc, sec.title() + " (" + sec.rows().size() + ")");
                if (sec.rows().isEmpty()) {
                    para(doc, "— Không có —", false);
                    continue;
                }
                XWPFTable tbl = doc.createTable();
                fillRow(tbl.getRow(0), true, "Mã", "Loại", "Công việc", "Trạng thái", "Người", "Hạn", "% HT");
                for (ProjectDto.ReportTaskItem t : sec.rows()) {
                    String titleCell = nz(t.title()) + (t.parentPath() != null ? "  [" + t.parentPath() + "]" : "");
                    fillRow(tbl.createRow(), false, nz(t.code()), typeLabel(t.type()), titleCell,
                            statusLabel(t.status()), nz(t.assigneeName()), nz(t.dueDate()),
                            Math.round(t.progressPct()) + "%");
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Word báo cáo", e);
        }
    }

    // ===================== Helpers XLSX =====================
    private static CellStyle headerStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        return st;
    }
    private static void kv(Sheet sh, int r, String k, String v) {
        Row row = sh.createRow(r);
        row.createCell(0).setCellValue(k);
        row.createCell(1).setCellValue(v == null ? "" : v);
    }
    /** Tên sheet hợp lệ Excel: bỏ ký tự cấm, ≤ 31 ký tự. */
    private static String safeSheet(String s) {
        String x = s.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return x.length() > 31 ? x.substring(0, 31) : (x.isEmpty() ? "Sheet" : x);
    }

    // ===================== Helpers DOCX =====================
    private static void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText(text);
    }
    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(13);
        run.setText(text);
    }
    private static void para(XWPFDocument doc, String text, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setText(text);
    }
    /** Điền cả hàng bảng; hàng đầu (header) in đậm. Thêm ô nếu thiếu để khớp số cột. */
    private static void fillRow(XWPFTableRow row, boolean bold, String... vals) {
        for (int i = 0; i < vals.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            if (cell == null) cell = row.addNewTableCell();
            cell.removeParagraph(0);
            XWPFParagraph p = cell.addParagraph();
            XWPFRun run = p.createRun();
            run.setBold(bold);
            run.setText(vals[i] == null ? "" : vals[i]);
        }
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
