package com.bpm.application;

import com.bpm.domain.erp.AttendanceRecord;
import com.bpm.domain.erp.CustomerWorkdayEntry;
import com.bpm.domain.erp.ErpAttendanceEntry;
import com.bpm.domain.erp.ErpConfig;
import com.bpm.domain.erp.WorkdayReconciliation;
import com.bpm.domain.report.ExcelReportEngine;
import com.bpm.domain.report.ReportTemplate;
import com.bpm.domain.report.SafeWorkbookReader;
import com.bpm.domain.report.ValidationResult;
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
    @Transactional
    public String testConnection(String actor) {
        ErpConfig cfg = config();
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
            byPerson.computeIfAbsent(e.getMatchKey(), k -> new ErpAgg(e.getEmployeeName())).add(e);
        }
        List<ErpPersonRow> out = new ArrayList<>();
        for (Map.Entry<String, ErpAgg> en : byPerson.entrySet()) {
            ErpAgg a = en.getValue();
            out.add(new ErpPersonRow(en.getKey(), a.name, WorkdayReconciliation.round2(a.hours),
                    WorkdayReconciliation.toDays(a.hours), a.dates.size()));
        }
        out.sort(Comparator.comparing(ErpPersonRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
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

    /** Kiểm tra file trước khi lưu — dùng chung bộ validate của Công cụ Excel. */
    public ValidationResult validateCustomerFile(byte[] bytes, String fileName) {
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            return ExcelReportEngine.validate(ReportTemplate.CONG_KHACH_HANG, wb);
        } catch (SafeWorkbookReader.UnsafeFileException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file: " + e.getMessage());
        }
    }

    /**
     * Nhận file công khách hàng cho một kỳ và THAY toàn bộ dữ liệu kỳ đó — bản gửi sau của khách hàng
     * là bản có hiệu lực.
     */
    @Transactional
    public int importCustomer(String periodKey, byte[] bytes, String fileName, String actor) {
        YearMonth ym = parsePeriod(periodKey);
        ValidationResult v = validateCustomerFile(bytes, fileName);
        if (!v.isValid()) {
            throw new IllegalArgumentException("File có lỗi, chưa import: " + v.getIssues().size()
                    + " lỗi — xem lại rồi gửi bản đúng, dữ liệu kỳ cũ giữ nguyên.");
        }
        List<CustomerWorkdayEntry> rows = new ArrayList<>();
        try (Workbook wb = SafeWorkbookReader.open(bytes, fileName)) {
            for (ExcelReportEngine.InputRow r : ExcelReportEngine.readRows(ReportTemplate.CONG_KHACH_HANG, wb)) {
                String name = str(r.values().get("Họ và tên"));
                if (name == null || name.isBlank()) {
                    continue;
                }
                Double days = num(r.values().get("Số công"));
                rows.add(new CustomerWorkdayEntry(ym.toString(), str(r.values().get("Mã NV")), name.trim(),
                        WorkdayReconciliation.matchKey(name), days == null ? 0d : days,
                        str(r.values().get("Ghi chú")), fileName, actor));
            }
        } catch (SafeWorkbookReader.UnsafeFileException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file: " + e.getMessage());
        }
        customerRepo.deleteByPeriodKey(ym.toString());
        customerRepo.saveAll(rows);
        auditPort.record("CUSTOMER_WORKDAY_IMPORTED", "CustomerWorkday", ym.toString(), actor,
                "file=" + fileName + ", rows=" + rows.size());
        log.info("[erp] kỳ {} ← file khách hàng {}: {} dòng", ym, fileName, rows.size());
        return rows.size();
    }

    @Transactional(readOnly = true)
    public List<CustomerWorkdayEntry> customerRows(String periodKey) {
        List<CustomerWorkdayEntry> rows = customerRepo.findByPeriodKey(parsePeriod(periodKey).toString());
        rows.sort(Comparator.comparing(CustomerWorkdayEntry::getEmployeeName, String.CASE_INSENSITIVE_ORDER));
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
        Map<String, ErpPersonRow> erp = new LinkedHashMap<>();
        for (ErpPersonRow r : erpRows(period)) {
            erp.put(r.matchKey(), r);
        }
        Map<String, CustAgg> cust = new LinkedHashMap<>();
        for (CustomerWorkdayEntry c : customerRepo.findByPeriodKey(period)) {
            cust.computeIfAbsent(c.getMatchKey(), k -> new CustAgg(c.getEmployeeName(), c.getEmpCode()))
                    .add(c.getDays());
        }

        List<WorkdayReconciliation.Row> out = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(erp.keySet());
        keys.addAll(cust.keySet());
        for (String key : keys) {
            ErpPersonRow e = erp.get(key);
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
                    e != null ? e.name() : c.name, c != null ? c.empCode : null,
                    e == null ? 0 : e.hours(), erpDays, e == null ? 0 : e.dayCount(),
                    custDays, diff, status));
        }
        // Đưa việc cần xử lý lên trước: lệch nhiều nhất trước, dòng khớp xuống cuối.
        out.sort(Comparator
                .comparingInt((WorkdayReconciliation.Row r) ->
                        r.status() == WorkdayReconciliation.Status.MATCHED ? 1 : 0)
                .thenComparing(r -> -Math.abs(r.diffDays()))
                .thenComparing(WorkdayReconciliation.Row::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ===== phụ trợ =====

    /** Tổng theo người phía ERP. */
    public record ErpPersonRow(String matchKey, String name, double hours, double days, int dayCount) {
    }

    private static final class ErpAgg {
        private final String name;
        private double hours;
        private final TreeSet<LocalDate> dates = new TreeSet<>();

        private ErpAgg(String name) {
            this.name = name;
        }

        private void add(ErpAttendanceEntry e) {
            hours += e.getHours();
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
