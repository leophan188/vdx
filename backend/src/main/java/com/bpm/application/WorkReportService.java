package com.bpm.application;

import com.bpm.api.dto.WorkReportDto.GroupStat;
import com.bpm.api.dto.WorkReportDto.ReportRow;
import com.bpm.api.dto.WorkReportDto.WorkReport;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cụm BÁO CÁO CÔNG VIỆC — snapshot LIVE tại một mốc (Report Ngày / Report Tuần / Dashboard).
 *
 * <p>Đo bằng EST GIỜ của TASK LÁ (task không có con) để tránh trùng với rollup của task cha —
 * cùng quy tắc "task lá" + progress theo estimateHours như {@link ProjectReportService} /
 * {@link ProjectService}. 4 nhóm:
 * <ul>
 *   <li>inProgress = IN_PROGRESS + IN_REVIEW</li>
 *   <li>done = DONE</li>
 *   <li>upcoming = TODO + BACKLOG</li>
 *   <li>overdue (cắt ngang) = dueDate &lt; mốc &amp;&amp; status != DONE</li>
 * </ul>
 *
 * <p>Phân quyền theo {@code canSeeAll}: true (admin / FEAT_REPORTS) ⇒ mọi dự án + mọi nhân sự;
 * false ⇒ chỉ dự án user là thành viên/chủ sở hữu, và chỉ chính họ ở dòng "theo thành viên".
 */
