package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Seed dữ liệu DEMO cho module QUẢN LÝ DỰ ÁN (mini-Jira) trên nhân sự THẬT đã import.
 * Kích hoạt thủ công bởi ADMIN từ màn "Xoá dữ liệu hệ thống" (POST /api/v1/system/seed-project-demo).
 *
 * <p>Nguyên tắc (giống {@link HrDemoSeeder}):
 * <ul>
 *   <li>CHỈ THÊM — không xoá/đụng dữ liệu thật. Gọi đúng service thật ({@link ProjectService},
 *       {@link ProjectTaskService}) — KHÔNG ghi thẳng repository, để mọi audit/validate chạy đủ.</li>
 *   <li>Idempotent: nếu đã có dự án theo code (vd "OCN") thì BỎ QUA toàn bộ, trả seeded=false.</li>
 *   <li>Chủ dự án / thành viên / người được giao là NHÂN SỰ THẬT có tài khoản:
 *       "Giám đốc" (GIAM_DOC) → PM, "Trưởng nhóm" (TRUONG_NHOM) → LEAD, còn lại → MEMBER.
 *       Fallback: nếu thiếu vai trò, dùng nhân sự bất kỳ có userAccountId.</li>
 *   <li>Cây task ĐA CẤP (Epic → Story → Task/Bug), assignee thật, estimateHours, start/dueDate
 *       (dd/MM/yyyy) trải nhiều tuần/tháng, trạng thái rải đủ (BACKLOG/TODO/IN_PROGRESS/IN_REVIEW/DONE)
 *       để Kanban / Timeline / Báo cáo có volume.</li>
 *   <li>Bọc try/catch theo TỪNG dự án — 1 lỗi không chặn các dự án còn lại.</li>
 * </ul>
 */
