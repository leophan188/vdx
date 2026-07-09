package com.bpm.application;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trộn dữ liệu Hồ sơ vào file .docx: thay mã «tênTrường» bằng giá trị tương ứng. Dùng Apache POI/XWPF
 * (đã có sẵn trên classpath). Giữ nguyên định dạng: chỉ đổi text của các run chứa mã, và tự GỘP các run
 * bị Word/OnlyOffice cắt giữa chừng một mã (vd «ho|Ten») để mỗi mã nằm trọn trong 1 run trước khi thay.
 * Mã KHÔNG có giá trị trong bản đồ → giữ nguyên (người dùng thấy để sửa); mã có giá trị rỗng → thay bằng "".
 */
public final class DocxMerge {

    private static final Logger log = LoggerFactory.getLogger(DocxMerge.class);
    /** Mã trộn: «key» — key là chữ/số/_/. (không chứa dấu « »). */
    private static final Pattern TOKEN = Pattern.compile("«([^«»]+)»");

    private DocxMerge() {
    }

    /** Có ít nhất một mã «...» trong danh sách giá trị không (để bỏ qua trộn khi không cần). */
    public static byte[] merge(byte[] templateBytes, Map<String, String> values) {
        if (templateBytes == null || values == null || values.isEmpty()) {
            return templateBytes;
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(templateBytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, values);
            }
            for (XWPFTable t : doc.getTables()) {
                replaceInTable(t, values);
            }
            for (XWPFHeader h : doc.getHeaderList()) {
                for (XWPFParagraph p : h.getParagraphs()) {
                    replaceInParagraph(p, values);
                }
                for (XWPFTable t : h.getTables()) {
                    replaceInTable(t, values);
                }
            }
            for (XWPFFooter f : doc.getFooterList()) {
                for (XWPFParagraph p : f.getParagraphs()) {
                    replaceInParagraph(p, values);
                }
                for (XWPFTable t : f.getTables()) {
                    replaceInTable(t, values);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            // Trộn lỗi (file lạ…) → dùng template gốc, KHÔNG chặn mở soạn thảo.
            log.warn("[docx-merge] trộn dữ liệu lỗi, dùng template gốc: {}", e.getMessage());
            return templateBytes;
        }
    }

    private static void replaceInTable(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, values);
                }
                for (XWPFTable nested : cell.getTables()) {
                    replaceInTable(nested, values);
                }
            }
        }
    }

    private static void replaceInParagraph(XWPFParagraph p, Map<String, String> values) {
        mergeSplitTokens(p);
        List<XWPFRun> runs = p.getRuns();
        if (runs == null) {
            return;
        }
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text == null || text.indexOf('«') < 0) {
                continue;
            }
            Matcher m = TOKEN.matcher(text);
            if (!m.find()) {
                continue;
            }
            m.reset();
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String key = m.group(1).trim();
                String val = values.get(key);
                // Không xác định → giữ nguyên «key»; xác định (kể cả rỗng) → thay.
                m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
            }
            m.appendTail(sb);
            run.setText(sb.toString(), 0);
        }
    }

    /** Gộp các run bị cắt giữa một mã: run có « nhưng chưa có » → nối các run kế tiếp tới khi gặp ». */
    private static void mergeSplitTokens(XWPFParagraph p) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<XWPFRun> runs = p.getRuns();
            if (runs == null) {
                return;
            }
            for (int i = 0; i < runs.size(); i++) {
                String t = safe(runs.get(i).getText(0));
                int open = t.lastIndexOf('«');
                if (open < 0 || t.indexOf('»', open) >= 0) {
                    continue; // không mở, hoặc đã đóng ngay trong run này
                }
                StringBuilder merged = new StringBuilder(t);
                int j = i + 1;
                boolean closed = false;
                while (j < runs.size()) {
                    String tj = safe(runs.get(j).getText(0));
                    merged.append(tj);
                    j++;
                    if (tj.indexOf('»') >= 0) {
                        closed = true;
                        break;
                    }
                }
                if (closed) {
                    runs.get(i).setText(merged.toString(), 0);
                    for (int k = j - 1; k > i; k--) {
                        p.removeRun(k);
                    }
                    changed = true;
                    break; // danh sách run đã đổi → duyệt lại
                }
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
