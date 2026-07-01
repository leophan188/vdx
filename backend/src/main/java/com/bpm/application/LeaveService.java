package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.leave.LeaveEntry;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.infrastructure.LeaveEntryRepository;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Công cụ Đăng ký NGHỈ.
 * - Cá nhân: register/update/delete — chỉ trên đăng ký của chính mình. KHÔNG có phê duyệt.
 * - Quản lý: tổng hợp theo nhân viên trong khoảng ngày + xuất .xlsx (Apache POI, chống CSV injection).
 * Số ngày nghỉ = số ngày T2–T6 trong khoảng (tự tính).
 */
@Service
public class LeaveService {

    private final LeaveEntryRepository entryRepo;
    private final UserAccountRepository userRepo;
    private final PositionRepository positionRepo;
    private final OrgUnitRepository orgRepo;
    private final AuditPort audit;

    public LeaveService(LeaveEntryRepository entryRepo, UserAccountRepository userRepo,
                        PositionRepository positionRepo, OrgUnitRepository orgRepo, AuditPort audit) {
        this.entryRepo = entryRepo;
        this.userRepo = userRepo;
        this.positionRepo = positionRepo;
        this.orgRepo = orgRepo;
        this.audit = audit;
    }

    // ===================== CRUD của tôi =====================

    @Transactional
    public LeaveEntry register(String actor, LocalDate fromDate, LocalDate toDate, String type, String reason) {
        UserAccount u = user(actor);
        validateRange(fromDate, toDate);
        String t = LeaveEntry.normalizeType(type);
        LeaveEntry e = entryRepo.save(new LeaveEntry(u.getId(), u.getFullName(), orgUnitOf(u.getId()),
                fromDate, toDate, t, trim(reason, 300)));
        audit.record("LEAVE_CREATED", "LeaveEntry", e.getId(), actor,
                "from=" + fromDate + ", to=" + toDate + ", type=" + t + ", days=" + e.getDays());
        return e;
    }

    @Transactional
    public LeaveEntry update(String actor, String id, LocalDate fromDate, LocalDate toDate, String type, String reason) {
        LeaveEntry e = mine(actor, id);
        validateRange(fromDate, toDate);
        e.apply(fromDate, toDate, LeaveEntry.normalizeType(type), trim(reason, 300));
        entryRepo.save(e);
        audit.record("LEAVE_UPDATED", "LeaveEntry", e.getId(), actor,
                "from=" + fromDate + ", to=" + toDate + ", type=" + e.getType() + ", days=" + e.getDays());
        return e;
    }

    @Transactional
    public void delete(String actor, String id) {
        LeaveEntry e = mine(actor, id);
        entryRepo.delete(e);
        audit.record("LEAVE_DELETED", "LeaveEntry", id, actor, "from=" + e.getFromDate() + ", to=" + e.getToDate());
    }

    @Transactional(readOnly = true)
    public List<LeaveEntry> myEntries(String actor) {
        return entryRepo.findByUserIdOrderByFromDateDesc(user(actor).getId());
    }

    // ===================== Tổng hợp theo khoảng ngày =====================

    public record EmployeeSummary(String userId, String userName, String orgUnitId, String orgUnitName,
                                  double annualDays, double unpaidDays, double totalDays, int entryCount) {
    }

    public record Totals(double annualDays, double unpaidDays, double totalDays, int people) {
    }

    public record RangeSummary(LocalDate from, LocalDate to, List<EmployeeSummary> byEmployee, Totals totals) {
    }

