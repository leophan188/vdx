package com.bpm.infrastructure.hr;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tải nội dung CSV của một Google Sheet qua đường dẫn export (tuỳ chọn đồng bộ qua link — Epic 1 GĐ2).
 * Sheet PHẢI cho phép xem công khai ("Bất kỳ ai có đường liên kết → Người xem") hoặc đã "Xuất bản lên web".
 * Ưu điểm so với tải file .xlsx: bản CSV export đã được Google TÍNH SẴN công thức (IMPORTRANGE…) → giá trị sạch.
 */
@Component
public class GoogleSheetReader {

    private static final Pattern ID = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");
    private static final Pattern GID = Pattern.compile("[#&?]gid=([0-9]+)");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Trả về bytes CSV của trang tính. Ném IllegalArgumentException (→ 400) với thông báo rõ nếu lỗi/riêng tư. */
    public byte[] fetchCsv(String sheetUrl) {
        if (sheetUrl == null || sheetUrl.isBlank()) {
            throw new IllegalArgumentException("Thiếu link Google Sheet.");
        }
        Matcher m = ID.matcher(sheetUrl);
        if (!m.find()) {
            throw new IllegalArgumentException(
                    "Link không hợp lệ — cần dạng https://docs.google.com/spreadsheets/d/<ID>/...");
        }
        String id = m.group(1);
        Matcher g = GID.matcher(sheetUrl);
        String gid = g.find() ? g.group(1) : "0";
        String exportUrl = "https://docs.google.com/spreadsheets/d/" + id + "/export?format=csv&gid=" + gid;

        HttpResponse<byte[]> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(exportUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(25))
                    .GET().build();
            res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("Lỗi tải Google Sheet: " + e.getMessage());
        }

        int sc = res.statusCode();
        byte[] body = res.body() == null ? new byte[0] : res.body();
        String ctype = res.headers().firstValue("content-type").orElse("").toLowerCase();
        boolean looksHtml = ctype.contains("text/html") || (body.length > 0 && (body[0] == '<'));
        if (sc == 401 || sc == 403 || looksHtml) {
            throw new IllegalArgumentException(
                    "Không đọc được Sheet (đang ở chế độ riêng tư). Hãy đặt chia sẻ \"Bất kỳ ai có đường liên kết → "
                    + "Người xem\", hoặc Tệp → Chia sẻ → Xuất bản lên web (CSV), rồi thử lại.");
        }
        if (sc != 200) {
            throw new IllegalArgumentException("Google Sheet trả mã " + sc + " — kiểm tra lại link / quyền chia sẻ.");
        }
        if (body.length > MAX_BYTES) {
            throw new IllegalArgumentException("Dữ liệu Sheet quá lớn (>10MB).");
        }
        return body;
    }
}
