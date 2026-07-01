package com.bpm.domain.report;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Mở file Excel an toàn (Epic 4, NFR-09): chỉ chấp nhận .xlsx (OOXML/zip — chữ ký "PK"), từ chối .xls cũ/macro .xlsm,
 * giới hạn kích thước, chống zip-bomb (tỉ lệ giải nén), giới hạn số dòng để tránh OOM.
 *
 * Lưu ý: ZipSecureFile.setMinInflateRatio là cấu hình TĨNH toàn cục của POI; ta đặt 1 lần khi mở.
 * Apache POI XSSF không tự thực thi macro/liên kết ngoài khi đọc — ta vẫn từ chối phần mở rộng .xlsm/.xltm.
 */
public final class SafeWorkbookReader {

    /** Giới hạn dung lượng file (10MB). */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    /** Giới hạn số dòng dữ liệu (chống OOM / file quá lớn). */
    public static final int MAX_DATA_ROWS = 100_000;
    /** Tỉ lệ giải nén tối thiểu cho phép (chống zip-bomb). */
    private static final double MIN_INFLATE_RATIO = 0.01d;

    private SafeWorkbookReader() {
    }

    /** Ngoại lệ file không an toàn / sai loại → service ánh xạ thành 400/thông báo rõ. */
    public static class UnsafeFileException extends RuntimeException {
        public UnsafeFileException(String message) {
            super(message);
        }
    }

    /**
     * Kiểm tra cơ bản (loại + dung lượng + chữ ký) rồi mở XSSFWorkbook.
     * Người gọi PHẢI đóng workbook trả về (try-with-resources).
     */
    public static Workbook open(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new UnsafeFileException("File rỗng.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new UnsafeFileException("File vượt giới hạn " + (MAX_FILE_BYTES / 1024 / 1024) + "MB.");
        }
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".xlsm") || lower.endsWith(".xltm") || lower.endsWith(".xlsb")) {
            throw new UnsafeFileException("Từ chối file chứa macro (.xlsm/.xltm/.xlsb).");
        }
        if (lower.endsWith(".xls")) {
            throw new UnsafeFileException("Định dạng .xls cũ không được hỗ trợ — hãy dùng .xlsx.");
        }
        if (!lower.endsWith(".xlsx")) {
            throw new UnsafeFileException("Chỉ chấp nhận file .xlsx.");
        }
        // Chữ ký zip OOXML: "PK"
        if (!(bytes[0] == 0x50 && bytes[1] == 0x4B)) {
            throw new UnsafeFileException("Nội dung file không phải định dạng .xlsx hợp lệ.");
        }

        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO); // chống zip-bomb (cấu hình tĩnh POI)
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UnsafeFileException("Không đọc được file .xlsx: " + e.getMessage());
        } catch (RuntimeException e) {
            // POI ném khi nghi zip-bomb / file hỏng
            throw new UnsafeFileException("File Excel không hợp lệ hoặc bị từ chối vì lý do an toàn.");
        }
    }
}
