package com.bpm;

import com.bpm.infrastructure.hr.EmployeeFileReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * File nhân sự thật (VDX) được xuất từ Google Sheets dùng IMPORTRANGE → mỗi ô là CÔNG THỨC
 * {@code =IFERROR(__xludf.DUMMYFUNCTION(...), "giá trị thật")}; giá trị thật nằm ở phần đã cache.
 * Test mô phỏng cấu trúc đó (công thức có giá trị đã cache) và xác minh reader lấy ĐÚNG giá trị,
 * không lấy chuỗi công thức.
 */
class EmployeeFileReaderTest {

    @Test
    void docGiaTriDaCacheCuaOCongThuc_khongLayChuoiCongThuc() throws Exception {
        String[] headers = {"ID", "Trạng thái", "Họ và tên", "Vị trí công việc", "Chức danh",
                "Mã bộ phận", "Đơn vị", "Ngày tham gia", "Ngày sinh", "Số điện thoại",
                "Loại hợp đồng hiện tại", "STK", "Ngân hàng"};
        String[] data = {"0025", "Đang làm việc", "Lê Hữu Thanh", "Giám đốc Trung tâm Phần mềm", "Giám đốc",
                "PDX", "KKD", "01/12/2023", "22/01/1990", "0979444692",
                "Hợp đồng lao động không xác định thời hạn", "190305403366", "Techcombank"};

        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Sheet1");
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]); // tiêu đề là text thường
            }
            Row r = sh.createRow(1);
            for (int i = 0; i < data.length; i++) {
                Cell c = r.createCell(i);
                // Mô phỏng ô Google Sheets: công thức lỗi (1/0) → IFERROR trả về dữ liệu thật làm giá trị cache.
                c.setCellFormula("IFERROR(1/0,\"" + data[i].replace("\"", "") + "\")");
            }
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll(); // tính & lưu giá trị cache
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            bytes = out.toByteArray();
        }

        EmployeeFileReader.ParsedFile parsed = new EmployeeFileReader().read(bytes, "vdx.xlsx");
        assertThat(parsed.rows()).hasSize(1);
        EmployeeFileReader.EmployeeRow row = parsed.rows().get(0);
        assertThat(row.get("empCode")).isEqualTo("0025");
        assertThat(row.get("status")).isEqualTo("Đang làm việc");
        assertThat(row.get("fullName")).isEqualTo("Lê Hữu Thanh");
        assertThat(row.get("jobPosition")).isEqualTo("Giám đốc Trung tâm Phần mềm");
        assertThat(row.get("title")).isEqualTo("Giám đốc");
        assertThat(row.get("deptCode")).isEqualTo("PDX");
        assertThat(row.get("unit")).isEqualTo("KKD");
        assertThat(row.get("phone")).isEqualTo("0979444692"); // giữ số 0 đầu (đọc dạng chuỗi cache)
        assertThat(row.get("bankAccount")).isEqualTo("190305403366");
    }
}
