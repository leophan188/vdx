package com.bpm.application;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocxMergeTest {

    private static byte[] docWith(java.util.function.Consumer<XWPFDocument> build) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            build.accept(d);
            d.write(out);
            return out.toByteArray();
        }
    }

    private static String textOf(byte[] docx) throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : d.getParagraphs()) sb.append(p.getText()).append('\n');
            return sb.toString();
        }
    }

    @Test
    void thay_ma_nam_tron_1_run() throws Exception {
        byte[] tpl = docWith(d -> {
            XWPFRun r = d.createParagraph().createRun();
            r.setText("Kính gửi «hoTen», chức danh «chucDanh».");
        });
        byte[] merged = DocxMerge.merge(tpl, Map.of("hoTen", "Nguyễn Văn A", "chucDanh", "Trưởng phòng"));
        String text = textOf(merged);
        assertThat(text).contains("Kính gửi Nguyễn Văn A, chức danh Trưởng phòng.");
        assertThat(text).doesNotContain("«");
    }

    @Test
    void thay_ma_bi_cat_qua_nhieu_run() throws Exception {
        // Word/OnlyOffice hay cắt «hoTen» thành nhiều run: "«ho" + "Ten" + "»".
        byte[] tpl = docWith(d -> {
            XWPFParagraph p = d.createParagraph();
            p.createRun().setText("Xin chào «ho");
            p.createRun().setText("Ten");
            p.createRun().setText("», hết.");
        });
        byte[] merged = DocxMerge.merge(tpl, Map.of("hoTen", "Trần B"));
        String text = textOf(merged);
        assertThat(text).contains("Xin chào Trần B, hết.");
        assertThat(text).doesNotContain("«").doesNotContain("»");
    }

    @Test
    void ma_khong_xac_dinh_giu_nguyen_ma_co_gia_tri_rong_thi_xoa() throws Exception {
        byte[] tpl = docWith(d -> d.createParagraph().createRun().setText("A=«coGiaTri» B=«rong» C=«laKhongCo»"));
        byte[] merged = DocxMerge.merge(tpl, Map.of("coGiaTri", "X", "rong", ""));
        String text = textOf(merged);
        assertThat(text).contains("A=X");
        assertThat(text).contains("B= C=");          // rong → "", laKhongCo giữ nguyên
        assertThat(text).contains("«laKhongCo»");
    }
}
