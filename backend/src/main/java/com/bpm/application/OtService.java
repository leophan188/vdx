package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.ot.OtEntry;
import com.bpm.domain.ot.OtPeriod;
import com.bpm.domain.position.Position;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.OtEntryRepository;
import com.bpm.infrastructure.OtPeriodRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Công cụ Đăng ký OT (Epic 3).
 * - FR-C01/C02: nhân viên register/update/delete — chỉ của chính mình, chỉ khi kỳ chưa chốt.
 * - FR-C03: tổng hợp theo nhân viên / phòng / kỳ.
 * - FR-C05: admin chốt kỳ → khoá chỉnh sửa (+ audit qua AuditPort).
 * - FR-C04 + NFR-09: xuất .xlsx bằng Apache POI, trung hoà công thức/CSV injection.
 * Hexagonal-lite: mọi mutation phát audit qua AuditPort.
 */
@Service
public class OtService {

    private final OtEntryRepository entryRepo;
    private final OtPeriodRepository periodRepo;
    private final UserAccountRepository userRepo;
    private final PositionRepository positionRepo;
    private final OrgUnitRepository orgRepo;
    private final AuditPort audit;

    public OtService(OtEntryRepository entryRepo, OtPeriodRepository periodRepo, UserAccountRepository userRepo,
                     PositionRepository positionRepo, OrgUnitRepository orgRepo, AuditPort audit) {
        this.entryRepo = entryRepo;
        this.periodRepo = periodRepo;
        this.userRepo = userRepo;
        this.positionRepo = positionRepo;
        this.orgRepo = orgRepo;
        this.audit = audit;
    }

    // ===================== CRUD của tôi (Story 3.1, FR-C01/C02) =====================

    @Transactional
    public OtEntry register(String actor, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                            Double hours, String reason, String note) {
        UserAccount u = user(actor);
        if (workDate == null) {
            throw new IllegalArgumentException("Thiếu ngày làm thêm");
        }
        double h = OtEntry.computeHours(workDate, startTime, endTime, hours == null ? 0 : hours);
        if (h <= 0) {
            throw new IllegalArgumentException("Số giờ OT phải lớn hơn 0 (nhập giờ bắt đầu/kết thúc hoặc số giờ)");
        }
        requireOpen(OtEntry.periodOf(workDate));
        OtEntry e = entryRepo.save(new OtEntry(u.getId(), u.getFullName(), orgUnitOf(u.getId()),
                workDate, startTime, endTime, h, trim(reason, 300), trim(note, 1000)));
        audit.record("OT_REGISTERED", "OtEntry", e.getId(), actor,
                "date=" + workDate + ", hours=" + e.getHours());
        return e;
    }

    @Transactional
    public OtEntry update(String actor, String id, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                          Double hours, String reason, String note) {
        OtEntry e = mine(actor, id);
        requireOpen(e.getPeriodKey());                 // kỳ cũ chưa chốt
        requireOpen(OtEntry.periodOf(workDate));        // kỳ mới (nếu đổi ngày) chưa chốt
        double h = OtEntry.computeHours(workDate, startTime, endTime, hours == null ? 0 : hours);
        if (h <= 0) {
            throw new IllegalArgumentException("Số giờ OT phải lớn hơn 0");
        }
        e.apply(workDate, startTime, endTime, h, trim(reason, 300), trim(note, 1000));
        entryRepo.save(e);
        audit.record("OT_UPDATED", "OtEntry", e.getId(), actor, "date=" + workDate + ", hours=" + e.getHours());
        return e;
    }

    @Transactional
    public void delete(String actor, String id) {
        OtEntry e = mine(actor, id);
        requireOpen(e.getPeriodKey());
        entryRepo.delete(e);
        audit.record("OT_DELETED", "OtEntry", id, actor, "date=" + e.getWorkDate());
    }

