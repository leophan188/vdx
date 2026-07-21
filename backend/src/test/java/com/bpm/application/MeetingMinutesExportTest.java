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
                List.of(new ProjectDto.DiaryPerson("Nguyễn Văn A", "Quản lý dự án"),
                        new ProjectDto.DiaryPerson("Trần Thị B", "Thành viên")),
                "Anh C (Trưởng phòng), Chị D", "Rà soát tiến độ giai đoạn 1.\nChốt phạm vi UAT.",
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
        assertThat(text).contains("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", "Độc lập - Tự do - Hạnh phúc", "BIÊN BẢN HỌP");
        assertThat(text).contains("[BPM] Hệ thống quản trị");
        // 2..5. Các mục
        // Khách hàng đứng TRƯỚC đơn vị thực hiện, mỗi người có Họ và tên + Vai trò
        assertThat(text).contains("I. THÀNH PHẦN THAM DỰ", "1. Phía khách hàng", "2. Phía đơn vị thực hiện");
        assertThat(text.indexOf("1. Phía khách hàng")).isLessThan(text.indexOf("2. Phía đơn vị thực hiện"));
        assertThat(text).contains("Họ và tên: ", "Vai trò: ");
        assertThat(text).contains("Anh C", "Trưởng phòng");          // vai trò tách từ ngoặc
        assertThat(text).contains("Chị D");                           // không ghi vai trò → vẫn liệt kê
        assertThat(text).contains("Nguyễn Văn A", "Quản lý dự án");   // vai trò lấy từ hệ thống
        assertThat(text).contains("Trần Thị B", "Thành viên");
        assertThat(text).contains("II. THỜI GIAN TỔ CHỨC / ĐỊA ĐIỂM",
                "Từ 14 giờ 00 đến 15 giờ 30, ngày 21/07/2026", "Phòng họp tầng 5");
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

    /** Người dùng gõ ô text (mỗi dòng 1 việc, không phụ trách/hạn) → bảng RÚT GỌN 2 cột. */
    @Test
    void chiCoNoiDungThiInBangHaiCot() throws Exception {
        String text = textOf(svc.toDocx(entry(List.of(
                new ProjectDto.DiaryAction("Sửa lỗi đăng nhập SSO", null, null, "NEW"),
                new ProjectDto.DiaryAction("Gửi bản demo v2 cho khách", null, null, "NEW"))),
                "[BPM] Dự án", null));

        assertThat(text).contains("V. NEXT ACTION", "STT", "Nội dung công việc",
                "Sửa lỗi đăng nhập SSO", "Gửi bản demo v2 cho khách");
        // Không in các cột thừa rỗng
        assertThat(text).doesNotContain("Người phụ trách", "Hạn hoàn thành", "Trạng thái");
    }

    /** Trình bày chuẩn văn bản hành chính: Times New Roman, chữ đen, lề 3-2-2-2cm, bảng có khung. */
    @Test
    void trinhBayChuanVanBanHanhChinh() throws Exception {
        byte[] docx = svc.toDocx(entry(List.of(
                new ProjectDto.DiaryAction("Việc A", null, null, "NEW"))), "[BPM] Dự án", null);
        String xml;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            xml = doc.getDocument().getBody().xmlText();
        }
        assertThat(xml).contains("Times New Roman");
        assertThat(xml).doesNotContain("1F4E79");                 // không còn màu thương hiệu
        assertThat(xml).contains("w:left=\"1701\"", "w:right=\"1134\""); // lề trái 3cm, phải 2cm
        assertThat(xml).contains("w:val=\"both\"");               // thân bài căn đều hai bên
        assertThat(xml).contains("insideH");                      // bảng có kẻ khung trong
        assertThat(textOf(docx)).contains("Ngày 21 tháng 07 năm 2026", "NGƯỜI GHI BIÊN BẢN");
    }

    @Test
    void tenFileTheoNgayLamViec() {
        assertThat(svc.fileName(entry(List.of()))).isEqualTo("bien-ban-hop-21-07-2026.docx");
    }
}
