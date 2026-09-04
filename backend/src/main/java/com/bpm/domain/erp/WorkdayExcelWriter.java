package com.bpm.domain.erp;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Xuất kết quả đối soát giờ công ra .xlsx — ba sheet: Đối soát · Công ERP theo ngày · Công KH theo ngày.
 *
 * Có file rời mới khép được vòng công việc: người đối soát còn phải gửi lại cho kế toán, cho khách
 * hàng hoặc đính kèm vào biên bản, mà những nơi đó không mở được màn hình của hệ thống.
 *
 * KHÔNG tô màu dòng lệch theo kiểu trang trí: dòng lệch đã có cột "Tình trạng" nói rõ bằng chữ, tô
 * thêm màu chỉ làm file nặng và mất nghĩa khi in đen trắng hay dán sang Google Sheets.
 */
public final class WorkdayExcelWriter {

    private WorkdayExcelWriter() {
    }

    public static byte[] write(String period, List<WorkdayReconciliation.Row> rows,
                               WorkdayReconciliation.Summary summary,
                               PivotView erp, PivotView customer) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle head = headerStyle(wb);
            writeReconcile(wb, head, period, rows, summary);
            writePivot(wb, head, "Cong ERP theo ngay", period, erp);
            writePivot(wb, head, "Cong KH theo ngay", period, customer);
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được file kết quả: " + e.getMessage(), e);
        }
    }

    /** Dữ liệu một bảng ngang, truyền từ tầng ứng dụng xuống để lớp này không phụ thuộc Spring. */
    public record PivotView(int daysInMonth, List<PivotLine> lines) {
    }

    public record PivotLine(String name, String empCode, Map<Integer, Double> daysByDay,
                            double totalDays, int dayCount) {
    }

    private static void writeReconcile(Workbook wb, CellStyle head, String period,
                                       List<WorkdayReconciliation.Row> rows,
                                       WorkdayReconciliation.Summary s) {
        Sheet sh = wb.createSheet("Doi soat");
        int r = 0;
        Row title = sh.createRow(r++);
        title.createCell(0).setCellValue("Đối soát công ERP vs khách hàng — kỳ " + period);
        title.getCell(0).setCellStyle(head);

        // Tổng hợp đặt NGAY đầu file: người nhận file thường chỉ cần biết "có lệch không, lệch bao nhiêu".
        Row sum = sh.createRow(r++);
        sum.createCell(0).setCellValue("Tổng: " + s.total() + " nhân sự · khớp " + s.matched()
                + " · lệch " + s.diff() + " · chỉ ERP " + s.erpOnly() + " · chỉ KH " + s.customerOnly()
                + " · công ERP " + s.erpDays() + " · công KH " + s.customerDays()
                + " · lệch " + s.diffDays());
        r++;

        String[] cols = { "Mã NV", "Nhân sự", "Tình trạng", "Ngày chấm", "Công ERP", "Công KH", "Lệch (ERP−KH)" };
        Row h = sh.createRow(r++);
        for (int c = 0; c < cols.length; c++) {
            h.createCell(c).setCellValue(cols[c]);
            h.getCell(c).setCellStyle(head);
        }
        for (WorkdayReconciliation.Row row : rows) {
            Row line = sh.createRow(r++);
            line.createCell(0).setCellValue(row.empCode() == null ? "" : row.empCode());
            line.createCell(1).setCellValue(row.name());
            line.createCell(2).setCellValue(row.status().label());
            line.createCell(3).setCellValue(row.erpDaysCount());
            line.createCell(4).setCellValue(row.erpDays());
            line.createCell(5).setCellValue(row.customerDays());
            line.createCell(6).setCellValue(row.diffDays());
        }
        sh.setColumnWidth(0, 2600);
        sh.setColumnWidth(1, 8000);
        sh.setColumnWidth(2, 4600);
        for (int c = 3; c < cols.length; c++) {
            sh.setColumnWidth(c, 3400);
        }
        sh.createFreezePane(0, 4);
    }

    private static void writePivot(Workbook wb, CellStyle head, String sheetName, String period, PivotView view) {
        Sheet sh = wb.createSheet(sheetName);
        YearMonth ym = YearMonth.parse(period);
        int days = view == null ? ym.lengthOfMonth() : view.daysInMonth();
        int r = 0;
        Row h = sh.createRow(r++);
        h.createCell(0).setCellValue("Mã NV");
        h.createCell(1).setCellValue("Nhân sự");
        for (int d = 1; d <= days; d++) {
            h.createCell(1 + d).setCellValue(d);
        }
        h.createCell(2 + days).setCellValue("Tổng công");
        h.createCell(3 + days).setCellValue("Số ngày");
        for (int c = 0; c <= 3 + days; c++) {
            if (h.getCell(c) != null) {
                h.getCell(c).setCellStyle(head);
            }
        }
        if (view != null) {
            for (PivotLine line : view.lines()) {
                Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(line.empCode() == null ? "" : line.empCode());
                row.createCell(1).setCellValue(line.name());
                for (int d = 1; d <= days; d++) {
                    Double v = line.daysByDay().get(d);
                    if (v != null) {
                        row.createCell(1 + d).setCellValue(v);
                    }
                }
                row.createCell(2 + days).setCellValue(line.totalDays());
                row.createCell(3 + days).setCellValue(line.dayCount());
            }
        }
        sh.setColumnWidth(0, 2600);
        sh.setColumnWidth(1, 8000);
        for (int d = 1; d <= days; d++) {
            sh.setColumnWidth(1 + d, 1200);
        }
        sh.setColumnWidth(2 + days, 3000);
        sh.setColumnWidth(3 + days, 2600);
        // Ghim cột tên và hàng tiêu đề: bảng 31 cột mà cuộn ngang không có gì neo lại thì không đọc được.
        sh.createFreezePane(2, 1);
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        st.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }
}