    @Transactional(readOnly = true)
    public List<OtEntry> myEntries(String actor) {
        return entryRepo.findByUserIdOrderByWorkDateDescCreatedAtDesc(user(actor).getId());
    }

    /** Kỳ của một đăng ký đã chốt chưa (FE khoá nút sửa/xoá). */
    @Transactional(readOnly = true)
    public boolean isClosed(String periodKey) {
        return periodKey != null && periodRepo.existsById(periodKey);
    }

    // ===================== Tổng hợp (Story 3.2, FR-C03) =====================

    public record EmployeeSummary(String userId, String userName, String orgUnitId, String orgUnitName,
                                  double totalHours, int entryCount) {
    }

    public record DeptSummary(String orgUnitId, String orgUnitName, double totalHours, int entryCount) {
    }

    public record PeriodSummary(String periodKey, boolean closed, double totalHours, int entryCount,
                                List<EmployeeSummary> byEmployee, List<DeptSummary> byDept) {
    }

    @Transactional(readOnly = true)
    public PeriodSummary summary(String periodKey, String filterOrgUnitId) {
        List<OtEntry> entries = entryRepo.findByPeriodKeyOrderByOrgUnitIdAscUserNameAscWorkDateAsc(periodKey);
        Map<String, String> orgNames = orgNameMap();

        Map<String, EmployeeSummary> byEmp = new LinkedHashMap<>();
        Map<String, double[]> deptHours = new LinkedHashMap<>(); // orgUnitId -> [hours, count]
        double total = 0;
        int count = 0;
        for (OtEntry e : entries) {
            if (filterOrgUnitId != null && !filterOrgUnitId.isBlank() && !filterOrgUnitId.equals(e.getOrgUnitId())) {
                continue;
            }
            String ou = e.getOrgUnitId();
            String ouName = ou == null ? "(Chưa có phòng)" : orgNames.getOrDefault(ou, ou);
            EmployeeSummary cur = byEmp.get(e.getUserId());
            double th = (cur == null ? 0 : cur.totalHours()) + e.getHours();
            int ec = (cur == null ? 0 : cur.entryCount()) + 1;
            byEmp.put(e.getUserId(), new EmployeeSummary(e.getUserId(), e.getUserName(), ou, ouName, round(th), ec));

            double[] d = deptHours.computeIfAbsent(ou == null ? "__none__" : ou, k -> new double[2]);
            d[0] += e.getHours();
            d[1] += 1;
            total += e.getHours();
            count += 1;
        }

        List<DeptSummary> byDept = new ArrayList<>();
        for (Map.Entry<String, double[]> d : deptHours.entrySet()) {
            String ou = "__none__".equals(d.getKey()) ? null : d.getKey();
            String name = ou == null ? "(Chưa có phòng)" : orgNames.getOrDefault(ou, ou);
            byDept.add(new DeptSummary(ou, name, round(d.getValue()[0]), (int) d.getValue()[1]));
        }
        return new PeriodSummary(periodKey, isClosed(periodKey), round(total), count,
                new ArrayList<>(byEmp.values()), byDept);
    }

    /** Danh sách các kỳ có dữ liệu (mới nhất trước) — FE đổ vào dropdown chọn kỳ. */
    @Transactional(readOnly = true)
    public List<String> periodsWithData() {
        TreeSet<String> keys = new TreeSet<>(java.util.Comparator.reverseOrder());
        for (OtEntry e : entryRepo.findAll()) {
            keys.add(e.getPeriodKey());
        }
        return new ArrayList<>(keys);
    }

    // ===================== Chốt kỳ (Story 3.3, FR-C05) =====================

    @Transactional
    public OtPeriod closePeriod(String periodKey, String actor) {
        if (periodKey == null || !periodKey.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Kỳ không hợp lệ (định dạng YYYY-MM)");
        }
        if (periodRepo.existsById(periodKey)) {
            throw new IllegalStateException("Kỳ " + periodKey + " đã được chốt trước đó");
        }
        OtPeriod p = periodRepo.save(new OtPeriod(periodKey, actor));
        audit.record("OT_PERIOD_CLOSED", "OtPeriod", periodKey, actor, "period=" + periodKey);
        return p;
    }

