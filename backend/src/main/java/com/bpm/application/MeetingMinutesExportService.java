package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xuất BIÊN BẢN HỌP (.docx) từ một bản ghi Nhật ký dự án — POI/XWPF thuần code, không cần file mẫu.
 *
 * <p>Trình bày theo lối văn bản hành chính Việt Nam (tham chiếu NĐ 30/2020): Times New Roman 13,
 * lề trái 3cm / phải 2cm / trên–dưới 2cm, giãn dòng 1.3, chữ ĐEN (không màu thương hiệu),
 * quốc hiệu – tiêu ngữ căn giữa có gạch ngang, thân bài căn đều hai bên, bảng có khung kẻ.
 *
 * <p>Bố cục:
 * <ol>
 *   <li>Quốc hiệu – Tiêu ngữ + tiêu đề BIÊN BẢN HỌP</li>
 *   <li>I. Thành phần tham dự — KHÁCH HÀNG trước, đơn vị thực hiện sau; mỗi người "Họ và tên / Vai trò"</li>
 *   <li>II. Thời gian tổ chức / Địa điểm</li>
 *   <li>III. Nội dung</li>
 *   <li>IV. Kết luận</li>
 *   <li>V. Next action (bảng)</li>
 * </ol>
 * Mục nào trống vẫn IN nhưng ghi "—" để biên bản luôn đủ khung, người họp điền tay được.
 */
@Service
public class MeetingMinutesExportService {

    // ===== Chuẩn trình bày =====
    private static final String FONT = "Times New Roman";
    private static final int SIZE = 13;          // cỡ chữ thân bài
    private static final int SIZE_TITLE = 16;    // tên loại văn bản
    private static final double LINE = 1.3;      // giãn dòng
    private static final String HEADER_SHADE = "D9D9D9"; // nền hàng tiêu đề bảng (xám nhạt)

    // 1cm ≈ 567 twips
    private static final int MAR_TOP = 1134, MAR_BOTTOM = 1134, MAR_LEFT = 1701, MAR_RIGHT = 1134;
    private static final int IND_1 = 397;   // thụt cấp 1 (~0.7cm)
    private static final int IND_2 = 794;   // thụt cấp 2 (~1.4cm)

    private static final String[] ACTION_COLS = { "STT", "Nội dung công việc", "Người phụ trách", "Hạn hoàn thành", "Trạng thái" };
    private static final int[] ACTION_W = { 6, 44, 20, 16, 14 };
    /** Bảng rút gọn khi người dùng chỉ gõ nội dung việc (mỗi dòng 1 việc). */
    private static final String[] SIMPLE_COLS = { "STT", "Nội dung công việc" };
    private static final int[] SIMPLE_W = { 8, 92 };
    /** "Nguyễn Văn A (Trưởng phòng)" → tên + vai trò (hỗ trợ cả ngoặc full-width). */
    private static final Pattern CLIENT_ROLE = Pattern.compile("^(.*?)\\s*[(（]([^)）]*)[)）]$");

    /**
     * @param e           bản ghi nhật ký (đã resolve tên + vai trò nhân sự)
     * @param projectName tên hiển thị dự án, vd "[BPM] Hệ thống quản trị"
     * @param companyName tên đơn vị thực hiện (null → ghi chung "đơn vị thực hiện")
     */
    public byte[] toDocx(ProjectDto.DiaryEntry e, String projectName, String companyName) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pageSetup(doc);
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

    // ===== Khổ giấy + lề =====
    private static void pageSetup(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageMar mar = sectPr.addNewPgMar();
        mar.setTop(BigInteger.valueOf(MAR_TOP));
        mar.setBottom(BigInteger.valueOf(MAR_BOTTOM));
        mar.setLeft(BigInteger.valueOf(MAR_LEFT));
        mar.setRight(BigInteger.valueOf(MAR_RIGHT));
    }

