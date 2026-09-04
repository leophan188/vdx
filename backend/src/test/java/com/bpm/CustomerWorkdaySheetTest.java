package com.bpm;

import com.bpm.domain.erp.CustomerWorkdaySheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảng công khách hàng gửi thật: mười dòng đầu là tên công ty, địa chỉ, mã số thuế; hàng tiêu đề nằm
 * mãi dòng 12 và có tầng thứ hai ghi thứ trong tuần; cột ngày ghi "01/2"; không có cột mã nhân viên;
 * cuối tuần để trống; cuối bảng còn mục "2." của phần ngoài giờ.
 */
class CustomerWorkdaySheetTest {

    private static final YearMonth KY = YearMonth.of(2026, 2);

    private Workbook mauKhachHang() {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("Bang cong");
        sh.createRow(0).createCell(1).setCellValue("BIÊN BẢN NGHIỆM THU");
        sh.createRow(6).createCell(1).setCellValue("Địa chỉ:");
        sh.createRow(8).createCell(1).setCellValue("Mã số thuế:");
        sh.createRow(10).createCell(1).setCellValue("1. Thời gian cung cấp dịch vụ trong giờ hành chính");

        // Hàng tiêu đề (dòng 12 trên Excel = index 11): STT | Họ và tên | Hạng mục | Dự án | ngày… | tổng
        Row h = sh.createRow(11);
        h.createCell(1).setCellValue("STT");
        h.createCell(2).setCellValue("Họ và tên");
        h.createCell(3).setCellValue("Hạng mục công việc");
        h.createCell(4).setCellValue("Dự án");
        for (int d = 1; d <= 28; d++) {
            h.createCell(4 + d).setCellValue(String.format("%02d/2", d));
        }
        h.createCell(33).setCellValue("Số ngày làm việc thực tế trong tháng");
        h.createCell(34).setCellValue("Ghi chú");

        // Tầng thứ hai của tiêu đề: thứ trong tuần — không phải dòng dữ liệu.
        Row h2 = sh.createRow(12);
        String[] thu = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        for (int d = 1; d <= 28; d++) {
            h2.createCell(4 + d).setCellValue(thu[(d - 1) % 7]);
        }

        // Hai nhân sự. Ngày 1, 7, 8 (Sun/Sat/Sun) để trống như file thật.
        Row r1 = sh.createRow(13);
        r1.createCell(1).setCellValue(1);
        r1.createCell(2).setCellValue("Đỗ Quốc Hưng");
        r1.createCell(3).setCellValue("JavaScripts (Lập trình viên)");
        r1.createCell(4).setCellValue("Bảo trì, bảo dưỡng MySGR");
        r1.createCell(6).setCellValue(1.0);    // 02/2
        r1.createCell(9).setCellValue(0.5);    // 05/2
        r1.createCell(16).setCellValue(0.0);   // 12/2 — ghi 0, không phải công
        r1.createCell(33).setCellValue(12.5);

        Row r2 = sh.createRow(14);
        r2.createCell(1).setCellValue(2);
        r2.createCell(2).setCellValue("Nguyễn Quốc Tiến");
        r2.createCell(3).setCellValue("Java (Lập trình viên)");
        r2.createCell(6).setCellValue(1.0);
        r2.createCell(7).setCellValue(1.0);
        r2.createCell(33).setCellValue(15.0);

        // Mục kế tiếp của biên bản — KHÔNG được đọc lẫn vào bảng trong giờ hành chính.
        sh.createRow(16).createCell(1).setCellValue("2. Thời gian cung cấp dịch vụ ngoài giờ hành chính");
        Row ot = sh.createRow(18);
        ot.createCell(2).setCellValue("Đỗ Quốc Hưng");
        ot.createCell(6).setCellValue(3.0);
        return wb;
    }

    @Test
    @DisplayName("Đọc đúng bảng thật: bỏ dòng tiêu đề, lấy được ngày công theo từng ngày")
    void docBangThat() throws Exception {
        try (Workbook wb = mauKhachHang()) {
            CustomerWorkdaySheet.ParseResult res = CustomerWorkdaySheet.read(wb, KY);

            assertThat(res.cells()).extracting(CustomerWorkdaySheet.Cellule::name)
                    .containsOnly("Đỗ Quốc Hưng", "Nguyễn Quốc Tiến");
            // Ô ghi 0 không phải một ngày công — không được sinh dòng.
            assertThat(res.cells()).allSatisfy(c -> assertThat(c.days()).isNotZero());

            List<CustomerWorkdaySheet.Cellule> hung = res.cells().stream()
                    .filter(c -> c.name().startsWith("Đỗ")).toList();
            assertThat(hung).hasSize(2);
            assertThat(hung).extracting(CustomerWorkdaySheet.Cellule::date)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 5));
            assertThat(hung).extracting(CustomerWorkdaySheet.Cellule::days)
                    .containsExactlyInAnyOrder(1.0, 0.5);
        }
    }

    @Test
    @DisplayName("Không nuốt bảng ngoài giờ ở mục 2 phía dưới")
    void dungTruocMucKeTiep() throws Exception {
        try (Workbook wb = mauKhachHang()) {
            CustomerWorkdaySheet.ParseResult res = CustomerWorkdaySheet.read(wb, KY);
            // Dòng OT ghi 3 công vào ngày 02/2; nếu đọc lẫn thì Hưng có 3 ô và tổng vọt lên.
            double tongHung = res.cells().stream()
                    .filter(c -> c.name().startsWith("Đỗ")).mapToDouble(CustomerWorkdaySheet.Cellule::days).sum();
            assertThat(tongHung).isEqualTo(1.5);
        }
    }

    @Test
    @DisplayName("Chỉ đọc sheet ĐẦU TIÊN — các sheet chi tiết từng người bị bỏ qua")
    void chiDocSheetDau() throws Exception {
        try (Workbook wb = mauKhachHang()) {
            // Sheet chi tiết của một nhân sự, cùng khuôn nhưng số công khác — nếu bị đọc lẫn thì
            // công của người đó tăng gấp đôi mà bảng vẫn trông bình thường.
            Sheet chiTiet = wb.createSheet("1. Đỗ Quốc Hưng");
            Row h = chiTiet.createRow(0);
            h.createCell(2).setCellValue("Họ và tên");
            for (int d = 1; d <= 28; d++) {
                h.createCell(4 + d).setCellValue(String.format("%02d/2", d));
            }
            Row r = chiTiet.createRow(1);
            r.createCell(2).setCellValue("Đỗ Quốc Hưng");
            r.createCell(6).setCellValue(1.0);
            r.createCell(8).setCellValue(1.0);

            CustomerWorkdaySheet.ParseResult res = CustomerWorkdaySheet.read(wb, KY);
            assertThat(res.sheetName()).isEqualTo("Bang cong");
            double tongHung = res.cells().stream()
                    .filter(c -> c.name().startsWith("Đỗ")).mapToDouble(CustomerWorkdaySheet.Cellule::days).sum();
            assertThat(tongHung).isEqualTo(1.5);
        }
    }

    @Test
    @DisplayName("Cột tổng cuối bảng không bị nhận nhầm thành một ngày")
    void khongNhamCotTong() throws Exception {
        try (Workbook wb = mauKhachHang()) {
            CustomerWorkdaySheet.ParseResult res = CustomerWorkdaySheet.read(wb, KY);
            assertThat(res.cells()).extracting(CustomerWorkdaySheet.Cellule::days).doesNotContain(12.5, 15.0);
        }
    }
}
