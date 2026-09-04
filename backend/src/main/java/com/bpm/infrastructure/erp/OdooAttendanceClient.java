package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.AttendanceRecord;
import com.bpm.domain.erp.ErpConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc chấm công {@code hr.attendance} từ Odoo qua JSON-RPC ({@code POST /jsonrpc}).
 *
 * Vì sao JSON-RPC chứ không phải cào màn hình: đường link trên trình duyệt
 * ({@code /web#action=148&model=hr.attendance&view_type=list}) chỉ là địa chỉ của giao diện, dữ liệu
 * thật nằm sau lời gọi RPC mà giao diện đó phát đi. Gọi thẳng RPC vừa ổn định trước mọi thay đổi giao
 * diện, vừa lấy được đúng khoảng ngày cần thay vì tải cả bảng.
 *
 * MÚI GIỜ là chỗ dễ sai nhất: Odoo lưu và trả giờ theo UTC, còn "ngày công" là theo giờ Việt Nam.
 * Một ca chấm 08:00 ngày 05/09 giờ VN nằm ở 01:00 UTC cùng ngày, nhưng ca đêm 23:30 ngày 05/09 giờ VN
 * lại là 16:30 UTC — đọc thô theo UTC sẽ đẩy công sang ngày khác. Mọi mốc thời gian ở đây quy về
 * {@link #VN} ngay khi đọc, và khoảng lọc gửi sang Odoo cũng được đổi ngược lại sang UTC.
 */
@Component
public class OdooAttendanceClient {

    private static final Logger log = LoggerFactory.getLogger(OdooAttendanceClient.class);

    /** Múi giờ dùng để quy đổi "ngày công". */
    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter ODOO_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Trần số bản ghi một lần đọc — chặn việc lỡ tay chọn khoảng 5 năm rồi kéo sập cả hai hệ thống. */
    private static final int MAX_RECORDS = 200_000;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Hỏi ERP xem có những database nào ({@code service=db, method=list}).
     *
     * Nhiều bản Odoo TẮT tính năng này (list_db = False) để không lộ danh sách database ra ngoài —
     * khi đó trả về danh sách rỗng chứ không coi là lỗi, người dùng sẽ tự điền tên database.
     */
    public List<String> listDatabases(String baseUrl) {
        JsonNode res;
        try {
            res = callRaw(baseUrl, params("db", "list", json.createArrayNode()));
        } catch (RuntimeException e) {
            return List.of();
        }
        if (res == null || !res.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode n : res) {
            if (n != null && n.isTextual()) {
                out.add(n.asText());
            }
        }
        return out;
    }

    /**
     * Thử đăng nhập lần lượt vào từng database ứng viên, trả về tên database đăng nhập được.
     *
     * Dùng khi máy chủ không cho liệt kê database: sai tên database thì Odoo báo "database not found"
     * chứ không tính là lần đăng nhập sai, nên thử vài cái tên không làm khoá tài khoản. Trả về null
     * nếu không cái nào vào được.
     */
    public String probeDatabase(String baseUrl, String username, String password, List<String> candidates) {
        for (String db : candidates) {
            if (db == null || db.isBlank()) {
                continue;
            }
            try {
                JsonNode res = callRaw(baseUrl, params("common", "login",
                        arr(db.trim(), username, password)));
                if (res != null && res.isNumber() && res.asLong(0) > 0) {
                    return db.trim();
                }
            } catch (RuntimeException e) {
                // Sai tên database / máy chủ từ chối → thử cái tiếp theo.
                log.debug("[erp] thử database {} không được: {}", db, e.getMessage());
            }
        }
        return null;
    }

    /** Đăng nhập, trả về uid. Ném IllegalArgumentException (→ 400) kèm lý do người dùng hiểu được. */
    public long login(ErpConfig cfg) {
        JsonNode res = call(cfg, params("common", "login",
                arr(cfg.getDbName(), cfg.getUsername(), cfg.getApiKey())));
        if (res == null || res.isNull() || res.isBoolean() && !res.asBoolean()) {
            throw new IllegalArgumentException(
                    "ERP từ chối đăng nhập — kiểm tra lại tên database, tài khoản và API key.");
        }
        long uid = res.asLong(0);
        if (uid <= 0) {
            throw new IllegalArgumentException(
                    "ERP từ chối đăng nhập — kiểm tra lại tên database, tài khoản và API key.");
        }
        return uid;
    }

    /**
     * Đọc chấm công trong khoảng ngày (theo giờ VN, bao gồm cả hai đầu).
     * Đọc theo TRANG 5.000 bản ghi: Odoo mặc định không giới hạn, một tháng của công ty lớn có thể
     * hàng chục nghìn dòng và một cú trả về duy nhất dễ vượt bộ nhớ lẫn thời gian chờ.
     */
    public List<AttendanceRecord> fetchAttendance(ErpConfig cfg, LocalDate from, LocalDate to) {
        long uid = login(cfg);
        String fromUtc = toUtcText(from.atStartOfDay());
        String toUtc = toUtcText(to.plusDays(1).atStartOfDay().minusSeconds(1));

        List<AttendanceRecord> out = new ArrayList<>();
        int offset = 0;
        final int page = 5_000;
        while (true) {
            ArrayNode domain = json.createArrayNode();
            domain.add(triple("check_in", ">=", fromUtc));
            domain.add(triple("check_in", "<=", toUtc));

            ObjectNode kwargs = json.createObjectNode();
            ArrayNode fields = kwargs.putArray("fields");
            fields.add("employee_id");
            fields.add("check_in");
            fields.add("check_out");
            fields.add("worked_hours");
            kwargs.put("limit", page);
            kwargs.put("offset", offset);
            kwargs.put("order", "check_in asc");

            ArrayNode args = json.createArrayNode();
            args.add(cfg.getDbName());
            args.add(uid);
            args.add(cfg.getApiKey());
            args.add("hr.attendance");
            args.add("search_read");
            ArrayNode positional = args.addArray();
            positional.add(domain);
            args.add(kwargs);

            JsonNode rows = call(cfg, params("object", "execute_kw", args));
            if (rows == null || !rows.isArray() || rows.isEmpty()) {
                break;
            }
            for (JsonNode r : rows) {
                AttendanceRecord rec = toRecord(r);
                if (rec != null) {
                    out.add(rec);
                }
            }
            if (rows.size() < page) {
                break;
            }
            offset += page;
            if (out.size() >= MAX_RECORDS) {
                log.warn("[erp] khoảng {}..{} vượt {} bản ghi — cắt bớt", from, to, MAX_RECORDS);
                break;
            }
        }
        return out;
    }

    /**
     * Một dòng hr.attendance → bản ghi nội bộ. Bỏ qua dòng chưa check-out ({@code worked_hours} = 0
     * và không có check_out): đó là ca đang mở, tính vào thì công của hôm nay luôn thiếu.
     */
    private AttendanceRecord toRecord(JsonNode r) {
        JsonNode emp = r.get("employee_id");
        // Odoo trả trường many2one dạng [id, "Tên"]; false nếu trống.
        if (emp == null || !emp.isArray() || emp.size() < 2) {
            return null;
        }
        String checkIn = text(r, "check_in");
        if (checkIn == null) {
            return null;
        }
        boolean open = text(r, "check_out") == null;
        double hours = r.hasNonNull("worked_hours") ? r.get("worked_hours").asDouble(0) : 0;
        if (open && hours <= 0) {
            return null;
        }
        LocalDate workDate = fromUtcText(checkIn).atZone(ZoneId.of("UTC")).withZoneSameInstant(VN).toLocalDate();
        String display = emp.get(1).asText();
        return new AttendanceRecord(emp.get(0).asLong(), nameOf(display), codeOf(display), workDate, hours);
    }

    /**
     * Tên hiển thị của nhân sự bên Odoo có dạng "Đoàn Đình Đức - 4021" — phần đuôi chính là MÃ nhân
     * viên, cùng bộ mã với hồ sơ nhân sự bên này. Mã đáng giá hơn tên nhiều khi đối soát: tên viết
     * hoa/thường/thiếu dấu mỗi nơi một kiểu, còn hai người trùng tên là chuyện xảy ra thật.
     */
    private static final java.util.regex.Pattern NAME_CODE =
            java.util.regex.Pattern.compile("^(.*\\S)\\s*-\\s*([A-Za-z0-9._]{2,20})$");

    public static String codeOf(String display) {
        if (display == null) {
            return null;
        }
        java.util.regex.Matcher m = NAME_CODE.matcher(display.trim());
        return m.matches() ? m.group(2) : null;
    }

    public static String nameOf(String display) {
        if (display == null) {
            return null;
        }
        java.util.regex.Matcher m = NAME_CODE.matcher(display.trim());
        return m.matches() ? m.group(1) : display.trim();
    }

    // ===== JSON-RPC =====

    private ObjectNode params(String service, String method, ArrayNode args) {
        ObjectNode p = json.createObjectNode();
        p.put("service", service);
        p.put("method", method);
        p.set("args", args);
        return p;
    }

    private ArrayNode arr(Object... values) {
        ArrayNode a = json.createArrayNode();
        for (Object v : values) {
            if (v instanceof String s) {
                a.add(s);
            } else if (v instanceof Number n) {
                a.add(n.longValue());
            } else {
                a.addNull();
            }
        }
        return a;
    }

    private ArrayNode triple(String field, String op, String value) {
        ArrayNode t = json.createArrayNode();
        t.add(field);
        t.add(op);
        t.add(value);
        return t;
    }

    private JsonNode call(ErpConfig cfg, ObjectNode params) {
        if (cfg == null || !cfg.isConfigured()) {
            throw new IllegalArgumentException("Chưa khai báo kết nối ERP (URL, database, tài khoản, API key).");
        }
        return callRaw(cfg.getBaseUrl(), params);
    }

    /** Gọi JSON-RPC chỉ với URL — dùng được cả khi chưa lưu cấu hình (lúc dò database). */
    private JsonNode callRaw(String baseUrl, ObjectNode params) {
        ErpConfig cfg = null;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Thiếu URL Odoo.");
        }
        ObjectNode body = json.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", "call");
        body.set("params", params);

        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(baseUrl) + "/jsonrpc"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Không gọi được ERP tại " + baseUrl
                    + " — kiểm tra URL và việc máy chủ PlanX có ra được địa chỉ đó không. (" + e.getMessage() + ")");
        }
        if (res.statusCode() != 200) {
            throw new IllegalArgumentException("ERP trả HTTP " + res.statusCode()
                    + " — URL có đúng là gốc Odoo (không kèm /web/...) không?");
        }
        JsonNode root;
        try {
            root = json.readTree(res.body());
        } catch (Exception e) {
            throw new IllegalArgumentException("ERP trả về nội dung không phải JSON — nhiều khả năng URL "
                    + "trỏ vào trang đăng nhập chứ không phải endpoint /jsonrpc.");
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new IllegalArgumentException("ERP báo lỗi: " + odooMessage(error));
        }
        return root.get("result");
    }

    /** Lấy câu thông báo gọn nhất trong khối lỗi của Odoo (tránh dán cả stack trace lên màn hình). */
    private static String odooMessage(JsonNode error) {
        JsonNode data = error.get("data");
        if (data != null) {
            String msg = data.hasNonNull("message") ? data.get("message").asText() : null;
            if (msg != null && !msg.isBlank()) {
                return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
            }
        }
        return error.hasNonNull("message") ? error.get("message").asText() : error.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.isBoolean() ? null : v.asText();
    }

    /** "https://erp.vmo.dev/" và ".../web#action=148" đều phải quy về gốc "https://erp.vmo.dev". */
    private static String trimSlash(String s) {
        String t = s.trim();
        int hash = t.indexOf('#');
        if (hash > 0) {
            t = t.substring(0, hash);
        }
        int web = t.indexOf("/web");
        if (web > 0) {
            t = t.substring(0, web);
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String toUtcText(LocalDateTime vnTime) {
        return vnTime.atZone(VN).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().format(ODOO_DT);
    }

    private static LocalDateTime fromUtcText(String s) {
        return LocalDateTime.parse(s, ODOO_DT);
    }
}
