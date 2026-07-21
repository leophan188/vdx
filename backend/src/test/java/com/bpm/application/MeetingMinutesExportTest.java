package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Biên bản họp (.docx) sinh từ một bản ghi Nhật ký — file phải MỞ ĐƯỢC và đủ 6 mục theo bố cục. */
class MeetingMinutesExportTest {

    private final MeetingMinutesExportService svc = new MeetingMinutesExportService();

    private static ProjectDto.DiaryEntry entry(List<ProjectDto.DiaryAction> actions) {
        return new ProjectDto.DiaryEntry("d1", "21/07/2026", "Họp-trao đổi",
                List.of("u1", "u2"), List.of("Nguyễn Văn A", "Trần Thị B"),
                "Anh C (Trưởng phòng)", "Rà soát tiến độ giai đoạn 1.\nChốt phạm vi UAT.",
                "Đồng ý nghiệm thu module A.",
                "Phòng họp tầng 5", "14:00", "15:30", actions,
                "u1", "Nguyễn Văn A", "2026-07-21T07:00:00Z", true);
    }

    /** Toàn bộ text của văn bản (đoạn văn + ô bảng) để kiểm tra nội dung đã in ra. */
    private static String textOf(byte[] docx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            for (XWPFTable t : doc.getTables()) {
                sb.append(t.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void xuatBienBanDuBoCucVaNextAction() throws Exception {
        byte[] docx = svc.toDocx(entry(List.of(
                new ProjectDto.DiaryAction("Sửa lỗi đăng nhập SSO", "Nguyễn Văn A", "25/07/2026", "DOING"),
                new ProjectDto.DiaryAction("Gửi bản demo v2", "Anh C", "28/07/2026", "NEW"))),
                "[BPM] Hệ thống quản trị", null);

        String text = textOf(docx);
        // 1. Quốc hiệu + tiêu đề
        assertThat(text).contains("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", "Độc lập – Tự do – Hạnh phúc", "BIÊN BẢN HỌP");
        assertThat(text).contains("[BPM] Hệ thống quản trị");
        // 2..5. Các mục
        assertThat(text).contains("I. THÀNH PHẦN THAM DỰ", "Nguyễn Văn A, Trần Thị B", "Anh C (Trưởng phòng)");
        assertThat(text).contains("II. THỜI GIAN TỔ CHỨC / ĐỊA ĐIỂM", "Từ 14:00 đến 15:30, ngày 21/07/2026", "Phòng họp tầng 5");
        assertThat(text).contains("III. NỘI DUNG", "Chốt phạm vi UAT.");
        assertThat(text).contains("IV. KẾT LUẬN", "Đồng ý nghiệm thu module A.");
        // 6. Next action — có bảng, đủ dòng + nhãn trạng thái tiếng Việt
        assertThat(text).contains("V. NEXT ACTION", "Sửa lỗi đăng nhập SSO", "Đang làm", "Gửi bản demo v2", "Mới");
    }

    @Test
    void khongCoNextActionVanInDuKhung() throws Exception {
        String text = textOf(svc.toDocx(entry(List.of()), "[BPM] Dự án", null));
        assertThat(text).contains("V. NEXT ACTION", "— Không có —");
    }

    @Test
    void tenFileTheoNgayLamViec() {
        assertThat(svc.fileName(entry(List.of()))).isEqualTo("bien-ban-hop-21-07-2026.docx");
    }
}
