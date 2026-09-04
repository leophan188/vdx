package com.bpm.application;

import com.bpm.domain.erp.AttendanceRecord;
import com.bpm.domain.erp.CustomerWorkdaySheet;
import com.bpm.domain.erp.CustomerWorkdayEntry;
import com.bpm.domain.erp.ErpAttendanceEntry;
import com.bpm.domain.erp.ErpConfig;
import com.bpm.domain.erp.WorkdayReconciliation;
import com.bpm.domain.report.SafeWorkbookReader;
import com.bpm.infrastructure.erp.CustomerWorkdayRepository;
import com.bpm.infrastructure.erp.ErpAttendanceRepository;
import com.bpm.infrastructure.erp.ErpConfigRepository;
import com.bpm.infrastructure.erp.OdooAttendanceClient;
import com.bpm.domain.audit.AuditPort;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Kiểm soát giờ công: đọc chấm công từ ERP, nhận công khách hàng ghi nhận từ file Excel, rồi đối soát
 * hai nguồn theo từng tháng.
 *
 * Cả hai nguồn đều LƯU LẠI theo kỳ thay vì tính tạm mỗi lần mở màn hình. Đối soát là việc phải giải
 * trình được về sau ("số này chốt lúc nào, từ bản gửi nào"), mà số bên ERP thì đổi bất cứ lúc nào có
 * người sửa chấm công muộn.
 */
@Service
public class ErpTimesheetService {

    private static final Logger log = LoggerFactory.getLogger(ErpTimesheetService.class);

    private final ErpConfigRepository configRepo;
    private final ErpAttendanceRepository attendanceRepo;
    private final CustomerWorkdayRepository customerRepo;
    private final OdooAttendanceClient odoo;
    private final AuditPort auditPort;

    public ErpTimesheetService(ErpConfigRepository configRepo, ErpAttendanceRepository attendanceRepo,
                               CustomerWorkdayRepository customerRepo, OdooAttendanceClient odoo,
                               AuditPort auditPort) {
        this.configRepo = configRepo;
        this.attendanceRepo = attendanceRepo;
        this.customerRepo = customerRepo;
        this.odoo = odoo;
        this.auditPort = auditPort;
    }

    // ===== Cấu hình kết nối =====

    @Transactional(readOnly = true)
    public ErpConfig config() {
        return configRepo.findById("default").orElseGet(ErpConfig::new);
    }

    @Transactional
    public ErpConfig saveConfig(String baseUrl, String dbName, String username, String apiKey, String actor) {
        ErpConfig cfg = configRepo.findById("default").orElseGet(ErpConfig::new);
        cfg.update(baseUrl, dbName, username, apiKey, actor);
        ErpConfig saved = configRepo.save(cfg);
        auditPort.record("ERP_CONFIG_UPDATED", "ErpConfig", "default", actor,
                "baseUrl=" + saved.getBaseUrl() + ", db=" + saved.getDbName() + ", user=" + saved.getUsername());
        return saved;
    }