    // ===================== Xuất .xlsx (Story 3.4, FR-C04 + NFR-09) =====================

    @Transactional(readOnly = true)
    public byte[] exportXlsx(String periodKey) {
        PeriodSummary s = summary(periodKey, null);
        List<OtEntry> entries = entryRepo.findByPeriodKeyOrderByOrgUnitIdAscUserNameAscWorkDateAsc(periodKey);
        Map<String, String> orgNames = orgNameMap();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);

            // Sheet 1: Theo nhân viên
            Sheet emp = wb.createSheet("Theo nhân viên");
            writeHeader(emp, header, "Nhân viên", "Phòng ban", "Tổng giờ OT", "Số lần");
            int r = 1;
            for (EmployeeSummary e : s.byEmployee()) {
                Row row = emp.createRow(r++);
                setText(row, 0, e.userName());
                setText(row, 1, e.orgUnitName());
                setNumber(row, 2, e.totalHours());
                setNumber(row, 3, e.entryCount());
            }
            autoSize(emp, 4);

            // Sheet 2: Theo phòng ban
            Sheet dept = wb.createSheet("Theo phòng ban");
            writeHeader(dept, header, "Phòng ban", "Tổng giờ OT", "Số lần");
            r = 1;
            for (DeptSummary d : s.byDept()) {
                Row row = dept.createRow(r++);
                setText(row, 0, d.orgUnitName());
                setNumber(row, 1, d.totalHours());
                setNumber(row, 2, d.entryCount());
            }
            autoSize(dept, 3);

