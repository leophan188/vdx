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
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
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
            {"STT", "Tên công việc", "PIC", "Trạng thái", "Bắt đầu", "Kết thúc", "%"};

    // Màu khối/thẻ theo trạng thái (RGB).
    private static final byte[] GREEN = {(byte) 0x2E, (byte) 0xA0, (byte) 0x5A};
    private static final byte[] BLUE = {(byte) 0x1E, (byte) 0x50, (byte) 0xA0};
    private static final byte[] RED = {(byte) 0xC0, (byte) 0x39, (byte) 0x2B};
    private static final byte[] GREEN_BG = {(byte) 0xE6, (byte) 0xF4, (byte) 0xEA};
    private static final byte[] BLUE_BG = {(byte) 0xE3, (byte) 0xEC, (byte) 0xF9};
    private static final byte[] RED_BG = {(byte) 0xFB, (byte) 0xE9, (byte) 0xE7};

    private record Section(String title, List<ProjectDto.ReportTaskItem> rows, byte[] color) {}

    private List<Section> sections(ProjectDto.PeriodReportResponse r) {
        return List.of(
                new Section("✅ ĐÃ HOÀN THÀNH", r.done(), GREEN),
                new Section("🔄 ĐANG LÀM", r.inProgress(), BLUE),
                new Section("🗒️ SẮP LÀM", r.upcoming(), BRAND));
    }

    /** Cấp lồng của công việc (theo parentPath "Epic › Story › …") để thụt lề tên. */
    private int level(ProjectDto.ReportTaskItem t) {
        if (t.parentPath() == null || t.parentPath().isBlank()) {
            return 0;
        }
        return (int) t.parentPath().chars().filter(c -> c == '›').count() + 1;
    }

    /** Một dòng dữ liệu công việc (STT · Tên thụt lề · PIC · Trạng thái · Bắt đầu · Kết thúc · %). */
    private String[] rowOf(ProjectDto.ReportTaskItem t, int stt) {
        int lvl = level(t);
        String name = "    ".repeat(lvl) + (lvl > 0 ? "└ " : "") + nz(t.title());
        return new String[]{
                String.valueOf(stt), name, nz(t.assigneeName()), statusLabel(t.status()),
                nz(t.startDate()), nz(t.dueDate()), Math.round(t.progressPct()) + "%"};
    }

    /** Thanh tiến trình bằng ký tự cho Word: "██████░░░░ 60%". */
    private static String bar(double pct) {
        int p = (int) Math.round(Math.max(0, Math.min(100, pct)));
        int filled = Math.round(p / 10f);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? '█' : '░');
        return sb.append(' ').append(p).append('%').toString();
    }

    /** Đếm công việc theo TỪNG trạng thái (gộp cả 4 khối, khử trùng theo taskId) — giữ thứ tự đủ trạng thái. */
    private java.util.LinkedHashMap<String, Integer> statusCounts(ProjectDto.PeriodReportResponse r) {
        java.util.LinkedHashMap<String, Integer> cnt = new java.util.LinkedHashMap<>();
        for (String s : new String[]{"BACKLOG", "TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED"}) {
            cnt.put(s, 0);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<ProjectDto.ReportTaskItem> list : List.of(r.done(), r.overdue(), r.inProgress(), r.upcoming())) {
            for (ProjectDto.ReportTaskItem t : list) {
                if (seen.add(t.taskId()) && cnt.containsKey(t.status())) {
                    cnt.merge(t.status(), 1, Integer::sum);
                }
            }
        }
        return cnt;
    }

    /** Bảng text label|count cho Word. */
    private List<String[]> statusSummaryRows(ProjectDto.PeriodReportResponse r) {
        List<String[]> rows = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : statusCounts(r).entrySet()) {
            rows.add(new String[]{statusLabel(e.getKey()), String.valueOf(e.getValue())});
        }
        return rows;
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
            // Cột % HT: số + hậu tố "%" → nền cho DATA BAR (thanh tiến trình) hiển thị đẹp.
            XSSFCellStyle pct = style(wb, false, 10, null, null, true, HorizontalAlignment.CENTER);
            pct.setDataFormat(wb.createDataFormat().getFormat("0\"%\""));
            java.util.Map<String, XSSFCellStyle> sst = statusStyles(wb); // ô trạng thái tô màu

            XSSFSheet sh = wb.createSheet("Báo cáo");
            int last = COLS.length - 1;
            int rr = 0;
            merged(sh, rr, last, "BÁO CÁO — " + r.periodLabel(), title, 28); rr++;
            merged(sh, rr, last, projectName, subtitle, 18); rr++;
            rr++; // dòng trống

            // ===== 3 THẺ SỐ LIỆU: Hoàn thành (xanh) · Đang làm (xanh dương) · Trễ hạn (đỏ) =====
            int[][] cardCols = {{0, 1}, {2, 4}, {5, 6}};
            String[] cardLabel = {"✅ HOÀN THÀNH", "🔄 ĐANG LÀM", "⛔ TRỄ HẠN"};
            int[] cardVal = {r.done().size(), r.inProgress().size(), r.overdue().size()};
            byte[][] cardColor = {GREEN, BLUE, RED};
            byte[][] cardBg = {GREEN_BG, BLUE_BG, RED_BG};
            Row cardHead = sh.createRow(rr);
            Row cardNum = sh.createRow(rr + 1);
            cardNum.setHeightInPoints(34);
            for (int i = 0; i < 3; i++) {
                put(cardHead, cardCols[i][0], cardLabel[i], style(wb, true, 11, WHITE, cardColor[i], false, HorizontalAlignment.CENTER));
                put(cardNum, cardCols[i][0], String.valueOf(cardVal[i]), style(wb, true, 24, cardColor[i], cardBg[i], false, HorizontalAlignment.CENTER));
                sh.addMergedRegion(new CellRangeAddress(rr, rr, cardCols[i][0], cardCols[i][1]));
                sh.addMergedRegion(new CellRangeAddress(rr + 1, rr + 1, cardCols[i][0], cardCols[i][1]));
            }
            rr += 3;

            merged(sh, rr, last, "TỔNG QUAN", section, 20); rr++;
            for (String[] kv : overviewRows(r.overview())) {
                Row row = sh.createRow(rr++);
                put(row, 0, kv[0], ovLabel);
                Cell v = put(row, 1, kv[1], ovValue);
                sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, last));
            }
            rr++;

            // Thống kê theo TỪNG trạng thái (đủ trạng thái) + BIỂU ĐỒ DONUT bên phải.
            merged(sh, rr, last, "THỐNG KÊ THEO TRẠNG THÁI", section, 20); rr++;
            java.util.LinkedHashMap<String, Integer> scnt = statusCounts(r);
            int stRow0 = rr;
            for (java.util.Map.Entry<String, Integer> e : scnt.entrySet()) {
                Row row = sh.createRow(rr++);
                put(row, 0, statusLabel(e.getKey()), ovLabel);      // A: nhãn
                putNum(row, 1, e.getValue(), center);               // B: số (numeric → nguồn cho chart)
            }
            int stRow1 = rr - 1;
            try {
                XSSFDrawing dr = sh.createDrawingPatriarch();
                // Neo biểu đồ phủ cột C..H, cao ~ số dòng trạng thái + đệm.
                XSSFClientAnchor anchor = dr.createAnchor(0, 0, 0, 0, 2, stRow0, last + 1, stRow0 + 12);
                XSSFChart chart = dr.createChart(anchor);
                chart.setTitleText("Phân bố theo trạng thái");
                chart.setTitleOverlay(false);
                org.apache.poi.xddf.usermodel.chart.XDDFChartLegend legend = chart.getOrAddLegend();
                legend.setPosition(org.apache.poi.xddf.usermodel.chart.LegendPosition.RIGHT);
                var cats = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory
                        .fromStringCellRange(sh, new CellRangeAddress(stRow0, stRow1, 0, 0));
                var vals = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory
                        .fromNumericCellRange(sh, new CellRangeAddress(stRow0, stRow1, 1, 1));
                var cdata = chart.createData(org.apache.poi.xddf.usermodel.chart.ChartTypes.DOUGHNUT, null, null);
                cdata.setVaryColors(true);
                cdata.addSeries(cats, vals);
                chart.plot(cdata);
            } catch (Exception ignore) { /* bản POI không dựng được chart → vẫn có bảng số */ }
            rr = Math.max(rr, stRow0 + 13); // chừa chỗ cho biểu đồ
            rr++;

            // Tiến độ EPIC/Story (chỉ báo cáo tuần — có tổng quan dự án).
            if (r.epicStory() != null && !r.epicStory().isEmpty()) {
                merged(sh, rr, last, "TIẾN ĐỘ EPIC / STORY — " + r.epicStory().size() + " mục", section, 20); rr++;
                Row h = sh.createRow(rr++);
                for (int c = 0; c < COLS.length; c++) put(h, c, COLS[c], header);
                int estt = 1;
                for (ProjectDto.ReportTaskItem t : r.epicStory()) {
                    writeTaskRow(sh.createRow(rr++), rowOf(t, estt++), t, cell, center, pct, sst);
                }
                rr++;
            }

            for (Section sec : sections(r)) {
                XSSFCellStyle secStyle = style(wb, true, 12, WHITE, sec.color(), false, HorizontalAlignment.LEFT);
                merged(sh, rr, last, sec.title() + " (" + sec.rows().size() + ")", secStyle, 20); rr++;
                Row h = sh.createRow(rr++);
                for (int c = 0; c < COLS.length; c++) put(h, c, COLS[c], header);
                if (sec.rows().isEmpty()) {
                    Row row = sh.createRow(rr++);
                    put(row, 0, "— Không có —", cell);
                    sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, last));
                } else {
                    int stt = 1;
                    for (ProjectDto.ReportTaskItem t : sec.rows()) {
                        writeTaskRow(sh.createRow(rr++), rowOf(t, stt++), t, cell, center, pct, sst);
                    }
                }
                rr++; // trống giữa các khối
            }

            // Độ rộng cột cân đối: STT · Tên(rộng vừa) · PIC · Trạng thái · Bắt đầu · Kết thúc · %
            int[] w = {1500, 12000, 5200, 4600, 3600, 3600, 6000};
            for (int c = 0; c < COLS.length; c++) sh.setColumnWidth(c, w[c]);

            // THANH TIẾN TRÌNH %: data bar xanh trên cột "% HT" (G) — chỉ áp lên ô SỐ (bỏ qua header/section).
            try {
                var scf = sh.getSheetConditionalFormatting();
                XSSFColor barColor = new XSSFColor(new byte[]{(byte) 0x63, (byte) 0xC3, (byte) 0x84}, null);
                var rule = scf.createConditionalFormattingRule(barColor);
                var dbf = rule.getDataBarFormatting();
                dbf.getMinThreshold().setRangeType(org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType.NUMBER);
                dbf.getMinThreshold().setValue(0d);
                dbf.getMaxThreshold().setRangeType(org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType.NUMBER);
                dbf.getMaxThreshold().setValue(100d);
                dbf.setWidthMax(90);
                CellRangeAddress[] regions = {CellRangeAddress.valueOf("G1:G" + Math.max(1, rr))};
                scf.addConditionalFormatting(regions, rule);
            } catch (Exception ignore) { /* bản POI không hỗ trợ data bar → bỏ qua, vẫn có số % */ }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Excel báo cáo", e);
        }
    }

    private static Cell putNum(Row row, int col, double v, XSSFCellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(style);
        return c;
    }

    /** Ghi 1 dòng công việc (STT · Tên · PIC · Trạng thái tô màu · Bắt đầu · Kết thúc · % số→data bar). */
    private void writeTaskRow(Row row, String[] vals, ProjectDto.ReportTaskItem t,
                              XSSFCellStyle cell, XSSFCellStyle center, XSSFCellStyle pct,
                              java.util.Map<String, XSSFCellStyle> sst) {
        put(row, 0, vals[0], center);   // STT
        put(row, 1, vals[1], cell);     // Tên (thụt lề)
        put(row, 2, vals[2], cell);     // PIC
        put(row, 3, vals[3], sst.getOrDefault(t.status(), center)); // Trạng thái (màu)
        put(row, 4, vals[4], center);   // Bắt đầu
        put(row, 5, vals[5], center);   // Kết thúc
        putNum(row, 6, t.progressPct(), pct); // %
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

            heading(doc, "THỐNG KÊ THEO TRẠNG THÁI");
            XWPFTable stt = doc.createTable();
            stt.setWidth("100%");
            boolean firstSt = true;
            for (String[] kv : statusSummaryRows(r)) {
                XWPFTableRow row = firstSt ? stt.getRow(0) : stt.createRow();
                firstSt = false;
                cell(row, 0, kv[0], true, LABEL_HEX, 30);
                cell(row, 1, kv[1], false, null, 70);
            }
            blank(doc);

            if (r.epicStory() != null && !r.epicStory().isEmpty()) {
                heading(doc, "TIẾN ĐỘ EPIC / STORY — " + r.epicStory().size() + " mục");
                XWPFTable et = doc.createTable();
                et.setWidth("100%");
                XWPFTableRow ehr = et.getRow(0);
                for (int c = 0; c < COLS.length; c++) cell(ehr, c, COLS[c], true, BRAND_HEX, 0);
                int estt = 1;
                for (ProjectDto.ReportTaskItem t : r.epicStory()) {
                    XWPFTableRow row = et.createRow();
                    String[] vals = rowOf(t, estt++);
                    for (int c = 0; c < vals.length; c++) cell(row, c, vals[c], false, null, 0);
                }
                blank(doc);
            }

            for (Section sec : sections(r)) {
                heading(doc, sec.title() + " (" + sec.rows().size() + ")");
                XWPFTable tbl = doc.createTable();
                tbl.setWidth("100%");
                XWPFTableRow hr = tbl.getRow(0);
                for (int c = 0; c < COLS.length; c++) cell(hr, c, COLS[c], true, BRAND_HEX, 0);
                if (sec.rows().isEmpty()) {
                    XWPFTableRow row = tbl.createRow();
                    cell(row, 0, "— Không có —", false, null, 0);
                } else {
                    int nseq = 1;
                    for (ProjectDto.ReportTaskItem t : sec.rows()) {
                        XWPFTableRow row = tbl.createRow();
                        String[] vals = rowOf(t, nseq++);
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

    // ===================== BACKLOG (cây công việc) =====================
    private record Node(ProjectDto.TaskResponse t, int level) {}

    /** Sắp xếp task thành CÂY (cha → con), giữ thứ tự orderIndex/seq. */
    private static List<Node> tree(List<ProjectDto.TaskResponse> tasks) {
        java.util.Map<String, List<ProjectDto.TaskResponse>> byParent = new java.util.LinkedHashMap<>();
        for (ProjectDto.TaskResponse t : tasks) {
            byParent.computeIfAbsent(t.parentId() == null ? "" : t.parentId(), k -> new java.util.ArrayList<>()).add(t);
        }
        for (List<ProjectDto.TaskResponse> l : byParent.values()) {
            l.sort(java.util.Comparator.comparingInt(ProjectDto.TaskResponse::orderIndex)
                    .thenComparingInt(ProjectDto.TaskResponse::seq));
        }
        List<Node> out = new java.util.ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ProjectDto.TaskResponse t : tasks) ids.add(t.id());
        // gốc = không có cha HOẶC cha không nằm trong tập (mồ côi)
        List<ProjectDto.TaskResponse> roots = new java.util.ArrayList<>();
        for (ProjectDto.TaskResponse t : tasks) {
            if (t.parentId() == null || !ids.contains(t.parentId())) roots.add(t);
        }
        roots.sort(java.util.Comparator.comparingInt(ProjectDto.TaskResponse::orderIndex)
                .thenComparingInt(ProjectDto.TaskResponse::seq));
        java.util.Deque<Node> stack = new java.util.ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) stack.push(new Node(roots.get(i), 0));
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (!seen.add(n.t().id())) continue;
            out.add(n);
            List<ProjectDto.TaskResponse> kids = byParent.getOrDefault(n.t().id(), List.of());
            for (int i = kids.size() - 1; i >= 0; i--) stack.push(new Node(kids.get(i), n.level() + 1));
        }
        return out;
    }

    private static final String[] BL_COLS =
            {"Loại", "Công việc", "Trạng thái", "Người thực hiện", "Est (h)", "% HT", "Bắt đầu", "Kết thúc", "Ưu tiên"};

    /** Xuất BACKLOG (cây công việc) ra Excel định dạng đẹp. */
    public byte[] backlogXlsx(String projectName, List<ProjectDto.TaskResponse> tasks) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFCellStyle title = style(wb, true, 16, WHITE, BRAND, false, HorizontalAlignment.CENTER);
            XSSFCellStyle subtitle = style(wb, false, 11, null, null, false, HorizontalAlignment.CENTER);
            XSSFCellStyle header = style(wb, true, 10, null, HEADER_BG, true, HorizontalAlignment.CENTER);
            XSSFCellStyle cell = style(wb, false, 10, null, null, true, HorizontalAlignment.LEFT);
            XSSFCellStyle center = style(wb, false, 10, null, null, true, HorizontalAlignment.CENTER);
            XSSFCellStyle epic = style(wb, true, 10, null, LABEL_BG, true, HorizontalAlignment.LEFT);
            XSSFCellStyle epicC = style(wb, true, 10, null, LABEL_BG, true, HorizontalAlignment.CENTER);
            java.util.Map<String, XSSFCellStyle> sst = statusStyles(wb);

            XSSFSheet sh = wb.createSheet("Backlog");
            int last = BL_COLS.length - 1;
            int rr = 0;
            merged(sh, rr++, last, "DANH SÁCH CÔNG VIỆC (BACKLOG)", title, 28);
            merged(sh, rr++, last, projectName, subtitle, 18);
            List<Node> nodes = tree(tasks);
            long doneLeaf = tasks.stream().filter(t -> t.leaf() && "DONE".equals(t.status())).count();
            long leaf = tasks.stream().filter(ProjectDto.TaskResponse::leaf).count();
            double est = tasks.stream().filter(ProjectDto.TaskResponse::leaf).mapToDouble(ProjectDto.TaskResponse::estimateHours).sum();
            merged(sh, rr++, last, "Tổng: " + tasks.size() + " công việc · " + leaf + " task lá (" + doneLeaf + " đã xong) · Est "
                    + trimNum(est) + " giờ", subtitle, 16);
            rr++;

            Row h = sh.createRow(rr++);
            for (int c = 0; c < BL_COLS.length; c++) put(h, c, BL_COLS[c], header);
            for (Node n : nodes) {
                ProjectDto.TaskResponse t = n.t();
                boolean grp = !t.leaf();
                Row row = sh.createRow(rr++);
                // Thò thụt cây: mỗi cấp thêm khoảng thụt + "› " cho nhóm để nhìn rõ cha–con như web.
                String indent = "    ".repeat(Math.min(n.level(), 6));
                String name = indent + (grp && n.level() > 0 ? "› " : "") + nz(t.title());
                put(row, 0, typeLabel(t.type()), grp ? epicC : center);
                put(row, 1, name, grp ? epic : cell);
                put(row, 2, statusVi(t.status()), sst.getOrDefault(t.status(), grp ? epicC : center));
                put(row, 3, nz(t.assigneeName()), grp ? epic : cell);
                put(row, 4, t.estimateHours() > 0 ? trimNum(t.estimateHours()) : "", grp ? epicC : center);
                put(row, 5, Math.round(t.progressPct()) + "%", grp ? epicC : center);
                put(row, 6, nz(t.startDate()), grp ? epicC : center);
                put(row, 7, nz(t.dueDate()), grp ? epicC : center);
                put(row, 8, priorityVi(t.priority()), grp ? epicC : center);
            }
            int[] w = {2600, 18000, 3800, 4800, 2200, 2200, 3000, 3000, 3000};
            for (int c = 0; c < BL_COLS.length; c++) sh.setColumnWidth(c, w[c]);
            sh.createFreezePane(0, 4);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Excel backlog", e);
        }
    }

    // ===================== TIMELINE (lịch trình + Gantt theo tuần) =====================
    private static final String[] TL_COLS =
            {"Công việc", "Người thực hiện", "Bắt đầu", "Kết thúc", "Số ngày", "Trạng thái", "% HT"};

    /** Xuất TIMELINE ra Excel: bảng lịch trình + biểu đồ Gantt theo tuần. */
    public byte[] timelineXlsx(String projectName, List<ProjectDto.TaskResponse> tasks) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFCellStyle title = style(wb, true, 16, WHITE, BRAND, false, HorizontalAlignment.CENTER);
            XSSFCellStyle subtitle = style(wb, false, 11, null, null, false, HorizontalAlignment.CENTER);
            XSSFCellStyle section = style(wb, true, 12, WHITE, BRAND, false, HorizontalAlignment.LEFT);
            XSSFCellStyle header = style(wb, true, 10, null, HEADER_BG, true, HorizontalAlignment.CENTER);
            XSSFCellStyle cell = style(wb, false, 10, null, null, true, HorizontalAlignment.LEFT);
            XSSFCellStyle center = style(wb, false, 10, null, null, true, HorizontalAlignment.CENTER);
            XSSFCellStyle bar = style(wb, false, 10, null, BRAND, true, HorizontalAlignment.CENTER);
            XSSFCellStyle barDone = style(wb, false, 10, null, new byte[]{(byte) 0x16, (byte) 0xA3, (byte) 0x4A}, true, HorizontalAlignment.CENTER);
            java.util.Map<String, XSSFCellStyle> sst = statusStyles(wb);

            // Chỉ task có lịch (bắt đầu + kết thúc), sắp theo ngày bắt đầu.
            List<ProjectDto.TaskResponse> sched = new java.util.ArrayList<>();
            for (ProjectDto.TaskResponse t : tasks) {
                if (parse(t.startDate()) != null && parse(t.dueDate()) != null) sched.add(t);
            }
            sched.sort(java.util.Comparator.comparing((ProjectDto.TaskResponse t) -> parse(t.startDate()))
                    .thenComparing(t -> parse(t.dueDate())));

            // ===== SHEET 1: Lịch trình (bảng) — cột cân đối, KHÔNG có Mã =====
            XSSFSheet sh = wb.createSheet("Lịch trình");
            int last = TL_COLS.length - 1;
            int rr = 0;
            merged(sh, rr++, last, "TIMELINE — LỊCH TRÌNH DỰ ÁN", title, 28);
            merged(sh, rr++, last, projectName, subtitle, 18);
            rr++;
            merged(sh, rr++, last, "LỊCH TRÌNH CÔNG VIỆC (" + sched.size() + " mục)", section, 20);
            Row h = sh.createRow(rr++);
            for (int c = 0; c < TL_COLS.length; c++) put(h, c, TL_COLS[c], header);
            for (ProjectDto.TaskResponse t : sched) {
                java.time.LocalDate s = parse(t.startDate()), d = parse(t.dueDate());
                long days = java.time.temporal.ChronoUnit.DAYS.between(s, d) + 1;
                Row row = sh.createRow(rr++);
                put(row, 0, nz(t.title()), cell);
                put(row, 1, nz(t.assigneeName()), cell);
                put(row, 2, nz(t.startDate()), center);
                put(row, 3, nz(t.dueDate()), center);
                put(row, 4, String.valueOf(days), center);
                put(row, 5, statusVi(t.status()), sst.getOrDefault(t.status(), center));
                put(row, 6, Math.round(t.progressPct()) + "%", center);
            }
            int[] w = {24000, 5200, 3400, 3400, 2600, 4000, 2600};
            for (int c = 0; c < TL_COLS.length; c++) sh.setColumnWidth(c, w[c]);
            sh.createFreezePane(0, 5);

            // ===== SHEET 2: Gantt (theo tuần) — tách riêng để không phá độ rộng bảng =====
            if (!sched.isEmpty()) {
                XSSFSheet gs = wb.createSheet("Gantt");
                java.time.LocalDate min = sched.stream().map(t -> parse(t.startDate())).min(java.time.LocalDate::compareTo).get();
                java.time.LocalDate max = sched.stream().map(t -> parse(t.dueDate())).max(java.time.LocalDate::compareTo).get();
                java.time.LocalDate w0 = min.with(java.time.DayOfWeek.MONDAY);
                int weeks = (int) (java.time.temporal.ChronoUnit.WEEKS.between(w0, max.with(java.time.DayOfWeek.MONDAY)) + 1);
                weeks = Math.min(weeks, 60);
                int gr = 0;
                merged(gs, gr++, weeks, "BIỂU ĐỒ GANTT (theo tuần)", title, 28);
                merged(gs, gr++, weeks, projectName, subtitle, 18);
                Row gh = gs.createRow(gr++);
                put(gh, 0, "Công việc", header);
                for (int wk = 0; wk < weeks; wk++) {
                    put(gh, wk + 1, w0.plusWeeks(wk).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), header);
                    gs.setColumnWidth(wk + 1, 1200);
                }
                for (ProjectDto.TaskResponse t : sched) {
                    if (!t.leaf()) continue; // Gantt chỉ vẽ task lá cho gọn
                    java.time.LocalDate s = parse(t.startDate()), d = parse(t.dueDate());
                    Row row = gs.createRow(gr++);
                    put(row, 0, nz(t.title()), cell);
                    boolean done = "DONE".equals(t.status());
                    for (int wk = 0; wk < weeks; wk++) {
                        java.time.LocalDate ws = w0.plusWeeks(wk), we = ws.plusDays(6);
                        boolean overlap = !s.isAfter(we) && !d.isBefore(ws);
                        put(row, wk + 1, "", overlap ? (done ? barDone : bar) : center);
                    }
                }
                gs.setColumnWidth(0, 26000);
                gs.createFreezePane(1, 3);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không xuất được Excel timeline", e);
        }
    }

    /** Style ô trạng thái theo màu (Hoàn thành xanh lá · Đang làm xanh dương · Kiểm thử cam · Huỷ đỏ). */
    private static java.util.Map<String, XSSFCellStyle> statusStyles(XSSFWorkbook wb) {
        java.util.Map<String, XSSFCellStyle> m = new java.util.HashMap<>();
        m.put("DONE", style(wb, true, 10, null, new byte[]{(byte) 0xD5, (byte) 0xF5, (byte) 0xE3}, true, HorizontalAlignment.CENTER));
        m.put("IN_PROGRESS", style(wb, true, 10, null, new byte[]{(byte) 0xD6, (byte) 0xEA, (byte) 0xF8}, true, HorizontalAlignment.CENTER));
        m.put("IN_REVIEW", style(wb, true, 10, null, new byte[]{(byte) 0xFD, (byte) 0xEB, (byte) 0xD0}, true, HorizontalAlignment.CENTER));
        m.put("CANCELLED", style(wb, false, 10, null, new byte[]{(byte) 0xFA, (byte) 0xDB, (byte) 0xD8}, true, HorizontalAlignment.CENTER));
        return m;
    }

    private static java.time.LocalDate parse(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(ddMMyyyy.trim(), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }
    private static String trimNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
    private static String statusVi(String s) {
        if (s == null) return "";
        switch (s) {
            case "BACKLOG": return "Backlog";
            case "TODO": return "Cần làm";
            case "IN_PROGRESS": return "Đang làm";
            case "IN_REVIEW": return "Kiểm thử";
            case "DONE": return "Hoàn thành";
            case "CANCELLED": return "Huỷ";
            default: return s;
        }
    }
    private static String priorityVi(String p) {
        if (p == null) return "";
        switch (p) {
            case "LOW": return "Thấp";
            case "MEDIUM": return "Trung bình";
            case "HIGH": return "Cao";
            case "URGENT": return "Khẩn cấp";
            default: return p;
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
            case "CANCELLED": return "Huỷ";
            default: return s;
        }
    }
}