    /**
     * Gom các đăng ký GIAO với [from,to]; với mỗi đăng ký tính số ngày nghỉ T2–T6 nằm trong
     * giao [max(from,e.from), min(to,e.to)] → cộng vào nhân sự theo loại.
     */
    @Transactional(readOnly = true)
    public RangeSummary summary(LocalDate from, LocalDate to, String filterOrgUnitId) {
        validateRange(from, to);
        List<LeaveEntry> entries =
                entryRepo.findByFromDateLessThanEqualAndToDateGreaterThanEqual(to, from);
        Map<String, String> orgNames = orgNameMap();

        Map<String, double[]> agg = new LinkedHashMap<>();   // userId -> [annual, unpaid, total, count]
        Map<String, String[]> meta = new LinkedHashMap<>();  // userId -> [userName, orgUnitId]
        for (LeaveEntry e : entries) {
            if (filterOrgUnitId != null && !filterOrgUnitId.isBlank() && !filterOrgUnitId.equals(e.getOrgUnitId())) {
                continue;
            }
            LocalDate effFrom = e.getFromDate().isBefore(from) ? from : e.getFromDate();
            LocalDate effTo = e.getToDate().isAfter(to) ? to : e.getToDate();
            int d = LeaveEntry.workdays(effFrom, effTo);
            if (d <= 0) {
                continue;
            }
            double[] a = agg.computeIfAbsent(e.getUserId(), k -> new double[4]);
            if ("UNPAID".equals(e.getType())) {
                a[1] += d;
            } else {
                a[0] += d;
            }
            a[2] += d;
            a[3] += 1;
            meta.putIfAbsent(e.getUserId(), new String[]{e.getUserName(), e.getOrgUnitId()});
        }

        List<EmployeeSummary> rows = new ArrayList<>();
        double tAnnual = 0, tUnpaid = 0, tTotal = 0;
        for (Map.Entry<String, double[]> en : agg.entrySet()) {
            double[] a = en.getValue();
            String[] m = meta.get(en.getKey());
            String ou = m[1];
            String ouName = ou == null ? "(Chưa có phòng)" : orgNames.getOrDefault(ou, ou);
            rows.add(new EmployeeSummary(en.getKey(), m[0], ou, ouName,
                    round(a[0]), round(a[1]), round(a[2]), (int) a[3]));
            tAnnual += a[0];
            tUnpaid += a[1];
            tTotal += a[2];
        }
        Totals totals = new Totals(round(tAnnual), round(tUnpaid), round(tTotal), rows.size());
        return new RangeSummary(from, to, rows, totals);
    }

    // ===================== Xuất .xlsx =====================

    @Transactional(readOnly = true)
    public byte[] exportXlsx(LocalDate from, LocalDate to, String filterOrgUnitId) {
        RangeSummary s = summary(from, to, filterOrgUnitId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            Sheet sheet = wb.createSheet("Tổng hợp nghỉ");

            Row title = sheet.createRow(0);
            setText(title, 0, "Tổng hợp nghỉ phép từ " + from + " đến " + to);

            int hr = 2;
            writeHeader(sheet, hr, header, "Mã NV/Tên", "Bộ phận", "Phép năm (ngày)",
                    "Không lương (ngày)", "Tổng (ngày)", "Số đơn");
            int r = hr + 1;
            for (EmployeeSummary e : s.byEmployee()) {
                Row row = sheet.createRow(r++);
                setText(row, 0, e.userName());
                setText(row, 1, e.orgUnitName());
                setNumber(row, 2, e.annualDays());
                setNumber(row, 3, e.unpaidDays());
                setNumber(row, 4, e.totalDays());
                setNumber(row, 5, e.entryCount());
            }
            Row total = sheet.createRow(r);
            Cell tc = total.createCell(0);
            tc.setCellValue("TỔNG");
            tc.setCellStyle(header);
            setNumber(total, 2, s.totals().annualDays());
            setNumber(total, 3, s.totals().unpaidDays());
            setNumber(total, 4, s.totals().totalDays());
            setNumber(total, 5, s.totals().people());

            autoSize(sheet, 6);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được file Excel nghỉ phép: " + ex.getMessage(), ex);
        }
    }

    // ===================== helpers =====================

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Thiếu ngày bắt đầu/kết thúc");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc");
        }
    }

    private UserAccount user(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + username));
    }

    private LeaveEntry mine(String actor, String id) {
        LeaveEntry e = entryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đăng ký nghỉ"));
        UserAccount u = user(actor);
        if (!u.getId().equals(e.getUserId())) {
            throw new IllegalArgumentException("Chỉ được sửa/xoá đăng ký nghỉ của chính mình");
        }
        return e;
    }

    /** Phòng/đơn vị của user: lấy theo vị trí đang giữ (vị trí đầu tiên có orgUnit). */
    private String orgUnitOf(String userId) {
        for (Position p : positionRepo.findByCurrentHolderUserId(userId)) {
            if (p.getOrgUnitId() != null) {
                return p.getOrgUnitId();
            }
        }
        return null;
    }

    private Map<String, String> orgNameMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (OrgUnit o : orgRepo.findAll()) {
            m.put(o.getId(), o.getName());
        }
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ---- POI helpers ----

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static void writeHeader(Sheet sheet, int rowIdx, CellStyle style, String... titles) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]); // tiêu đề cố định, an toàn
            c.setCellStyle(style);
        }
    }

    private static void setText(Row row, int col, String value) {
        row.createCell(col).setCellValue(neutralize(value));
    }

    private static void setNumber(Row row, int col, double value) {
        row.createCell(col).setCellValue(value);
    }

    private static void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Trung hoà formula/CSV injection: nếu ô text bắt đầu bằng = + - @ (hoặc tab/CR),
     * chèn dấu nháy đơn đứng trước để Excel coi là văn bản thuần.
     */
    static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
