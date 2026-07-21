package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Xuất BIÊN BẢN HỌP (.docx) từ một bản ghi Nhật ký dự án — POI/XWPF thuần code, không cần file mẫu
 * (cùng lối với {@link ProjectReportExportService#toDocx}).
 *
 * <p>Bố cục theo chuẩn văn bản Việt Nam:
 * <ol>
 *   <li>Quốc hiệu – Tiêu ngữ + tiêu đề BIÊN BẢN HỌP</li>
 *   <li>Thành phần tham dự (team nội bộ + phía khách hàng)</li>
 *   <li>Thời gian tổ chức / Địa điểm</li>
 *   <li>Nội dung</li>
 *   <li>Kết luận</li>
 *   <li>Next action (bảng: nội dung – phụ trách – hạn – trạng thái)</li>
 * </ol>
 * Mục nào trống vẫn IN nhưng ghi "—" để biên bản luôn đủ khung, người họp điền tay được.
 */
@Service
public class MeetingMinutesExportService {

    private static final String BRAND_HEX = "1F4E79";
    private static final String LABEL_HEX = "EEF3F8";
    private static final String[] ACTION_COLS = { "STT", "Nội dung công việc", "Người phụ trách", "Hạn hoàn thành", "Trạng thái" };
    /** Bề rộng cột bảng next action (%), tổng = 100. */
    private static final int[] ACTION_W = { 6, 44, 20, 16, 14 };

    /**
     * @param e           bản ghi nhật ký (đã resolve tên nhân sự)
     * @param projectName tên hiển thị dự án, vd "[BPM] Hệ thống quản trị"
     * @param companyName tên đơn vị đứng biên bản (phía team)
     */
    public byte[] toDocx(ProjectDto.DiaryEntry e, String projectName, String companyName) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            header(doc, e, projectName);
            attendees(doc, e, companyName);
            timeAndPlace(doc, e);
            heading(doc, "III. NỘI DUNG");
            multiline(doc, e.content());
            heading(doc, "IV. KẾT LUẬN");
            multiline(doc, e.conclusion());
            nextActions(doc, e);
            signature(doc, e);
            doc.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Không xuất được biên bản họp", ex);
        }
    }

    /** Tên file gợi ý: bien-ban-hop-<ngày>.docx (ngày dd/MM/yyyy → dd-MM-yyyy). */
    public String fileName(ProjectDto.DiaryEntry e) {
        String d = e.workDate() == null ? "" : "-" + e.workDate().replace('/', '-');
        return "bien-ban-hop" + d + ".docx";
    }

    // ===== 1. Quốc hiệu + tiêu đề =====
    private static void header(XWPFDocument doc, ProjectDto.DiaryEntry e, String projectName) {
        center(doc, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", true, 12, "222222");
        center(doc, "Độc lập – Tự do – Hạnh phúc", true, 12, "222222");
        center(doc, "———————————", false, 11, "666666");
        blank(doc);
        center(doc, "BIÊN BẢN HỌP", true, 18, BRAND_HEX);
        if (notBlank(e.category())) {
            center(doc, "(" + e.category() + ")", false, 11, "666666");
        }
        if (notBlank(projectName)) {
            center(doc, "Dự án: " + projectName, true, 12, "222222");
        }
        blank(doc);
    }

    // ===== 2. Thành phần tham dự =====
    private static void attendees(XWPFDocument doc, ProjectDto.DiaryEntry e, String companyName) {
        heading(doc, "I. THÀNH PHẦN THAM DỰ");
        String team = e.teamNames() == null || e.teamNames().isEmpty() ? null : String.join(", ", e.teamNames());
        bullet(doc, notBlank(companyName) ? "Phía " + companyName + ":" : "Phía đơn vị thực hiện:", team);
        bullet(doc, "Phía khách hàng:", e.clientContacts());
    }

    // ===== 3. Thời gian / Địa điểm =====
    private static void timeAndPlace(XWPFDocument doc, ProjectDto.DiaryEntry e) {
        heading(doc, "II. THỜI GIAN TỔ CHỨC / ĐỊA ĐIỂM");
        bullet(doc, "Thời gian:", timeText(e));
        bullet(doc, "Địa điểm / hình thức:", e.location());
    }

    /** "Từ 14:00 đến 15:30, ngày 21/07/2026" — thiếu giờ thì chỉ ghi ngày. */
    private static String timeText(ProjectDto.DiaryEntry e) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(e.startTime()) && notBlank(e.endTime())) {
            sb.append("Từ ").append(e.startTime()).append(" đến ").append(e.endTime());
        } else if (notBlank(e.startTime())) {
            sb.append("Bắt đầu ").append(e.startTime());
        }
        if (notBlank(e.workDate())) {
            sb.append(sb.length() > 0 ? ", ngày " : "Ngày ").append(e.workDate());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ===== 6. Next action =====
    private static void nextActions(XWPFDocument doc, ProjectDto.DiaryEntry e) {
        List<ProjectDto.DiaryAction> actions = e.nextActions();
        heading(doc, "V. NEXT ACTION (việc cần làm tiếp)");
        XWPFTable tbl = doc.createTable();
        tbl.setWidth("100%");
        XWPFTableRow hr = tbl.getRow(0);
        for (int c = 0; c < ACTION_COLS.length; c++) {
            cell(hr, c, ACTION_COLS[c], true, BRAND_HEX, ACTION_W[c]);
        }
        if (actions == null || actions.isEmpty()) {
            XWPFTableRow row = tbl.createRow();
            cell(row, 0, "", false, null, 0);
            cell(row, 1, "— Không có —", false, null, 0);
            cell(row, 2, "", false, null, 0);
            cell(row, 3, "", false, null, 0);
            cell(row, 4, "", false, null, 0);
            return;
        }
        int i = 1;
        for (ProjectDto.DiaryAction a : actions) {
            XWPFTableRow row = tbl.createRow();
            cell(row, 0, String.valueOf(i++), false, null, 0);
            cell(row, 1, nz(a.content()), false, null, 0);
            cell(row, 2, nz(a.owner()), false, null, 0);
            cell(row, 3, nz(a.dueDate()), false, null, 0);
            cell(row, 4, statusLabel(a.status()), false, null, 0);
        }
    }

    static String statusLabel(String s) {
        if (s == null) {
            return "Mới";
        }
        switch (s.trim().toUpperCase()) {
            case "DOING": return "Đang làm";
            case "DONE": return "Hoàn thành";
            default: return "Mới";
        }
    }

    // ===== Ký =====
    private static void signature(XWPFDocument doc, ProjectDto.DiaryEntry e) {
        blank(doc);
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setBold(true);
        r.setText("NGƯỜI GHI BIÊN BẢN");
        XWPFParagraph p2 = doc.createParagraph();
        p2.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r2 = p2.createRun();
        r2.setFontSize(11);
        r2.setItalic(true);
        r2.setText("(Ký, ghi rõ họ tên)");
        r2.addBreak();
        r2.addBreak();
        r2.setText(nz(e.createdByName()));
    }

    // ===== Helpers DOCX =====
    private static void center(XWPFDocument doc, String text, boolean bold, int size, String colorHex) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0);
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(size);
        run.setColor(colorHex);
        run.setText(text);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        p.setSpacingAfter(60);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(13);
        run.setColor(BRAND_HEX);
        run.setText(text);
    }

    /** Dòng "• Nhãn: giá trị" — giá trị trống in "—" để biên bản vẫn đủ khung. */
    private static void bullet(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(360);
        p.setSpacingAfter(40);
        XWPFRun l = p.createRun();
        l.setFontSize(11);
        l.setBold(true);
        l.setText("• " + label + " ");
        XWPFRun v = p.createRun();
        v.setFontSize(11);
        v.setText(notBlank(value) ? value : "—");
    }

    /** Text nhiều dòng → mỗi dòng một paragraph (giữ xuống dòng người dùng gõ). Trống → "—". */
    private static void multiline(XWPFDocument doc, String text) {
        if (!notBlank(text)) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(360);
            XWPFRun r = p.createRun();
            r.setFontSize(11);
            r.setText("—");
            return;
        }
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(360);
            p.setSpacingAfter(40);
            XWPFRun r = p.createRun();
            r.setFontSize(11);
            r.setText(line);
        }
    }

    private static void blank(XWPFDocument doc) {
        doc.createParagraph();
    }

    /** Điền 1 ô bảng: bold + nền (shadeHex) + rộng % (0 = bỏ qua). Header nền xanh → chữ trắng. */
    private static void cell(XWPFTableRow row, int idx, String text, boolean bold, String shadeHex, int widthPct) {
        XWPFTableCell c = row.getCell(idx);
        if (c == null) {
            c = row.addNewTableCell();
        }
        if (shadeHex != null) {
            c.setColor(shadeHex);
        }
        if (widthPct > 0) {
            c.setWidth(widthPct + "%");
        }
        c.removeParagraph(0);
        XWPFParagraph p = c.addParagraph();
        p.setSpacingAfter(0);
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(10);
        if (BRAND_HEX.equals(shadeHex)) {
            run.setColor("FFFFFF");
        } else if (LABEL_HEX.equals(shadeHex)) {
            run.setColor("222222");
        }
        run.setText(text == null ? "" : text);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