    // ===== 1. Quốc hiệu – Tiêu ngữ + tiêu đề =====
    private static void header(XWPFDocument doc, ProjectDto.DiaryEntry e, String projectName) {
        center(doc, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", true, SIZE, false);
        center(doc, "Độc lập - Tự do - Hạnh phúc", true, SIZE, false);
        center(doc, "______________________", false, SIZE - 2, false);
        blank(doc);
        center(doc, "BIÊN BẢN HỌP", true, SIZE_TITLE, false);
        if (notBlank(e.category())) {
            center(doc, "V/v: " + e.category(), false, SIZE, true);
        }
        if (notBlank(projectName)) {
            center(doc, "Dự án: " + projectName, true, SIZE, false);
        }
        blank(doc);
    }

    // ===== 2. Thành phần tham dự — KHÁCH HÀNG TRƯỚC, đơn vị thực hiện SAU =====
    private static void attendees(XWPFDocument doc, ProjectDto.DiaryEntry e, String companyName) {
        heading(doc, "I. THÀNH PHẦN THAM DỰ");
        subHeading(doc, "1. Phía khách hàng");
        people(doc, parseClients(e.clientContacts()));
        subHeading(doc, notBlank(companyName) ? "2. Phía " + companyName : "2. Phía đơn vị thực hiện");
        people(doc, e.team());
    }

    /** In từng người dạng "Họ và tên: … — Vai trò: …". Không có ai → "—". */
    private static void people(XWPFDocument doc, List<ProjectDto.DiaryPerson> list) {
        if (list == null || list.isEmpty()) {
            body(doc, IND_2, ParagraphAlignment.LEFT, "—");
            return;
        }
        for (ProjectDto.DiaryPerson p : list) {
            XWPFParagraph par = para(doc, IND_2, ParagraphAlignment.LEFT);
            run(par, "- Họ và tên: ", true, SIZE, false);
            run(par, notBlank(p.name()) ? p.name() : "—", false, SIZE, false);
            run(par, "     Vai trò: ", true, SIZE, false);
            run(par, notBlank(p.role()) ? p.role() : "—", false, SIZE, false);
        }
    }

    /**
     * Tách text tự do phía khách hàng thành danh sách người.
     * Chấp nhận "Nguyễn Văn A (Trưởng phòng), Chị B" hoặc mỗi người một dòng;
     * phần trong ngoặc là VAI TRÒ (khách hàng không có trong hệ thống nên phải tự ghi).
     */
    static List<ProjectDto.DiaryPerson> parseClients(String raw) {
        List<ProjectDto.DiaryPerson> out = new ArrayList<>();
        if (!notBlank(raw)) {
            return out;
        }
        for (String part : raw.replace("\r\n", "\n").split("[\n,;]")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            Matcher m = CLIENT_ROLE.matcher(s);
            if (m.matches()) {
                out.add(new ProjectDto.DiaryPerson(m.group(1).trim(), m.group(2).trim()));
            } else {
                out.add(new ProjectDto.DiaryPerson(s, null));
            }
        }
        return out;
    }

    // ===== 3. Thời gian / Địa điểm =====
    private static void timeAndPlace(XWPFDocument doc, ProjectDto.DiaryEntry e) {
        heading(doc, "II. THỜI GIAN TỔ CHỨC / ĐỊA ĐIỂM");
        labeled(doc, "Thời gian:", timeText(e));
        labeled(doc, "Địa điểm / hình thức:", e.location());
    }

    /** "Từ 14 giờ 00 đến 15 giờ 30, ngày 21/07/2026" — thiếu giờ thì chỉ ghi ngày. */
    private static String timeText(ProjectDto.DiaryEntry e) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(e.startTime()) && notBlank(e.endTime())) {
            sb.append("Từ ").append(hourText(e.startTime())).append(" đến ").append(hourText(e.endTime()));
        } else if (notBlank(e.startTime())) {
            sb.append("Bắt đầu lúc ").append(hourText(e.startTime()));
        }
        if (notBlank(e.workDate())) {
            sb.append(sb.length() > 0 ? ", ngày " : "Ngày ").append(e.workDate());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** "14:00" → "14 giờ 00" (lối ghi văn bản hành chính). */
    private static String hourText(String hhmm) {
        String[] p = hhmm.split(":");
        return p.length == 2 ? p[0] + " giờ " + p[1] : hhmm;
    }

    // ===== 6. Next action =====
    private static void nextActions(XWPFDocument doc, ProjectDto.DiaryEntry e) {
        List<ProjectDto.DiaryAction> actions = e.nextActions();
        heading(doc, "V. NEXT ACTION (việc cần làm tiếp)");
        // Người dùng thường chỉ gõ nội dung (mỗi dòng 1 việc) → in bảng 2 cột cho gọn.
        // Chỉ khi có phụ trách/hạn/trạng thái mới in đủ 5 cột, tránh bảng đầy ô rỗng.
        boolean detailed = hasDetail(actions);
        String[] cols = detailed ? ACTION_COLS : SIMPLE_COLS;
        int[] widths = detailed ? ACTION_W : SIMPLE_W;

        XWPFTable tbl = doc.createTable();
        tbl.setWidth("100%");
        bordered(tbl);
        XWPFTableRow hr = tbl.getRow(0);
        for (int c = 0; c < cols.length; c++) {
            cell(hr, c, cols[c], true, HEADER_SHADE, widths[c], ParagraphAlignment.CENTER);
        }
        if (actions == null || actions.isEmpty()) {
            XWPFTableRow row = tbl.createRow();
            cell(row, 0, "", false, null, 0, ParagraphAlignment.CENTER);
            cell(row, 1, "— Không có —", false, null, 0, ParagraphAlignment.LEFT);
            return;
        }
        int i = 1;
        for (ProjectDto.DiaryAction a : actions) {
            XWPFTableRow row = tbl.createRow();
            cell(row, 0, String.valueOf(i++), false, null, 0, ParagraphAlignment.CENTER);
            cell(row, 1, nz(a.content()), false, null, 0, ParagraphAlignment.LEFT);
            if (detailed) {
                cell(row, 2, nz(a.owner()), false, null, 0, ParagraphAlignment.LEFT);
                cell(row, 3, nz(a.dueDate()), false, null, 0, ParagraphAlignment.CENTER);
                cell(row, 4, statusLabel(a.status()), false, null, 0, ParagraphAlignment.CENTER);
            }
        }
    }

    /** Có việc nào ghi phụ trách/hạn/trạng thái khác "Mới" không? */
    private static boolean hasDetail(List<ProjectDto.DiaryAction> actions) {
        if (actions == null) {
            return false;
        }
        for (ProjectDto.DiaryAction a : actions) {
            if (notBlank(a.owner()) || notBlank(a.dueDate())
                    || (notBlank(a.status()) && !"NEW".equalsIgnoreCase(a.status().trim()))) {
                return true;
            }
        }
        return false;
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
        String d = dateInWords(e.workDate());
        if (d != null) {
            XWPFParagraph pd = para(doc, 0, ParagraphAlignment.RIGHT);
            run(pd, d, false, SIZE, true);
        }
        XWPFParagraph p = para(doc, 0, ParagraphAlignment.RIGHT);
        run(p, "NGƯỜI GHI BIÊN BẢN", true, SIZE, false);
        XWPFParagraph p2 = para(doc, 0, ParagraphAlignment.RIGHT);
        run(p2, "(Ký, ghi rõ họ tên)", false, SIZE, true);
        blank(doc);
        blank(doc);
        XWPFParagraph p3 = para(doc, 0, ParagraphAlignment.RIGHT);
        run(p3, nz(e.createdByName()), true, SIZE, false);
    }

    /** "21/07/2026" → "Ngày 21 tháng 07 năm 2026" (dòng trên chỗ ký). */
    private static String dateInWords(String dmy) {
        if (!notBlank(dmy)) {
            return null;
        }
        String[] p = dmy.split("/");
        return p.length == 3 ? "Ngày " + p[0] + " tháng " + p[1] + " năm " + p[2] : "Ngày " + dmy;
    }

    // ===================== Helpers trình bày =====================

    /** Đoạn văn chuẩn: font/giãn dòng thống nhất + thụt lề + căn lề. */
    private static XWPFParagraph para(XWPFDocument doc, int indentLeft, ParagraphAlignment align) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        p.setSpacingBetween(LINE, LineSpacingRule.AUTO);
        p.setSpacingAfter(20);
        if (indentLeft > 0) {
            p.setIndentationLeft(indentLeft);
        }
        return p;
    }