    /** Thử đăng nhập ERP; ghi lại kết quả để màn hình nói được lần kiểm tra gần nhất ra sao. */
    /**
     * Thử đăng nhập bằng thông tin ĐANG GÕ trên form; ô nào bỏ trống thì lấy giá trị đã lưu.
     *
     * Trước đây hàm này chỉ đọc cấu hình trong CSDL, nên bấm "Kiểm tra kết nối" mà chưa bấm "Lưu" thì
     * nhận đúng câu "Chưa khai báo kết nối ERP" dù bốn ô trên màn đã điền đủ — người dùng không hiểu
     * mình sai ở đâu. Kiểm tra trước rồi mới lưu mới là thứ tự tự nhiên.
     */
    @Transactional
    public String testConnection(String baseUrl, String dbName, String username, String password, String actor) {
        ErpConfig cfg = config();
        if (!blank(baseUrl) || !blank(dbName) || !blank(username) || !blank(password)) {
            ErpConfig probe = new ErpConfig();
            probe.update(blank(baseUrl) ? cfg.getBaseUrl() : baseUrl,
                    blank(dbName) ? cfg.getDbName() : dbName,
                    blank(username) ? cfg.getUsername() : username,
                    blank(password) ? cfg.getApiKey() : password, actor);
            long uid = odoo.login(probe);
            return "OK — đăng nhập thành công (uid=" + uid + "). Bấm Lưu kết nối để dùng cho các kỳ sau.";
        }
        try {
            long uid = odoo.login(cfg);
            String ok = "OK — đăng nhập thành công (uid=" + uid + ")";
            cfg.markChecked(ok);
            configRepo.save(cfg);
            auditPort.record("ERP_CONNECTION_TESTED", "ErpConfig", "default", actor, ok);
            return ok;
        } catch (RuntimeException e) {
            cfg.markChecked("LỖI — " + e.getMessage());
            configRepo.save(cfg);
            throw e;
        }
    }

    /**
     * Dò tên database: hỏi thẳng ERP trước, không được thì thử đăng nhập vào vài cái tên suy ra từ
     * chính địa chỉ máy chủ.
     *
     * Người dùng thường không biết tên database — đó là thứ của người quản trị ERP, không hiện ở đâu
     * trong giao diện thường ngày. Bắt họ đi hỏi rồi mới dùng được màn hình là cách chắc chắn để tính
     * năng nằm im không ai đụng tới.
     */
    @Transactional(readOnly = true)
    public DbProbe detectDatabase(String baseUrl, String username, String password) {
        ErpConfig saved = config();
        String url = blank(baseUrl) ? saved.getBaseUrl() : baseUrl;
        String user = blank(username) ? saved.getUsername() : username;
        String pass = blank(password) ? saved.getApiKey() : password;

        List<String> listed = odoo.listDatabases(url);
        if (listed.size() == 1) {
            return new DbProbe(listed.get(0), listed, "Máy chủ chỉ có một database.");
        }
        if (!listed.isEmpty()) {
            String hit = (user == null || pass == null) ? null : odoo.probeDatabase(url, user, pass, listed);
            return new DbProbe(hit, listed, hit != null
                    ? "Đăng nhập được vào database này."
                    : "Máy chủ có nhiều database — chọn một rồi bấm Kiểm tra kết nối.");
        }
        if (user == null || pass == null) {
            return new DbProbe(null, List.of(),
                    "Máy chủ không cho liệt kê database. Điền tài khoản và mật khẩu rồi dò lại.");
        }
        String hit = odoo.probeDatabase(url, user, pass, candidateNames(url));
        return new DbProbe(hit, List.of(), hit != null
                ? "Đăng nhập được vào database này."
                : "Không đoán được tên database. Hỏi người quản trị ERP, hoặc mở ERP trên trình duyệt, "
                        + "bấm F12 → tab Console, gõ odoo.info.db rồi chép kết quả vào ô Database.");
    }

    /** Kết quả dò: tên tìm được (nếu có), danh sách máy chủ công bố, và câu giải thích cho người dùng. */
    public record DbProbe(String database, List<String> options, String message) {
    }