@Service
public class WorkReportService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final ProjectMemberRepository memberRepo;

    public WorkReportService(ProjectRepository projectRepo, ProjectTaskRepository taskRepo,
                             UserAccountRepository userRepo, EmployeeRepository employeeRepo,
                             ProjectMemberRepository memberRepo) {
        this.projectRepo = projectRepo;
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.memberRepo = memberRepo;
    }

    // ===================== API cấp cao =====================

    /** Báo cáo NGÀY: mốc = ngày chọn (mặc định hôm nay). */
    @Transactional(readOnly = true)
    public WorkReport daily(String actor, boolean canSeeAll, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return build(actor, canSeeAll, d, "DAILY", "Ngày " + d.format(DMY));
    }

    /** Báo cáo TUẦN: mốc = cuối tuần (Chủ nhật) của tuần chứa ngày chọn. */
    @Transactional(readOnly = true)
    public WorkReport weekly(String actor, boolean canSeeAll, LocalDate anyDateInWeek) {
        LocalDate base = anyDateInWeek != null ? anyDateInWeek : LocalDate.now();
        LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        String label = "Tuần " + monday.format(DMY) + "–" + sunday.format(DMY);
        return build(actor, canSeeAll, sunday, "WEEKLY", label);
    }

    /** Dashboard = snapshot hôm nay (dùng chung dữ liệu với Report Ngày). */
    @Transactional(readOnly = true)
    public WorkReport dashboard(String actor, boolean canSeeAll) {
        return daily(actor, canSeeAll, LocalDate.now());
    }

    // ===================== Core build =====================

    @Transactional(readOnly = true)
    public WorkReport build(String actor, boolean canSeeAll, LocalDate snapshot,
                            String periodType, String periodLabel) {
        List<ProjectTask> tasks = scopedTasks(actor, canSeeAll);

        // Task LÁ = không có con (trong phạm vi đã lọc). Cha/con cùng dự án nên luôn cùng phạm vi.
        Set<String> parentIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                parentIds.add(t.getParentId());
            }
        }
        List<ProjectTask> leaves = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (!parentIds.contains(t.getId())) {
                leaves.add(t);
            }
        }

        // ----- Tổng quan -----
        Agg overall = new Agg();
        for (ProjectTask t : leaves) {
            overall.add(t, snapshot);
        }
        ReportRow overview = overall.toRow("ALL", null, "Tổng quan", null);

        // ----- Theo dự án -----
        Map<String, Agg> byProjectAgg = new HashMap<>();
        for (ProjectTask t : leaves) {
            byProjectAgg.computeIfAbsent(t.getProjectId(), k -> new Agg()).add(t, snapshot);
        }
        List<ReportRow> byProject = new ArrayList<>();
        Map<String, Project> projectCache = new HashMap<>();
        for (Map.Entry<String, Agg> e : byProjectAgg.entrySet()) {
            Project p = projectCache.computeIfAbsent(e.getKey(),
                    id -> projectRepo.findById(id).orElse(null));
            String code = p != null ? p.getCode() : null;
            String name = p != null ? p.getName() : e.getKey();
            byProject.add(e.getValue().toRow(e.getKey(), code, name, code));
        }
        byProject.sort((a, b) -> Double.compare(b.totalEst(), a.totalEst()));

        // ----- Theo thành viên (assignee) -----
        Map<String, Employee> empByUser = new HashMap<>();
        for (Employee emp : employeeRepo.findAll()) {
            if (emp.getUserAccountId() != null) {
                empByUser.put(emp.getUserAccountId(), emp);
            }
        }
        Map<String, Agg> byMemberAgg = new HashMap<>();
        Agg unassigned = new Agg();
        boolean hasUnassigned = false;
        for (ProjectTask t : leaves) {
            String uid = t.getAssigneeUserId();
            if (uid == null) {
                unassigned.add(t, snapshot);
                hasUnassigned = true;
            } else {
                byMemberAgg.computeIfAbsent(uid, k -> new Agg()).add(t, snapshot);
            }
        }
        List<ReportRow> byMember = new ArrayList<>();
        for (Map.Entry<String, Agg> e : byMemberAgg.entrySet()) {
            String uid = e.getKey();
            Employee emp = empByUser.get(uid);
            UserAccount acc = userRepo.findById(uid).orElse(null);
            String name = ProjectService.personName(emp, acc, uid);
            String empCode = emp != null ? emp.getEmpCode() : null;
            String dept = emp != null ? emp.getDeptCode() : null;
            byMember.add(e.getValue().toRow(uid, empCode, name, dept));
        }
        byMember.sort((a, b) -> Double.compare(b.totalEst(), a.totalEst()));
        if (hasUnassigned) {
            byMember.add(unassigned.toRow("UNASSIGNED", null, "Chưa giao", null));
        }

        return new WorkReport(periodType, periodLabel, snapshot.format(DMY), overview, byProject, byMember);
    }

    // ===================== Scope dữ liệu =====================

    /** Tập task theo phạm vi: canSeeAll → mọi task; else → task thuộc dự án user là thành viên/chủ sở hữu. */
    private List<ProjectTask> scopedTasks(String actor, boolean canSeeAll) {
        if (canSeeAll) {
            return taskRepo.findAll();
        }
        UserAccount me = userRepo.findByUsername(actor).orElse(null);
        if (me == null) {
            return new ArrayList<>();
        }
        Set<String> allowed = new HashSet<>();
        for (ProjectMember m : memberRepo.findByUserId(me.getId())) {
            allowed.add(m.getProjectId());
        }
        for (Project p : projectRepo.findAll()) {
            if (me.getId().equals(p.getOwnerUserId())) {
                allowed.add(p.getId());
            }
        }
        if (allowed.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProjectTask> out = new ArrayList<>();
        for (ProjectTask t : taskRepo.findAll()) {
            if (allowed.contains(t.getProjectId())) {
                out.add(t);
            }
        }
        return out;
    }

    // ===================== Bộ tích luỹ 4 nhóm =====================

    /** Cộng dồn est giờ + số task theo 4 nhóm cho MỘT dòng báo cáo. */
    private static final class Agg {
        double totalEst;
        double inEst; int inCount;
        double doneEst; int doneCount;
        double upEst; int upCount;
        double overEst; int overCount;

        void add(ProjectTask t, LocalDate snapshot) {
            TaskStatus s = t.getStatus();
            if (s == TaskStatus.CANCELLED) {
                return; // Huỷ = ngoài phạm vi, không tính vào báo cáo công việc
            }
            double est = t.getEstimateHours();
            totalEst += est;
            boolean isDone = s == TaskStatus.DONE;
            if (isDone) {
                doneEst += est; doneCount++;
            } else if (s == TaskStatus.IN_PROGRESS || s == TaskStatus.IN_REVIEW) {
                inEst += est; inCount++;
            } else { // TODO / BACKLOG
                upEst += est; upCount++;
            }
            // Trễ = cắt ngang: dueDate < mốc & chưa DONE (tập con của chưa-xong).
            if (!isDone && t.getDueDate() != null && t.getDueDate().isBefore(snapshot)) {
                overEst += est; overCount++;
            }
        }

        ReportRow toRow(String id, String code, String name, String extra) {
            return new ReportRow(id, code, name, extra, round2(totalEst),
                    new GroupStat("inProgress", round2(inEst), inCount, pct(inEst)),
                    new GroupStat("done", round2(doneEst), doneCount, pct(doneEst)),
                    new GroupStat("upcoming", round2(upEst), upCount, pct(upEst)),
                    new GroupStat("overdue", round2(overEst), overCount, pct(overEst)),
                    pct(doneEst));
        }

        private double pct(double part) {
            return totalEst > 0 ? round1(part / totalEst * 100.0) : 0.0;
        }
    }

    // ===================== Xuất .xlsx =====================

    /**
     * Xuất báo cáo ra .xlsx (Apache POI): 3 sheet — "Tổng quan", "Theo dự án", "Theo thành viên".
     * Header đậm; est giờ + % được ghi dạng số. Ô text được trung hoà formula/CSV injection.
     */
    public byte[] exportXlsx(WorkReport r) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);

            // Sheet 1: Tổng quan
            Sheet s1 = wb.createSheet("Tổng quan");
            setText(s1.createRow(0), 0, r.periodLabel() + "  (mốc " + r.snapshotDate() + ")");
            writeGroupHeader(s1, 2, header);
            writeRow(s1, 3, r.overview());
            autoSize(s1, 10);

            // Sheet 2: Theo dự án
            Sheet s2 = wb.createSheet("Theo dự án");
            writeGroupHeader(s2, 0, header);
            int rr = 1;
            for (ReportRow row : r.byProject()) {
                writeRow(s2, rr++, row);
            }
            autoSize(s2, 10);

            // Sheet 3: Theo thành viên
            Sheet s3 = wb.createSheet("Theo thành viên");
            writeGroupHeader(s3, 0, header);
            rr = 1;
            for (ReportRow row : r.byMember()) {
                writeRow(s3, rr++, row);
            }
            autoSize(s3, 10);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được file Excel báo cáo công việc: " + ex.getMessage(), ex);
        }
    }

    private static void writeGroupHeader(Sheet sheet, int rowIdx, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        String[] titles = {"Tên", "Mã/Bộ phận", "Tổng est (giờ)",
                "Đang làm (giờ)", "Đang làm %", "Đã xong (giờ)", "Đã xong %",
                "Trễ (giờ)", "Trễ %", "Sắp làm (giờ)", "Sắp làm %", "% hoàn thành"};
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(style);
        }
    }

    private static void writeRow(Sheet sheet, int rowIdx, ReportRow r) {
        Row row = sheet.createRow(rowIdx);
        setText(row, 0, r.name());
        setText(row, 1, r.extra() != null ? r.extra() : (r.code() != null ? r.code() : ""));
        setNumber(row, 2, r.totalEst());
        setNumber(row, 3, r.inProgress().estimateHours());
        setNumber(row, 4, r.inProgress().pct());
        setNumber(row, 5, r.done().estimateHours());
        setNumber(row, 6, r.done().pct());
        setNumber(row, 7, r.overdue().estimateHours());
        setNumber(row, 8, r.overdue().pct());
        setNumber(row, 9, r.upcoming().estimateHours());
        setNumber(row, 10, r.upcoming().pct());
        setNumber(row, 11, r.completionPct());
    }

    // ---- POI helpers (cùng quy ước LeaveService) ----

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
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

    /** Trung hoà formula/CSV injection: ô text bắt đầu bằng = + - @ (hoặc tab/CR) → tiền tố '. */
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

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