    private static XWPFRun run(XWPFParagraph p, String text, boolean bold, int size, boolean italic) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setBold(bold);
        r.setItalic(italic);
        r.setColor("000000");
        r.setText(text == null ? "" : text);
        return r;
    }

    private static void center(XWPFDocument doc, String text, boolean bold, int size, boolean italic) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0);
        run(p, text, bold, size, italic);
    }

    /** Đề mục cấp 1 (I., II., …) — in hoa đậm, canh trái. */
    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        p.setSpacingAfter(40);
        p.setSpacingBetween(LINE, LineSpacingRule.AUTO);
        run(p, text, true, SIZE, false);
    }

    /** Đề mục cấp 2 (1., 2., …) trong một mục. */
    private static void subHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = para(doc, IND_1, ParagraphAlignment.LEFT);
        p.setSpacingBefore(60);
        run(p, text, true, SIZE, false);
    }

    /** Dòng "Nhãn: giá trị" thụt cấp 1 — giá trị trống in "—". */
    private static void labeled(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = para(doc, IND_1, ParagraphAlignment.LEFT);
        run(p, "- " + label + " ", true, SIZE, false);
        run(p, notBlank(value) ? value : "—", false, SIZE, false);
    }

    private static void body(XWPFDocument doc, int indent, ParagraphAlignment align, String text) {
        run(para(doc, indent, align), text, false, SIZE, false);
    }

    /** Text nhiều dòng → mỗi dòng một đoạn, CĂN ĐỀU hai bên. Trống → "—". */
    private static void multiline(XWPFDocument doc, String text) {
        if (!notBlank(text)) {
            body(doc, IND_1, ParagraphAlignment.LEFT, "—");
            return;
        }
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            body(doc, IND_1, ParagraphAlignment.BOTH, line.trim());
        }
    }

    private static void blank(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        run(p, "", false, SIZE, false);
    }

    /** Kẻ khung đầy đủ cho bảng (mặc định POI để viền mảnh/không đều). */
    private static void bordered(XWPFTable t) {
        t.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
    }

    /** Điền 1 ô bảng: đậm + nền + rộng % (0 = bỏ qua) + căn lề. */
    private static void cell(XWPFTableRow row, int idx, String text, boolean bold, String shadeHex,
                             int widthPct, ParagraphAlignment align) {
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
        p.setAlignment(align);
        p.setSpacingAfter(0);
        run(p, text, bold, SIZE - 1, false);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