            // Sheet 3: Chi tiết
            Sheet det = wb.createSheet("Chi tiết");
            writeHeader(det, header, "Ngày", "Nhân viên", "Phòng ban", "Bắt đầu", "Kết thúc", "Số giờ", "Lý do/Dự án", "Ghi chú");
            r = 1;
            for (OtEntry e : entries) {
                Row row = det.createRow(r++);
                setText(row, 0, e.getWorkDate() == null ? "" : e.getWorkDate().toString());
                setText(row, 1, e.getUserName());
                setText(row, 2, e.getOrgUnitId() == null ? "(Chưa có phòng)"
                        : orgNames.getOrDefault(e.getOrgUnitId(), e.getOrgUnitId()));
                setText(row, 3, e.getStartTime() == null ? "" : e.getStartTime().toString());
                setText(row, 4, e.getEndTime() == null ? "" : e.getEndTime().toString());
                setNumber(row, 5, e.getHours());
                setText(row, 6, e.getReason());
                setText(row, 7, e.getNote());
            }
            autoSize(det, 9);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được file Excel OT: " + ex.getMessage(), ex);
        }
    }

    // ===================== Tổng hợp theo KHOẢNG NGÀY =====================

    public record RangeEmployeeSummary(String userId, String userName, String orgUnitId, String orgUnitName,
                                       double totalHours, int entryCount) {
    }

    public record RangeTotals(double totalHours, int people) {
    }

    public record RangeSummary(LocalDate from, LocalDate to, List<RangeEmployeeSummary> byEmployee,
                               RangeTotals totals) {
    }

    /** Gom OtEntry có workDate trong [from,to], tổng giờ theo nhân sự (lọc theo orgUnitId nếu có). */
    @Transactional(readOnly = true)
    public RangeSummary summaryRange(LocalDate from, LocalDate to, String filterOrgUnitId) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Thiếu ngày bắt đầu/kết thúc");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc");
        }
        List<OtEntry> entries = entryRepo.findByWorkDateBetween(from, to);
        Map<String, String> orgNames = orgNameMap();

        Map<String, double[]> agg = new LinkedHashMap<>();  // userId -> [hours, count]
        Map<String, String[]> meta = new LinkedHashMap<>(); // userId -> [userName, orgUnitId]
        double total = 0;
        for (OtEntry e : entries) {
            if (filterOrgUnitId != null && !filterOrgUnitId.isBlank() && !filterOrgUnitId.equals(e.getOrgUnitId())) {
                continue;
            }
            double[] a = agg.computeIfAbsent(e.getUserId(), k -> new double[2]);
            a[0] += e.getHours();
            a[1] += 1;
            meta.putIfAbsent(e.getUserId(), new String[]{e.getUserName(), e.getOrgUnitId()});
            total += e.getHours();
        }

        List<RangeEmployeeSummary> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> en : agg.entrySet()) {
            String[] m = meta.get(en.getKey());
            String ou = m[1];
            String ouName = ou == null ? "(Chưa có phòng)" : orgNames.getOrDefault(ou, ou);
            rows.add(new RangeEmployeeSummary(en.getKey(), m[0], ou, ouName,
                    round(en.getValue()[0]), (int) en.getValue()[1]));
        }
        return new RangeSummary(from, to, rows, new RangeTotals(round(total), rows.size()));
    }

    /** Xuất .xlsx tổng hợp OT theo khoảng ngày: cột [Tên, Bộ phận, Tổng giờ OT, Số lần] + dòng TỔNG. */
    @Transactional(readOnly = true)
    public byte[] exportRangeXlsx(LocalDate from, LocalDate to, String filterOrgUnitId) {
        RangeSummary s = summaryRange(from, to, filterOrgUnitId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            Sheet sheet = wb.createSheet("Tổng hợp OT");

            Row title = sheet.createRow(0);
            setText(title, 0, "Tổng hợp OT từ " + from + " đến " + to);

            Row head = sheet.createRow(2);
            String[] titles = {"Tên", "Bộ phận", "Tổng giờ OT", "Số lần"};
            for (int i = 0; i < titles.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(titles[i]);
                c.setCellStyle(header);
            }
            int r = 3;
            for (RangeEmployeeSummary e : s.byEmployee()) {
                Row row = sheet.createRow(r++);
                setText(row, 0, e.userName());
                setText(row, 1, e.orgUnitName());
                setNumber(row, 2, e.totalHours());
                setNumber(row, 3, e.entryCount());
            }
            Row total = sheet.createRow(r);
            Cell tc = total.createCell(0);
            tc.setCellValue("TỔNG");
            tc.setCellStyle(header);
            setNumber(total, 2, s.totals().totalHours());
            setNumber(total, 3, s.totals().people());

            autoSize(sheet, 4);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được file Excel OT theo khoảng: " + ex.getMessage(), ex);
        }
    }

    // ===================== helpers =====================

    private UserAccount user(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + username));
    }

    /** Đăng ký phải tồn tại và thuộc về người gọi. */
    private OtEntry mine(String actor, String id) {
        OtEntry e = entryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đăng ký OT"));
        UserAccount u = user(actor);
        if (!u.getId().equals(e.getUserId())) {
            throw new IllegalArgumentException("Chỉ được sửa/xoá đăng ký OT của chính mình");
        }
        return e;
    }

    private void requireOpen(String periodKey) {
        if (periodRepo.existsById(periodKey)) {
            throw new IllegalStateException("Kỳ " + periodKey + " đã chốt — không thể chỉnh sửa");
        }
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

    private static void writeHeader(Sheet sheet, CellStyle style, String... titles) {
        Row row = sheet.createRow(0);
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
     * Trung hoà formula/CSV injection (NFR-09): nếu ô text bắt đầu bằng = + - @ (hoặc tab/CR),
     * chèn dấu nháy đơn đứng trước để Excel coi là văn bản thuần, không thực thi công thức.
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