@Service
public class ProjectDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProjectDemoSeeder.class);

    /** Khoá nhận diện đã-seed (idempotent). Dự án OCN là "mỏ neo". */
    private static final String GUARD_CODE = "OCN";

    /** Guard RIÊNG cho dự án kiểm thử % (tạo được kể cả khi OCN đã tồn tại). */
    private static final String QA_GUARD_CODE = "QA";

    private static final String ROLE_GIAM_DOC = "GIAM_DOC";
    private static final String ROLE_TRUONG_NHOM = "TRUONG_NHOM";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectService projectService;
    private final ProjectTaskService taskService;
    private final ProjectCollabService collabService;
    private final ProjectRepository projectRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final RoleService roleService;
    private final AuditPort auditPort;

    public ProjectDemoSeeder(ProjectService projectService, ProjectTaskService taskService,
                             ProjectCollabService collabService,
                             ProjectRepository projectRepo, EmployeeRepository employeeRepo,
                             UserAccountRepository userRepo, RoleService roleService, AuditPort auditPort) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.collabService = collabService;
        this.projectRepo = projectRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.roleService = roleService;
        this.auditPort = auditPort;
    }

    /** Tóm tắt kết quả seed (trả về cho FE). */
    public record SeedResult(boolean seeded, int projects, int members, int tasks, int bugs, String message) {
    }

    /**
     * Tạo 2–3 dự án thật chi tiết (OCN, BPM, WEB) + thành viên + cây task đa cấp + bug. Idempotent.
     * @param actor người bấm nút (admin) — dùng cho audit + làm createdBy của task.
     */
    public SeedResult seed(String actor) {
        // Dự án QA (kiểm thử %) có guard RIÊNG — seed kể cả khi OCN đã tồn tại.
        Counter qa = new Counter();
        boolean qaSeeded = false;
        if (!projectRepo.existsByCode(QA_GUARD_CODE)) {
            qaSeeded = seedSafely(QA_GUARD_CODE, () -> seedQa(actor, qa));
        } else {
            log.info("[ProjectDemoSeeder] Đã có dự án kiểm thử % (code={}), bỏ qua QA.", QA_GUARD_CODE);
        }

        if (projectRepo.existsByCode(GUARD_CODE)) {
            log.info("[ProjectDemoSeeder] Đã có dự án demo (code={}), bỏ qua phần demo chính.", GUARD_CODE);
            if (qaSeeded) {
                auditPort.record("PROJECT_DEMO_SEEDED", "System", null, actor,
                        "QA only: projects=" + qa.projects + ", tasks=" + qa.tasks);
                return new SeedResult(true, qa.projects, qa.members, qa.tasks, qa.bugs,
                        "Demo chính đã có từ trước. ĐÃ tạo dự án kiểm thử % \"QA\" ("
                                + qa.tasks + " công việc; % kỳ vọng dự án ≈ 54.17%).");
            }
            auditPort.record("PROJECT_DEMO_SEED_SKIPPED", "System", null, actor, "Đã tồn tại — không seed lại.");
            return new SeedResult(false, 0, 0, 0, 0,
                    "Đã có dữ liệu dự án demo (và QA) từ trước — bỏ qua (không nhân đôi).");
        }

        // ===== Chọn NHÂN SỰ THẬT theo vai trò (đang làm việc + có tài khoản) =====
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        if (active.isEmpty()) {
            if (qaSeeded) {
                auditPort.record("PROJECT_DEMO_SEEDED", "System", null, actor,
                        "QA only (chưa có nhân sự): projects=" + qa.projects + ", tasks=" + qa.tasks);
                return new SeedResult(true, qa.projects, qa.members, qa.tasks, qa.bugs,
                        "Chưa có nhân sự để seed demo chính, nhưng ĐÃ tạo dự án kiểm thử % \"QA\" ("
                                + qa.tasks + " công việc; % kỳ vọng dự án ≈ 54.17%). Hãy import nhân sự để có demo đầy đủ.");
            }
            return new SeedResult(false, 0, 0, 0, 0,
                    "Chưa có nhân sự (đang làm việc + có tài khoản) để gán chủ dự án/thành viên. Hãy import nhân sự trước.");
        }
        List<Employee> directors = byRole(active, ROLE_GIAM_DOC);
        List<Employee> leaders = byRole(active, ROLE_TRUONG_NHOM);
        Picker pmPick = new Picker(directors.isEmpty() ? active : directors);
        Picker leadPick = new Picker(leaders.isEmpty() ? active : leaders);
        // Thành viên thường: ai không phải GĐ; fallback toàn bộ active.
        List<Employee> rest = active.stream().filter(e -> !directors.contains(e)).toList();
        Picker memberPick = new Picker(rest.isEmpty() ? active : rest);

        log.info("[ProjectDemoSeeder] Nhân sự thật: {} active (GĐ={}, TrNhóm={}, còn lại={})",
                active.size(), directors.size(), leaders.size(), rest.size());

        Counter c = new Counter();
        // Mốc thời gian gốc: lùi về quá khứ để Timeline/Gantt trải nhiều tuần/tháng.
        LocalDate base = LocalDate.now().minusMonths(2);

        seedSafely("OCN", () -> seedOcn(actor, pmPick, leadPick, memberPick, base, c));
        seedSafely("BPM", () -> seedBpm(actor, pmPick, leadPick, memberPick, base, c));
        seedSafely("WEB", () -> seedWeb(actor, pmPick, leadPick, memberPick, base, c));

        // Gộp số liệu QA (nếu vừa seed) vào tổng.
        c.projects += qa.projects;
        c.members += qa.members;
        c.tasks += qa.tasks;
        c.bugs += qa.bugs;

        auditPort.record("PROJECT_DEMO_SEEDED", "System", null, actor,
                "projects=" + c.projects + ", members=" + c.members + ", tasks=" + c.tasks + ", bugs=" + c.bugs);
        log.info("[ProjectDemoSeeder] Seed dự án: {} dự án, {} thành viên, {} task ({} bug).",
                c.projects, c.members, c.tasks, c.bugs);
        return new SeedResult(true, c.projects, c.members, c.tasks, c.bugs,
                "Đã tạo " + c.projects + " dự án, " + c.members + " thành viên, " + c.tasks
                        + " công việc (trong đó " + c.bugs + " bug)"
                        + (qaSeeded ? " — gồm dự án \"QA\" kiểm thử % (≈54.17%)" : "")
                        + " — đầy đủ mô tả, ngày bắt đầu/kết thúc, giờ thực tế (spent), man-day thành viên & bình luận"
                        + " cho Kanban/Timeline/Burndown/Báo cáo.");
    }

    // ===================== DỰ ÁN 1: OCN — Cổng nội bộ ONEConnect (ACTIVE) =====================

    private void seedOcn(String actor, Picker pmPick, Picker leadPick, Picker memberPick, LocalDate base, Counter c) {
        String pmId = pmPick.nextUser();
        String leadId = leadPick.nextUser();
        String m1 = memberPick.nextUser();
        String m2 = memberPick.nextUser();
        String m3 = memberPick.nextUser();

        String pid = createProject("OCN", "Cổng nội bộ ONEConnect",
                "Cổng thông tin & mạng xã hội nội bộ: bảng tin, quản lý nhân sự, đăng ký OT, phê duyệt.",
                "ACTIVE", base, base.plusMonths(4), pmId, actor, c);

        // Mỗi thành viên 1 khoảng tham gia KHÁC NHAU (manday > 0, lệch nhau) — trải suốt timeline dự án.
        addMember(pid, pmId, "PM", base, base.plusMonths(4), actor, c);
        addMember(pid, leadId, "LEAD", base, base.plusMonths(3), actor, c);
        addMember(pid, m1, "MEMBER", base, base.plusWeeks(8), actor, c);
        addMember(pid, m2, "MEMBER", base.plusWeeks(2), base.plusMonths(3), actor, c);
        addMember(pid, m3, "MEMBER", base.plusWeeks(3), base.plusMonths(2), actor, c);

        // ---- Epic 1: Mạng xã hội ----
        String epic1 = task(pid, null, "Mạng xã hội nội bộ", "EPIC", "IN_PROGRESS", "HIGH",
                leadId, 0, base, base.plusMonths(2), null, actor, c);
        String story11 = task(pid, epic1, "Bảng tin (News Feed)", "STORY", "IN_PROGRESS", "HIGH",
                leadId, 0, base, base.plusWeeks(5), null, actor, c);
        String t111 = task(pid, story11, "Đăng bài + đính kèm media",
                "Cho phép nhân viên đăng bài kèm ảnh/clip lên bảng tin nội bộ; giới hạn 10MB/tệp.",
                "TASK", "DONE", "HIGH",
                m1, 16, base, base.plusWeeks(2), "Màn đăng bài", actor, c);
        comment(pid, t111, actor,
                "Đã xong phần upload ảnh, đang chờ review UI.",
                "Lưu ý: nén ảnh > 5MB ở client trước khi gửi.");
        task(pid, story11, "Like / Comment bài viết", "TASK", "IN_PROGRESS", "MEDIUM",
                m2, 12, base.plusWeeks(2), base.plusWeeks(4), "Màn bảng tin", actor, c);
        task(pid, story11, "Thông báo realtime (WebSocket)", "TASK", "TODO", "MEDIUM",
                m1, 20, base.plusWeeks(4), base.plusWeeks(6), null, actor, c);
        // Bug gắn vào màn đăng bài
        String bug1 = bug(pid, story11, "Mất media khi đăng bài quá 10MB", "IN_PROGRESS", "URGENT",
                m1, 6, base.plusWeeks(2), base.plusWeeks(3), "Màn đăng bài", actor, c);
        comment(pid, bug1, actor,
                "Tái hiện được khi tệp ~12MB, server trả 413.",
                "Đang tăng giới hạn multipart + báo lỗi thân thiện ở client.");

        // ---- Epic 2: Quản lý nhân sự ----
        String epic2 = task(pid, null, "Quản lý nhân sự", "EPIC", "IN_PROGRESS", "HIGH",
                leadId, 0, base.plusWeeks(3), base.plusMonths(3), null, actor, c);
        String story21 = task(pid, epic2, "Hồ sơ nhân sự", "STORY", "IN_REVIEW", "MEDIUM",
                m2, 0, base.plusWeeks(3), base.plusWeeks(7), null, actor, c);
        task(pid, story21, "Xem & sửa hồ sơ cá nhân", "TASK", "DONE", "MEDIUM",
                m2, 14, base.plusWeeks(3), base.plusWeeks(5), "Màn hồ sơ", actor, c);
        task(pid, story21, "Import nhân sự từ Excel", "TASK", "IN_REVIEW", "HIGH",
                m3, 18, base.plusWeeks(5), base.plusWeeks(7), "Màn import nhân sự", actor, c);
        bug(pid, story21, "Import sai định dạng ngày sinh dd/MM/yyyy", "TODO", "HIGH",
                m3, 4, base.plusWeeks(6), base.plusWeeks(7), "Màn import nhân sự", actor, c);
        task(pid, epic2, "Sơ đồ cơ cấu tổ chức", "STORY", "TODO", "LOW",
                m1, 22, base.plusWeeks(7), base.plusWeeks(10), null, actor, c);

        // ---- Epic 3: Đăng ký OT ----
        String epic3 = task(pid, null, "Đăng ký & duyệt OT", "EPIC", "TODO", "MEDIUM",
                leadId, 0, base.plusMonths(1), base.plusMonths(3), null, actor, c);
        String story31 = task(pid, epic3, "Form đăng ký OT", "STORY", "TODO", "MEDIUM",
                m3, 0, base.plusMonths(1), base.plusMonths(2), null, actor, c);
        task(pid, story31, "Nhập giờ OT + lý do", "TASK", "BACKLOG", "MEDIUM",
                m3, 10, base.plusMonths(1), base.plusWeeks(6), "Màn đăng ký OT", actor, c);
        task(pid, story31, "Quản lý duyệt OT", "TASK", "BACKLOG", "MEDIUM",
                leadId, 12, base.plusWeeks(6), base.plusMonths(2), "Màn duyệt OT", actor, c);
        task(pid, epic3, "Báo cáo tổng hợp OT theo tháng", "STORY", "BACKLOG", "LOW",
                m2, 16, base.plusMonths(2), base.plusMonths(3), null, actor, c);
    }

    // ===================== DỰ ÁN 2: BPM — Triển khai hệ thống BPM (ACTIVE) =====================

    private void seedBpm(String actor, Picker pmPick, Picker leadPick, Picker memberPick, LocalDate base, Counter c) {
        String pmId = pmPick.nextUser();
        String leadId = leadPick.nextUser();
        String m1 = memberPick.nextUser();
        String m2 = memberPick.nextUser();

        String pid = createProject("BPM", "Triển khai hệ thống BPM",
                "Triển khai nền tảng quy trình nghiệp vụ: hạ tầng, tích hợp hệ thống, đào tạo người dùng.",
                "ACTIVE", base.minusWeeks(2), base.plusMonths(3), pmId, actor, c);

        addMember(pid, pmId, "PM", base.minusWeeks(2), base.plusMonths(3), actor, c);
        addMember(pid, leadId, "LEAD", base.minusWeeks(2), base.plusMonths(2), actor, c);
        addMember(pid, m1, "MEMBER", base.minusWeeks(2), base.plusWeeks(6), actor, c);
        addMember(pid, m2, "MEMBER", base, base.plusMonths(2), actor, c);

        // ---- Epic: Hạ tầng ----
        String eHaTang = task(pid, null, "Hạ tầng", "EPIC", "DONE", "HIGH",
                leadId, 0, base.minusWeeks(2), base.plusWeeks(3), null, actor, c);
        task(pid, eHaTang, "Cài đặt máy chủ ứng dụng & CSDL", "TASK", "DONE", "HIGH",
                m1, 24, base.minusWeeks(2), base.plusWeeks(1), null, actor, c);
        task(pid, eHaTang, "Cấu hình sao lưu & giám sát", "TASK", "DONE", "MEDIUM",
                m2, 16, base.plusWeeks(1), base.plusWeeks(3), null, actor, c);

        // ---- Epic: Tích hợp ----
        String eTichHop = task(pid, null, "Tích hợp hệ thống", "EPIC", "IN_PROGRESS", "HIGH",
                leadId, 0, base.plusWeeks(2), base.plusMonths(2), null, actor, c);
        String sSso = task(pid, eTichHop, "Tích hợp SSO", "STORY", "IN_PROGRESS", "HIGH",
                m1, 0, base.plusWeeks(2), base.plusWeeks(6), null, actor, c);
        task(pid, sSso, "Kết nối LDAP / Active Directory", "TASK", "DONE", "HIGH",
                m1, 20, base.plusWeeks(2), base.plusWeeks(4), "Màn đăng nhập SSO", actor, c);
        task(pid, sSso, "Đồng bộ tài khoản tự động", "TASK", "IN_PROGRESS", "MEDIUM",
                m2, 18, base.plusWeeks(4), base.plusWeeks(6), null, actor, c);
        bug(pid, sSso, "Đăng nhập SSO lỗi với tài khoản có dấu", "IN_REVIEW", "HIGH",
                m1, 5, base.plusWeeks(4), base.plusWeeks(5), "Màn đăng nhập SSO", actor, c);
        task(pid, eTichHop, "Tích hợp API nhân sự", "STORY", "TODO", "MEDIUM",
                m2, 26, base.plusWeeks(6), base.plusMonths(2), null, actor, c);

        // ---- Epic: Đào tạo ----
        String eDaoTao = task(pid, null, "Đào tạo người dùng", "EPIC", "TODO", "MEDIUM",
                leadId, 0, base.plusMonths(2), base.plusMonths(3), null, actor, c);
        task(pid, eDaoTao, "Biên soạn tài liệu hướng dẫn", "TASK", "TODO", "MEDIUM",
                m1, 16, base.plusMonths(2), base.plusWeeks(10), null, actor, c);
        task(pid, eDaoTao, "Tổ chức buổi đào tạo trực tiếp", "TASK", "BACKLOG", "LOW",
                leadId, 12, base.plusWeeks(10), base.plusMonths(3), null, actor, c);
    }

    // ===================== DỰ ÁN 3: WEB — Website công ty (PLANNING) =====================

    private void seedWeb(String actor, Picker pmPick, Picker leadPick, Picker memberPick, LocalDate base, Counter c) {
        String pmId = pmPick.nextUser();
        String m1 = memberPick.nextUser();

        String pid = createProject("WEB", "Website công ty",
                "Làm mới website giới thiệu công ty: trang chủ, tin tức, tuyển dụng, liên hệ.",
                "PLANNING", base.plusMonths(2), base.plusMonths(5), pmId, actor, c);

        addMember(pid, pmId, "PM", base.plusMonths(2), base.plusMonths(5), actor, c);
        addMember(pid, m1, "MEMBER", base.plusMonths(2), base.plusMonths(4), actor, c);

        String epic = task(pid, null, "Thiết kế & nội dung", "EPIC", "BACKLOG", "MEDIUM",
                pmId, 0, base.plusMonths(2), base.plusMonths(4), null, actor, c);
        task(pid, epic, "Khảo sát yêu cầu & sitemap", "TASK", "BACKLOG", "MEDIUM",
                m1, 12, base.plusMonths(2), base.plusWeeks(10), null, actor, c);
        task(pid, epic, "Thiết kế giao diện trang chủ", "TASK", "BACKLOG", "MEDIUM",
                m1, 20, base.plusWeeks(10), base.plusMonths(3), "Màn trang chủ", actor, c);
        task(pid, epic, "Trang Tuyển dụng", "TASK", "BACKLOG", "LOW",
                m1, 16, base.plusMonths(3), base.plusMonths(4), "Màn tuyển dụng", actor, c);
    }

    // ===================== DỰ ÁN QA: kiểm thử tính % hoàn thành =====================

    /**
     * Dự án "QA" với % HOÀN THÀNH KỲ VỌNG TÍNH ĐƯỢC TAY (rollup theo estimateHours của task LÁ).
     * Assignee để trống (không ảnh hưởng %). Cấu trúc & kỳ vọng:
     *
     * <pre>
     * Nhóm A (cha, est=0): 10 lá × 10h = 100h; 5 lá DONE  → A = 50/100 = 50.00%
     * Nhóm B (cha, est=0):  8 lá × 5h  = 40h;  4 lá DONE  → B = 20/40  = 50.00%
     * Nhóm C (cha, est=0):  6 lá [20,10,10,10,5,5] = 60h; DONE [20,10,10] = 40h → C = 40/60 = 66.67%
     * 4 lá ĐỘC LẬP (không cha) × 10h = 40h; 2 DONE → 20h done
     *
     * Tổng LÁ: est = 100 + 40 + 60 + 40 = 240h; doneEst = 50 + 20 + 40 + 20 = 130h
     * → completionPct dự án = 130 / 240 × 100 = 54.166… ≈ 54.17%
     * Tổng task: 3 cha + 28 lá = 31 task.
     * </pre>
     */
    private void seedQa(String actor, Counter c) {
        LocalDate base = LocalDate.now();
        String pid = createProject(QA_GUARD_CODE, "Kiểm thử tính % hoàn thành",
                "Dữ liệu có est & trạng thái biết trước để kiểm chứng % hoàn thành (rollup theo estimateHours của task lá). "
                        + "KỲ VỌNG: Nhóm A=50%, Nhóm B=50%, Nhóm C=66.67%; toàn dự án ≈ 54.17%.",
                "ACTIVE", base, base.plusMonths(2), null, actor, c);

        // Nhóm A: 10 lá × 10h, 5 DONE → 50%
        String a = task(pid, null, "Nhóm A (10×10h, 5 DONE → kỳ vọng 50%)", "EPIC", "IN_PROGRESS", "MEDIUM",
                null, 0, base, base.plusWeeks(4), null, actor, c);
        for (int i = 1; i <= 10; i++) {
            String st = i <= 5 ? "DONE" : "TODO";
            task(pid, a, "A-" + i + " (10h, " + st + ")", "TASK", st, "MEDIUM",
                    null, 10, base, base.plusWeeks(2), null, actor, c);
        }

        // Nhóm B: 8 lá × 5h, 4 DONE → 50%
        String b = task(pid, null, "Nhóm B (8×5h, 4 DONE → kỳ vọng 50%)", "EPIC", "IN_PROGRESS", "MEDIUM",
                null, 0, base, base.plusWeeks(4), null, actor, c);
        for (int i = 1; i <= 8; i++) {
            String st = i <= 4 ? "DONE" : "IN_PROGRESS";
            task(pid, b, "B-" + i + " (5h, " + st + ")", "TASK", st, "MEDIUM",
                    null, 5, base, base.plusWeeks(2), null, actor, c);
        }

        // Nhóm C: est lệch nhau. Lá: 20,10,10,10,5,5 = 60h. DONE: 20,10,10 = 40h → 66.67%
        String cc = task(pid, null, "Nhóm C (est lệch; DONE 40/60h → kỳ vọng 66.67%)", "EPIC", "IN_PROGRESS", "HIGH",
                null, 0, base, base.plusWeeks(6), null, actor, c);
        task(pid, cc, "C-1 (20h, DONE)", "TASK", "DONE", "HIGH", null, 20, base, base.plusWeeks(3), null, actor, c);
        task(pid, cc, "C-2 (10h, DONE)", "TASK", "DONE", "MEDIUM", null, 10, base, base.plusWeeks(3), null, actor, c);
        task(pid, cc, "C-3 (10h, DONE)", "TASK", "DONE", "MEDIUM", null, 10, base, base.plusWeeks(3), null, actor, c);
        task(pid, cc, "C-4 (10h, TODO)", "TASK", "TODO", "MEDIUM", null, 10, base, base.plusWeeks(4), null, actor, c);
        task(pid, cc, "C-5 (5h, TODO)", "TASK", "TODO", "LOW", null, 5, base, base.plusWeeks(4), null, actor, c);
        task(pid, cc, "C-6 (5h, BACKLOG)", "TASK", "BACKLOG", "LOW", null, 5, base, base.plusWeeks(5), null, actor, c);

        // 4 lá ĐỘC LẬP (không cha) × 10h, 2 DONE → 20/40h
        task(pid, null, "S-1 (10h, DONE, độc lập)", "TASK", "DONE", "MEDIUM", null, 10, base, base.plusWeeks(1), null, actor, c);
        task(pid, null, "S-2 (10h, DONE, độc lập)", "TASK", "DONE", "MEDIUM", null, 10, base, base.plusWeeks(1), null, actor, c);
        task(pid, null, "S-3 (10h, IN_REVIEW, độc lập)", "TASK", "IN_REVIEW", "MEDIUM", null, 10, base, base.plusWeeks(2), null, actor, c);
        task(pid, null, "S-4 (10h, TODO, độc lập)", "TASK", "TODO", "MEDIUM", null, 10, base, base.plusWeeks(2), null, actor, c);
    }

    // ===================== helpers gọi SERVICE THẬT =====================

    private interface SeedAction {
        void run();
    }

    /** Bọc try/catch theo từng dự án để 1 lỗi không chặn phần còn lại. Trả true nếu chạy không lỗi. */
    private boolean seedSafely(String code, SeedAction action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            log.warn("[ProjectDemoSeeder] Seed dự án {} gặp lỗi (bỏ qua, tiếp tục dự án khác): {}", code, e.toString());
            return false;
        }
    }

    private String createProject(String code, String name, String desc, String status,
                                 LocalDate start, LocalDate due, String ownerUserId, String actor, Counter c) {
        ProjectDto.ProjectResponse p = projectService.create(new ProjectDto.ProjectRequest(
                code, name, desc, status, fmt(start), fmt(due), ownerUserId, null, null), actor);
        c.projects++;
        return p.id();
    }

    /** Thêm thành viên KHÔNG ngày (vd dự án QA). */
    private void addMember(String projectId, String userId, String role, String actor, Counter c) {
        addMember(projectId, userId, role, null, null, actor, c);
    }

    /**
     * Thêm thành viên VỚI khoảng [start, end] thật → manday > 0. Mỗi người 1 khoảng khác nhau
     * (nhờ caller truyền các mốc lệch nhau) để màn thành viên / báo cáo có dữ liệu phong phú.
     */
    private void addMember(String projectId, String userId, String role,
                           LocalDate start, LocalDate end, String actor, Counter c) {
        try {
            // effort 50% cho dữ liệu mẫu (một người có thể tham gia tối đa ~2 dự án mà không vượt 100%).
            projectService.addMember(projectId, userId, role, fmt(start), fmt(end), 50, actor);
            c.members++;
        } catch (IllegalArgumentException ex) {
            // Bỏ qua nếu trùng người (vd PM cũng là LEAD do thiếu vai trò) — không nhân đôi.
            log.debug("[ProjectDemoSeeder] Bỏ qua thành viên {} ({}): {}", userId, role, ex.getMessage());
        }
    }

    /** Tạo task qua service thật (KHÔNG mô tả riêng — sinh mô tả mặc định). Trả id để làm cha. */
    private String task(String projectId, String parentId, String title, String type, String status,
                        String priority, String assigneeUserId, double estimateHours,
                        LocalDate start, LocalDate due, String screen, String actor, Counter c) {
        return task(projectId, parentId, title, null, type, status, priority, assigneeUserId,
                estimateHours, start, due, screen, actor, c);
    }

    /**
     * Tạo task qua service thật + LÀM GIÀU dữ liệu cho task LÁ có estimate:
     * <ul>
     *   <li>description: dùng {@code desc} nếu có, else sinh mô tả mặc định theo tiêu đề.</li>
     *   <li>spentHours: với task LÁ đang/đã làm (IN_PROGRESS/IN_REVIEW/DONE) gọi {@code logWork}
     *       để có spent > 0 (DONE ≈ 95% est, đang làm ≈ 45% est) — phục vụ so sánh est vs spent.</li>
     * </ul>
     * Task cha (estimate = 0, EPIC/STORY) coi như "không lá" → không log work.
     */
    private String task(String projectId, String parentId, String title, String desc, String type, String status,
                        String priority, String assigneeUserId, double estimateHours,
                        LocalDate start, LocalDate due, String screen, String actor, Counter c) {
        String description = desc != null ? desc : defaultDesc(title, type);
        ProjectDto.TaskResponse t = taskService.create(projectId, new ProjectDto.TaskRequest(
                parentId, title, description, type, status, priority, assigneeUserId,
                estimateHours, fmt(start), fmt(due), null, screen,
                null, null, null, null, null), actor);
        c.tasks++;
        // Spent giờ: chỉ cho task có estimate (lá) đang/đã làm → spent > 0.
        if (estimateHours > 0) {
            double ratio = switch (status) {
                case "DONE" -> 0.95;
                case "IN_REVIEW" -> 0.7;
                case "IN_PROGRESS" -> 0.45;
                default -> 0.0;
            };
            double spent = Math.round(estimateHours * ratio * 10.0) / 10.0;
            if (spent > 0) {
                try {
                    taskService.logWork(projectId, t.id(), spent, actor);
                } catch (Exception ex) {
                    log.debug("[ProjectDemoSeeder] Bỏ qua log work cho {}: {}", t.id(), ex.getMessage());
                }
            }
        }
        return t.id();
    }

    /** Mô tả mặc định cho task seed (để màn chi tiết không trống). */
    private static String defaultDesc(String title, String type) {
        return switch (type) {
            case "EPIC" -> "Nhóm công việc lớn: " + title + ". Bao gồm nhiều story/task con.";
            case "STORY" -> "Story: " + title + ". Mô tả yêu cầu nghiệp vụ & tiêu chí nghiệm thu.";
            case "BUG" -> "Lỗi: " + title + ". Cần tái hiện, xác định nguyên nhân và vá.";
            default -> "Công việc: " + title + ". Thực hiện theo yêu cầu và cập nhật tiến độ.";
        };
    }

    /** Thêm vài bình luận mẫu vào một task (cho màn chi tiết có dữ liệu). Bọc lỗi, không chặn seed. */
    private void comment(String projectId, String taskId, String actor, String... bodies) {
        for (String body : bodies) {
            try {
                collabService.addComment(projectId, taskId, body, actor);
            } catch (Exception ex) {
                log.debug("[ProjectDemoSeeder] Bỏ qua bình luận task {}: {}", taskId, ex.getMessage());
            }
        }
    }

    /** Bug = task type=BUG (đếm riêng cho tóm tắt). */
    private String bug(String projectId, String parentId, String title, String status, String priority,
                       String assigneeUserId, double estimateHours, LocalDate start, LocalDate due,
                       String screen, String actor, Counter c) {
        String id = task(projectId, parentId, title, "BUG", status, priority,
                assigneeUserId, estimateHours, start, due, screen, actor, c);
        c.bugs++;
        return id;
    }

    private static String fmt(LocalDate d) {
        return d == null ? null : d.format(DMY);
    }

    private List<Employee> byRole(List<Employee> active, String roleCode) {
        List<Employee> out = new ArrayList<>();
        for (Employee e : active) {
            if (roleService.roleCodesForUser(e.getUserAccountId()).contains(roleCode)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Vòng tròn chọn người trong một nhóm (rải qua nhiều người thật). */
    private final class Picker {
        private final List<Employee> list;
        private int i = 0;
        Picker(List<Employee> list) { this.list = list; }
        /** userAccountId của người kế tiếp. */
        String nextUser() {
            Employee e = list.get((i++) % list.size());
            // Trả userId của tài khoản (= userAccountId). Đảm bảo tồn tại tài khoản; fallback userAccountId thô.
            return userRepo.findById(e.getUserAccountId()).map(UserAccount::getId).orElse(e.getUserAccountId());
        }
    }

    /** Bộ đếm dùng chung qua các dự án. */
    private static final class Counter {
        int projects, members, tasks, bugs;
    }
}