    /**
     * Các tên database hay gặp, suy từ chính địa chỉ: "https://erp.vmo.dev" → erp.vmo.dev, erp_vmo_dev,
     * erp-vmo-dev, erp, vmo… Thử vài cái rẻ hơn nhiều so với chờ người quản trị ERP trả lời.
     */
    private static List<String> candidateNames(String baseUrl) {
        String host = baseUrl == null ? "" : baseUrl.replaceAll("^https?://", "").replaceAll("[/:].*$", "");
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (!host.isBlank()) {
            out.add(host);
            out.add(host.replace('.', '_'));
            out.add(host.replace('.', '-'));
            String[] parts = host.split("\\.");
            if (parts.length > 0) {
                out.add(parts[0]);
            }
            if (parts.length > 1) {
                out.add(parts[1]);
                out.add(parts[0] + "_" + parts[1]);
                out.add(parts[0] + "-" + parts[1]);
            }
        }
        out.add("odoo");
        out.add("production");
        out.add("prod");
        return List.copyOf(out);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ===== Nguồn 1: chấm công ERP =====

    /**
     * Tải chấm công một kỳ từ ERP và THAY toàn bộ dữ liệu kỳ đó.
     * Thay chứ không cộng thêm: gọi lại lần hai mà cộng dồn thì số công gấp đôi, và không ai nhìn ra
     * vì từng dòng đều hợp lệ.
     */
    @Transactional
    public int syncPeriod(String periodKey, String actor) {
        YearMonth ym = parsePeriod(periodKey);
        ErpConfig cfg = config();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<AttendanceRecord> records = odoo.fetchAttendance(cfg, from, to);
        attendanceRepo.deleteByPeriodKey(ym.toString());
        List<ErpAttendanceEntry> rows = new ArrayList<>(records.size());
        for (AttendanceRecord r : records) {
            rows.add(new ErpAttendanceEntry(ym.toString(), r,
                    WorkdayReconciliation.matchKey(r.employeeName()), actor));
        }
        attendanceRepo.saveAll(rows);

        cfg.markChecked("OK — tải kỳ " + ym + ": " + rows.size() + " lần chấm công");
        configRepo.save(cfg);
        auditPort.record("ERP_ATTENDANCE_SYNCED", "ErpAttendance", ym.toString(), actor,
                "records=" + rows.size());
        log.info("[erp] kỳ {} → {} bản ghi chấm công", ym, rows.size());
        return rows.size();
    }

    /** Tổng theo người của một kỳ (đọc từ dữ liệu đã lưu, KHÔNG gọi lại ERP). */
    @Transactional(readOnly = true)
    public List<ErpPersonRow> erpRows(String periodKey) {
        Map<String, ErpAgg> byPerson = new LinkedHashMap<>();
        for (ErpAttendanceEntry e : attendanceRepo.findByPeriodKey(parsePeriod(periodKey).toString())) {
            String key = keyOf(e.getEmpCode(), e.getMatchKey());
            byPerson.computeIfAbsent(key, k -> new ErpAgg(e.getEmployeeName(), e.getEmpCode())).add(e);
        }
        List<ErpPersonRow> out = new ArrayList<>();
        for (Map.Entry<String, ErpAgg> en : byPerson.entrySet()) {
            ErpAgg a = en.getValue();
            out.add(new ErpPersonRow(en.getKey(), a.name, a.empCode, WorkdayReconciliation.round2(a.hours),
                    WorkdayReconciliation.round2(a.workdays), a.dates.size()));
        }
        out.sort(Comparator.comparing(ErpPersonRow::empCode, CODE_ORDER)
                .thenComparing(ErpPersonRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Bảng công theo NGÀY: mỗi nhân sự một dòng, mỗi ngày trong tháng một cột.
     *
     * Bảng tổng theo người trả lời "tháng này ai bao nhiêu công", còn bảng này trả lời "lệch nằm ở
     * ngày nào" — câu hỏi tiếp theo ngay sau khi thấy một dòng lệch, mà tổng tháng thì chịu.
     */
    @Transactional(readOnly = true)
    public PivotResult pivot(String periodKey, boolean customer) {
        YearMonth ym = parsePeriod(periodKey);
        int days = ym.lengthOfMonth();
        Map<String, PivotAgg> byPerson = new LinkedHashMap<>();
        // Bản ghi tải bằng phiên bản trước KHÔNG có ngày công (cột mới, giá trị NULL) nên mọi ô hiện 0.
        // Đếm lại để màn hình nói thẳng "kỳ này cần tải lại" thay vì bày ra một bảng số 0 khó hiểu.
        int stale = 0;
        if (customer) {
            for (CustomerWorkdayEntry c : customerRepo.findByPeriodKey(ym.toString())) {
                if (c.getWorkDate() == null) {
                    continue;   // dòng tổng tháng kiểu cũ: không đặt được vào cột ngày nào
                }
                PivotAgg agg = byPerson.computeIfAbsent(keyOf(c.getEmpCode(), c.getMatchKey()),
                        k -> new PivotAgg(c.getEmployeeName(), c.getEmpCode()));
                if (agg.note == null && c.getNote() != null) {
                    agg.note = c.getNote();   // hạng mục / dự án khách hàng ghi cho người này
                }
                agg.byDay.merge(c.getWorkDate().getDayOfMonth(), c.getDays(), Double::sum);
            }
        } else {
            for (ErpAttendanceEntry e : attendanceRepo.findByPeriodKey(ym.toString())) {
                if (!e.hasWorkday()) {
                    stale++;
                }
                PivotAgg agg = byPerson.computeIfAbsent(keyOf(e.getEmpCode(), e.getMatchKey()),
                        k -> new PivotAgg(e.getEmployeeName(), e.getEmpCode()));
                // Cùng một ngày có thể có nhiều bản ghi — cộng dồn, đừng ghi đè.
                agg.byDay.merge(e.getWorkDate().getDayOfMonth(), e.getPayWorkday(), Double::sum);
                agg.hoursByDay.merge(e.getWorkDate().getDayOfMonth(), e.getHours(), Double::sum);
            }
        }
        List<PivotRow> rows = new ArrayList<>();
        for (PivotAgg a : byPerson.values()) {
            double total = 0;
            Map<Integer, Double> cells = new LinkedHashMap<>();
            for (Map.Entry<Integer, Double> c : a.byDay.entrySet()) {
                double v = WorkdayReconciliation.round2(c.getValue());
                cells.put(c.getKey(), v);
                total += v;
            }
            Map<Integer, Double> hours = new LinkedHashMap<>();
            for (Map.Entry<Integer, Double> c : a.hoursByDay.entrySet()) {
                hours.put(c.getKey(), WorkdayReconciliation.round2(c.getValue()));
            }
            rows.add(new PivotRow(a.name, a.empCode, a.note, cells, hours,
                    WorkdayReconciliation.round2(total), cells.size()));
        }
        rows.sort(Comparator.comparing(PivotRow::empCode, CODE_ORDER)
                .thenComparing(PivotRow::name, String.CASE_INSENSITIVE_ORDER));

        // Thứ Bảy/Chủ nhật đánh dấu sẵn ở backend để màn hình khỏi tự suy ra lịch — chấm công cuối
        // tuần là chuyện đáng chú ý, phải nhìn ra ngay giữa một rừng con số.
        List<Integer> weekend = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            var dow = ym.atDay(d).getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                weekend.add(d);
            }
        }
        return new PivotResult(ym.toString(), days, weekend, rows, stale > 0);
    }

    /**
     * @param daysByDay  NGÀY CÔNG theo ngày trong tháng (1..31) — 1 hoặc 0,5; ngày vắng mặt = nghỉ
     * @param hoursByDay số giờ tương ứng, để xem khi rê chuột (chỉ có ở nguồn ERP)
     */
    public record PivotRow(String name, String empCode, String note, Map<Integer, Double> daysByDay,
                           Map<Integer, Double> hoursByDay, double totalDays, int dayCount) {
    }

    /** @param stale dữ liệu kỳ này tải bằng phiên bản cũ, thiếu ngày công → cần tải lại từ ERP */
    public record PivotResult(String period, int daysInMonth, List<Integer> weekendDays,
                              List<PivotRow> rows, boolean stale) {
    }

    private static final class PivotAgg {
        private final String name;
        private final String empCode;
        private String note;
        private final Map<Integer, Double> byDay = new java.util.TreeMap<>();
        private final Map<Integer, Double> hoursByDay = new java.util.TreeMap<>();

        private PivotAgg(String name, String empCode) {
            this.name = name;
            this.empCode = empCode;
        }
    }

    /** Các kỳ đã có dữ liệu ở bất kỳ nguồn nào — để màn hình gợi ý tháng. */
    @Transactional(readOnly = true)
    public List<String> periods() {
        TreeSet<String> all = new TreeSet<>(Comparator.reverseOrder());
        all.addAll(attendanceRepo.findPeriods());
        all.addAll(customerRepo.findPeriods());
        return new ArrayList<>(all);
    }

    // ===== Nguồn 2: công khách hàng =====

    /** Biểu mẫu trống cho một kỳ — số cột ngày đúng bằng số ngày của tháng đó. */
    public byte[] customerTemplate(String periodKey) {
        return CustomerWorkdaySheet.writeTemplate(parsePeriod(periodKey));
    }

    /**
     * Nhận file công khách hàng cho một kỳ và THAY toàn bộ dữ liệu kỳ đó — bản gửi sau của khách hàng
     * là bản có hiệu lực.
     */
    @Transactional
    public int importCustomer(String periodKey, byte[] bytes, String fileName, String actor) {
        YearMonth ym = parsePeriod(periodKey);
        CustomerWorkdaySheet.ParseResult parsed;
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            parsed = CustomerWorkdaySheet.read(wb, ym);
        } catch (SafeWorkbookReader.UnsafeFileException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file: " + e.getMessage());
        }
        if (parsed.cells().isEmpty()) {
            String why = parsed.problems().isEmpty() ? "" : " (" + parsed.problems().get(0) + ")";
            throw new IllegalArgumentException("Không đọc được dòng công nào trong file" + why
                    + ". Dữ liệu kỳ cũ giữ nguyên.");
        }
        List<CustomerWorkdayEntry> rows = new ArrayList<>();
        for (CustomerWorkdaySheet.Cellule c : parsed.cells()) {
            rows.add(new CustomerWorkdayEntry(ym.toString(), c.date(), c.empCode(), c.name(),
                    WorkdayReconciliation.matchKey(c.name()), c.days(), c.note(), fileName, actor));
        }
        customerRepo.deleteByPeriodKey(ym.toString());
        customerRepo.saveAll(rows);

        // Tóm tắt để người import ĐỐI CHIẾU NGAY với file đang mở: đọc được bao nhiêu người, tổng bao
        // nhiêu công, từ ngày nào tới ngày nào. Chỉ nói "đã lưu 320 dòng" thì file bị cắt hụt nửa bảng
        // hay lẫn bảng ngoài giờ đều trông y hệt nhau.
        java.util.Set<String> people = new java.util.LinkedHashSet<>();
        double totalDays = 0;
        LocalDate min = null;
        LocalDate max = null;
        for (CustomerWorkdayEntry e : rows) {
            people.add(e.getMatchKey());
            totalDays += e.getDays();
            if (min == null || e.getWorkDate().isBefore(min)) {
                min = e.getWorkDate();
            }
            if (max == null || e.getWorkDate().isAfter(max)) {
                max = e.getWorkDate();
            }
        }
        String summary = "sheet \"" + parsed.sheetName() + "\" · "
                + people.size() + " nhân sự · " + WorkdayReconciliation.round2(totalDays)
                + " công · ngày " + (min == null ? "?" : min.getDayOfMonth())
                + "–" + (max == null ? "?" : max.getDayOfMonth());
        auditPort.record("CUSTOMER_WORKDAY_IMPORTED", "CustomerWorkday", ym.toString(), actor,
                "file=" + fileName + ", rows=" + rows.size() + ", " + summary);
        log.info("[erp] kỳ {} ← file khách hàng {}: {} ô ({})", ym, fileName, rows.size(), summary);
        lastImportSummary = summary;
        return rows.size();
    }

    /** Tóm tắt lần import gần nhất — controller ghép vào câu thông báo trả về màn hình. */
    private String lastImportSummary = "";

    public String lastImportSummary() {
        return lastImportSummary;
    }

    @Transactional(readOnly = true)
    public List<CustomerWorkdayEntry> customerRows(String periodKey) {
        List<CustomerWorkdayEntry> rows = customerRepo.findByPeriodKey(parsePeriod(periodKey).toString());
        rows.sort(Comparator.comparing(CustomerWorkdayEntry::getEmpCode, CODE_ORDER)
                .thenComparing(CustomerWorkdayEntry::getEmployeeName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    // ===== Đối soát =====

    /**
     * Ghép hai nguồn theo tên đã chuẩn hoá. Người chỉ có ở một bên VẪN xuất hiện trong bảng — họ mới
     * là trường hợp đáng xem nhất (khách hàng quên ghi, hoặc có người chấm công mà khách hàng không
     * ghi nhận), lọc bỏ đi thì bảng đối soát trông đẹp mà vô dụng.
     */
    @Transactional(readOnly = true)
    public List<WorkdayReconciliation.Row> reconcile(String periodKey) {
        String period = parsePeriod(periodKey).toString();
        // Bên ERP đánh chỉ mục theo CẢ HAI khoá: mã và tên. File khách hàng có mã thì ghép theo mã;
        // chỉ có tên thì vẫn ghép được — bắt buộc phải có mã mới đối soát nổi là đặt điều kiện lên
        // thứ khách hàng không nợ mình.
        Map<String, ErpPersonRow> erpByKey = new LinkedHashMap<>();
        Map<String, ErpPersonRow> erpByName = new LinkedHashMap<>();
        for (ErpPersonRow r : erpRows(period)) {
            erpByKey.put(r.matchKey(), r);
            erpByName.putIfAbsent(WorkdayReconciliation.matchKey(r.name()), r);
        }
        Map<String, CustAgg> cust = new LinkedHashMap<>();
        for (CustomerWorkdayEntry c : customerRepo.findByPeriodKey(period)) {
            ErpPersonRow hit = erpByKey.get(keyOf(c.getEmpCode(), c.getMatchKey()));
            if (hit == null) {
                hit = erpByName.get(c.getMatchKey());
            }
            String key = hit != null ? hit.matchKey() : keyOf(c.getEmpCode(), c.getMatchKey());
            cust.computeIfAbsent(key, k -> new CustAgg(c.getEmployeeName(), c.getEmpCode()))
                    .add(c.getDays());
        }

        List<WorkdayReconciliation.Row> out = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(erpByKey.keySet());
        keys.addAll(cust.keySet());
        for (String key : keys) {
            ErpPersonRow e = erpByKey.get(key);
            CustAgg c = cust.get(key);
            double erpDays = e == null ? 0 : e.days();
            double custDays = c == null ? 0 : WorkdayReconciliation.round2(c.days);
            double diff = WorkdayReconciliation.round2(erpDays - custDays);
            WorkdayReconciliation.Status status;
            if (e == null) {
                status = WorkdayReconciliation.Status.CUSTOMER_ONLY;
            } else if (c == null) {
                status = WorkdayReconciliation.Status.ERP_ONLY;
            } else {
                status = Math.abs(diff) <= WorkdayReconciliation.TOLERANCE_DAYS
                        ? WorkdayReconciliation.Status.MATCHED : WorkdayReconciliation.Status.DIFF;
            }
            out.add(new WorkdayReconciliation.Row(key,
                    e != null ? e.name() : c.name,
                    e != null && e.empCode() != null ? e.empCode() : (c != null ? c.empCode : null),
                    e == null ? 0 : e.hours(), erpDays, e == null ? 0 : e.dayCount(),
                    custDays, diff, status));
        }
        // Theo MÃ nhân viên, giống hai bảng nguồn — ba bảng cùng thứ tự thì dò ngang giữa các tab mới
        // nhanh. Muốn xem việc cần xử lý trước thì bấm cột "Lệch" để sắp lại.
        out.sort(Comparator.comparing(WorkdayReconciliation.Row::empCode, CODE_ORDER)
                .thenComparing(WorkdayReconciliation.Row::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ===== phụ trợ =====

    /** Tổng theo người phía ERP. */
    public record ErpPersonRow(String matchKey, String name, String empCode,
                               double hours, double days, int dayCount) {
    }

    /**
     * Sắp theo MÃ nhân viên: mã toàn số so theo SỐ, còn lại so theo chuỗi, người chưa có mã xuống cuối.
     * So thuần chuỗi thì "10" đứng trước "9", còn đẩy người chưa có mã lên đầu bảng thì phần đáng đọc
     * bị dồn xuống dưới.
     */
    private static final Comparator<String> CODE_ORDER = (a, b) -> {
        boolean ba = a == null || a.isBlank();
        boolean bb = b == null || b.isBlank();
        if (ba || bb) {
            return ba && bb ? 0 : (ba ? 1 : -1);
        }
        Long na = asLong(a);
        Long nb = asLong(b);
        if (na != null && nb != null) {
            // Cùng phần số ("3982" và "3982.1") thì so tiếp cả chuỗi, nếu không thứ tự hai dòng đó
            // đảo qua đảo lại giữa các lần tải và người dùng tưởng dữ liệu thay đổi.
            int byNumber = Long.compare(na, nb);
            return byNumber != 0 ? byNumber : a.compareToIgnoreCase(b);
        }
        if (na != null || nb != null) {
            return na != null ? -1 : 1;   // mã số trước, mã có chữ sau
        }
        return a.compareToIgnoreCase(b);
    };

    /** Phần số ở đầu mã ("3982.1" → 3982); null nếu không bắt đầu bằng số. */
    private static Long asLong(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,18})").matcher(s.trim());
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    /**
     * Khoá ghép: ưu tiên MÃ nhân viên, không có mã mới lùi về tên đã chuẩn hoá.
     * ERP ghi tên kèm mã ("Đoàn Đình Đức - 4021") và file khách hàng cũng thường có cột mã, nên phần
     * lớn dòng ghép được chính xác; tên chỉ là phương án dự phòng vì trùng tên là chuyện có thật.
     */
    private static String keyOf(String empCode, String nameKey) {
        return empCode != null && !empCode.isBlank() ? "#" + empCode.trim().toLowerCase() : nameKey;
    }

    private static final class ErpAgg {
        private final String name;
        private final String empCode;
        private double hours;
        /** Tổng NGÀY CÔNG do ERP tính — con số dùng để đối soát. */
        private double workdays;
        private final TreeSet<LocalDate> dates = new TreeSet<>();

        private ErpAgg(String name, String empCode) {
            this.name = name;
            this.empCode = empCode;
        }

        private void add(ErpAttendanceEntry e) {
            hours += e.getHours();
            workdays += e.getPayWorkday();
            dates.add(e.getWorkDate());
        }
    }

    private static final class CustAgg {
        private final String name;
        private final String empCode;
        private double days;

        private CustAgg(String name, String empCode) {
            this.name = name;
            this.empCode = empCode;
        }

        private void add(double d) {
            days += d;
        }
    }

    /** "2026-09" → YearMonth; sai định dạng thì báo rõ thay vì ném NPE ở tầng dưới. */
    private static YearMonth parsePeriod(String periodKey) {
        try {
            return YearMonth.parse(periodKey == null ? "" : periodKey.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Kỳ không hợp lệ (cần dạng yyyy-MM, vd 2026-09): " + periodKey);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }
}
