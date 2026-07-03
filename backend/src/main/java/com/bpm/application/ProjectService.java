package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.AccountStatus;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectRoleInProject;
import com.bpm.domain.project.ProjectStatus;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.TaskActivityRepository;
import com.bpm.infrastructure.TaskAttachmentRepository;
import com.bpm.infrastructure.TaskCommentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quản lý dự án (mini-Jira): CRUD project, thành viên, báo cáo, tính tỷ lệ hoàn thành.
 * Conventions: validate ném {@link IllegalArgumentException} (→ 400 ở tầng API), mọi mutation ghi {@link AuditPort}.
 *
 * <p>Tỷ lệ hoàn thành dựa trên <b>task lá</b> (không có con):
 * {@code completionPct = Σ estimateHours(task lá DONE) / Σ estimateHours(tất cả task lá)};
 * nếu tổng estimate = 0 thì đếm: doneLá / tổng task lá.
 */
@Service
public class ProjectService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final ProjectTaskRepository taskRepo;
    private final TaskCommentRepository commentRepo;
    private final TaskAttachmentRepository attachmentRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final AuditPort auditPort;
    private final TaskActivityRepository activityRepo;
    private final NotificationService notificationService;

    public ProjectService(ProjectRepository projectRepo, ProjectMemberRepository memberRepo,
                          ProjectTaskRepository taskRepo, TaskCommentRepository commentRepo,
                          TaskAttachmentRepository attachmentRepo, UserAccountRepository userRepo,
                          EmployeeRepository employeeRepo, AuditPort auditPort,
                          TaskActivityRepository activityRepo, NotificationService notificationService) {
        this.projectRepo = projectRepo;
        this.memberRepo = memberRepo;
        this.taskRepo = taskRepo;
        this.commentRepo = commentRepo;
        this.attachmentRepo = attachmentRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.auditPort = auditPort;
        this.activityRepo = activityRepo;
        this.notificationService = notificationService;
    }

    // ===== CRUD project =====

    /**
     * Danh sách dự án LỌC THEO QUYỀN: admin thấy tất cả; người thường chỉ thấy dự án mình là
     * owner HOẶC thành viên (bất kỳ vai trò). actor = username; isAdmin suy từ authorities ROLE_ADMIN.
     */
    @Transactional(readOnly = true)
    public List<ProjectDto.ProjectResponse> list(String actor, boolean isAdmin) {
        String myId = userIdOf(actor);
        List<ProjectDto.ProjectResponse> out = new ArrayList<>();
        for (Project p : projectRepo.findAllByOrderByCreatedAtDesc()) {
            if (!isAdmin && !isMemberOrOwner(p, myId)) {
                continue;
            }
            List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(p.getId());
            out.add(ProjectDto.ProjectResponse.of(p, ownerName(p.getOwnerUserId()),
                    completionPct(tasks), (int) memberRepo.countByProjectId(p.getId()), tasks.size()));
        }
        return out;
    }

    // ===== Phân quyền & khả kiến dự án =====

    /**
     * Có quyền QUẢN LÝ dự án (sửa/xoá dự án, thêm/gỡ thành viên, tạo/sửa/xoá/reorder task):
     * admin / owner / thành viên vai trò PM. Vi phạm ⇒ 403.
     */
    @Transactional(readOnly = true)
    public void requireCanManage(String projectId, String actor, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Project p = get(projectId);
        String myId = userIdOf(actor);
        if (myId != null) {
            if (myId.equals(p.getOwnerUserId())) {
                return;
            }
            ProjectMember m = memberRepo.findByProjectIdAndUserId(projectId, myId).orElse(null);
            if (m != null && m.getRoleInProject() == ProjectRoleInProject.PM) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý dự án này");
    }

    /**
     * Là THÀNH VIÊN dự án (admin / owner / thành viên bất kỳ vai trò): được xem dự án/task,
     * đổi status/assign task, bình luận, đính kèm, log work. Vi phạm ⇒ 403.
     */
    @Transactional(readOnly = true)
    public void requireMember(String projectId, String actor, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Project p = get(projectId);
        String myId = userIdOf(actor);
        if (myId != null && isMemberOrOwner(p, myId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập dự án này");
    }

    private boolean isMemberOrOwner(Project p, String myId) {
        if (myId == null) {
            return false;
        }
        if (myId.equals(p.getOwnerUserId())) {
            return true;
        }
        return memberRepo.existsByProjectIdAndUserId(p.getId(), myId);
    }

    private String userIdOf(String username) {
        if (username == null) {
            return null;
        }
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }

    /** Việc của TÔI xuyên dự án (màn cá nhân) — task có assignee = người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<ProjectDto.MyTaskResponse> myTasks(String actor) {
        com.bpm.domain.UserAccount me = userRepo.findByUsername(actor).orElse(null);
        if (me == null) {
            return new ArrayList<>();
        }
        java.util.Map<String, Project> cache = new java.util.HashMap<>();
        List<ProjectDto.MyTaskResponse> out = new ArrayList<>();
        if (me.isAdmin()) {
            // ADMIN (oversight — xem mọi data): tất cả task ĐÃ GIAO trên MỌI dự án; empCode theo assignee.
            java.util.Map<String, String> codeByUser = new java.util.HashMap<>();
            for (Employee e : employeeRepo.findAll()) {
                if (e.getUserAccountId() != null) codeByUser.put(e.getUserAccountId(), e.getEmpCode());
            }
            for (ProjectTask t : taskRepo.findAll()) {
                if (t.getAssigneeUserId() == null) continue;
                Project p = cache.computeIfAbsent(t.getProjectId(), id -> projectRepo.findById(id).orElse(null));
                if (p == null) continue;
                double prog = t.getStatus() == com.bpm.domain.project.TaskStatus.DONE ? 100.0 : 0.0;
                out.add(ProjectDto.MyTaskResponse.of(t, p.getCode(), p.getName(), prog, codeByUser.get(t.getAssigneeUserId())));
            }
            return out;
        }
        // Assignee các task này luôn là người đang đăng nhập → resolve empCode 1 lần.
        String myCode = employeeRepo.findByUserAccountId(me.getId()).map(Employee::getEmpCode).orElse(null);
        for (ProjectTask t : taskRepo.findByAssigneeUserId(me.getId())) {
            Project p = cache.computeIfAbsent(t.getProjectId(), id -> projectRepo.findById(id).orElse(null));
            if (p == null) {
                continue;
            }
            double prog = t.getStatus() == com.bpm.domain.project.TaskStatus.DONE ? 100.0 : 0.0;
            out.add(ProjectDto.MyTaskResponse.of(t, p.getCode(), p.getName(), prog, myCode));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Project get(String id) {
        return projectRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án"));
    }

    @Transactional(readOnly = true)
    public ProjectDto.ProjectResponse detail(String id) {
        Project p = get(id);
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(id);
        List<ProjectMember> members = memberRepo.findByProjectIdOrderByJoinedAtAsc(id);
        return ProjectDto.ProjectResponse.of(p, ownerName(p.getOwnerUserId()),
                completionPct(tasks), members.size(), tasks.size(), totalEffortMM(members));
    }

    /** Tổng nỗ lực man-month = Σ(member.manday) / 22 ngày công/tháng (làm tròn 2 chữ số). */
    private static double totalEffortMM(List<ProjectMember> members) {
        int totalManday = members.stream().mapToInt(ProjectMember::manday).sum();
        return round2(totalManday / 22.0);
    }

    @Transactional
    public ProjectDto.ProjectResponse create(ProjectDto.ProjectRequest req, String actor) {
        String code = require(req.code(), "mã dự án").toUpperCase();
        if (projectRepo.existsByCode(code)) {
            throw new IllegalArgumentException("Mã dự án đã tồn tại: " + code);
        }
        Project p = new Project(code, require(req.name(), "tên dự án"), blankToNull(req.ownerUserId()));
        p.apply(p.getName(), blankToNull(req.description()), parseStatus(req.status(), ProjectStatus.PLANNING),
                parseDate(req.startDate(), "ngày bắt đầu"), parseDate(req.dueDate(), "ngày kết thúc"),
                blankToNull(req.ownerUserId()), req.budget(), req.plannedEffortMm());
        Project saved = projectRepo.save(p);
        auditPort.record("PROJECT_CREATED", "Project", saved.getId(), actor, "code=" + saved.getCode());
        return detail(saved.getId());
    }

    @Transactional
    public ProjectDto.ProjectResponse update(String id, ProjectDto.ProjectRequest req, String actor) {
        Project p = get(id);
        p.apply(require(req.name(), "tên dự án"), blankToNull(req.description()),
                parseStatus(req.status(), p.getStatus()),
                parseDate(req.startDate(), "ngày bắt đầu"), parseDate(req.dueDate(), "ngày kết thúc"),
                blankToNull(req.ownerUserId()), req.budget(), req.plannedEffortMm());
        projectRepo.save(p);
        auditPort.record("PROJECT_UPDATED", "Project", p.getId(), actor, "code=" + p.getCode());
        return detail(id);
    }

    @Transactional
    public void delete(String id, String actor) {
        Project p = get(id);
        for (ProjectTask t : taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(id)) {
            commentRepo.deleteByTaskId(t.getId());
            attachmentRepo.deleteByTaskId(t.getId());
            activityRepo.deleteByTaskId(t.getId());
        }
        taskRepo.deleteByProjectId(id);
        memberRepo.deleteByProjectId(id);
        projectRepo.delete(p);
        auditPort.record("PROJECT_DELETED", "Project", id, actor, "code=" + p.getCode());
    }

    // ===== Thành viên =====

    @Transactional(readOnly = true)
    public List<ProjectDto.MemberResponse> listMembers(String projectId) {
        get(projectId);
        List<ProjectDto.MemberResponse> out = new ArrayList<>();
        for (ProjectMember m : memberRepo.findByProjectIdOrderByJoinedAtAsc(projectId)) {
            out.add(toMemberDto(m));
        }
        return out;
    }

    @Transactional
    public ProjectDto.MemberResponse addMember(String projectId, String userId, String role,
                                               String startDate, String endDate, Integer effortPct, String actor) {
        get(projectId);
        String uid = require(userId, "người dùng");
        UserAccount acc = userRepo.findById(uid)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng"));
        if (memberRepo.existsByProjectIdAndUserId(projectId, uid)) {
            throw new IllegalArgumentException("Người dùng đã là thành viên dự án");
        }
        // % effort: mặc định 100, kẹp 1–100. Tổng effort của nhân sự qua MỌI dự án không vượt 100%.
        int newEffort = (effortPct == null) ? 100 : Math.max(1, Math.min(100, effortPct));
        List<ProjectMember> otherMemberships = memberRepo.findByUserId(uid);
        int usedEffort = otherMemberships.stream().mapToInt(ProjectMember::getEffortPct).sum();
        if (usedEffort + newEffort > 100) {
            StringBuilder sb = new StringBuilder();
            for (ProjectMember pm : otherMemberships) {
                String pname = projectRepo.findById(pm.getProjectId()).map(Project::getName).orElse(pm.getProjectId());
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(pname).append(" (").append(pm.getEffortPct()).append("%)");
            }
            throw new IllegalArgumentException(
                    "Nhân sự đã phân bổ " + usedEffort + "% effort ở: " + sb
                            + ". Thêm " + newEffort + "% sẽ vượt 100%. Hãy giảm % effort hoặc gỡ bớt dự án khác.");
        }
        ProjectMember m = new ProjectMember(projectId, acc.getId(), parseRole(role, ProjectRoleInProject.MEMBER));
        m.setStartDate(parseDate(startDate, "ngày bắt đầu"));
        m.setEndDate(parseDate(endDate, "ngày kết thúc"));
        m.setEffortPct(newEffort);
        ProjectMember saved = memberRepo.save(m);
        auditPort.record("PROJECT_MEMBER_ADDED", "ProjectMember", saved.getId(), actor,
                "projectId=" + projectId + ", userId=" + uid + ", role=" + saved.getRoleInProject());
        // Thông báo cho người được thêm (không tự báo cho người thao tác).
        Project proj = get(projectId);
        if (!uid.equals(userIdOf(actor))) {
            safeNotify(uid, "PROJECT_MEMBER_ADDED", "Bạn được thêm vào dự án",
                    "Bạn được thêm vào dự án " + proj.getName(), "/projects/" + projectId);
        }
        return toMemberDto(saved);
    }

    @Transactional
    public void removeMember(String projectId, String memberId, String actor) {
        get(projectId);
        ProjectMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên"));
        if (!m.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Thành viên không thuộc dự án này");
        }
        memberRepo.delete(m);
        auditPort.record("PROJECT_MEMBER_REMOVED", "ProjectMember", memberId, actor,
                "projectId=" + projectId + ", userId=" + m.getUserId());
    }

    /** Sửa thông tin thành viên (vai trò / % effort / ngày). KHÔNG đổi người. */
    @Transactional
    public ProjectDto.MemberResponse updateMember(String projectId, String memberId, String role,
                                                  String startDate, String endDate, Integer effortPct, String actor) {
        get(projectId);
        ProjectMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên"));
        if (!m.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Thành viên không thuộc dự án này");
        }
        // % effort: kẹp 1–100. Tổng effort qua các dự án KHÁC (loại chính membership này) + mới ≤ 100.
        int newEffort = (effortPct == null) ? m.getEffortPct() : Math.max(1, Math.min(100, effortPct));
        int usedEffort = memberRepo.findByUserId(m.getUserId()).stream()
                .filter(pm -> !pm.getId().equals(memberId))
                .mapToInt(ProjectMember::getEffortPct).sum();
        if (usedEffort + newEffort > 100) {
            throw new IllegalArgumentException("Tổng % effort của nhân sự sẽ vượt 100% ("
                    + usedEffort + "% ở dự án khác + " + newEffort + "%). Hãy giảm % effort.");
        }
        if (role != null && !role.isBlank()) {
            m.setRoleInProject(parseRole(role, m.getRoleInProject()));
        }
        m.setStartDate(parseDate(startDate, "ngày bắt đầu"));
        m.setEndDate(parseDate(endDate, "ngày kết thúc"));
        m.setEffortPct(newEffort);
        ProjectMember saved = memberRepo.save(m);
        auditPort.record("PROJECT_MEMBER_UPDATED", "ProjectMember", saved.getId(), actor,
                "projectId=" + projectId + ", role=" + saved.getRoleInProject() + ", effort=" + newEffort);
        return toMemberDto(saved);
    }

    private ProjectDto.MemberResponse toMemberDto(ProjectMember m) {
        UserAccount acc = userRepo.findById(m.getUserId()).orElse(null);
        Employee emp = employeeRepo.findByUserAccountId(m.getUserId()).orElse(null);
        String name = personName(emp, acc, m.getUserId());
        String empCode = emp != null ? emp.getEmpCode() : null;
        String jobPosition = emp != null ? emp.getJobPosition() : null;
        String title = emp != null ? emp.getTitle() : null;
        String deptCode = emp != null ? emp.getDeptCode() : null;
        return ProjectDto.MemberResponse.of(m, name, empCode, jobPosition, title, deptCode);
    }

    /**
     * Tên hiển thị ƯU TIÊN theo HỒ SƠ NHÂN SỰ (Employee.fullName — nguồn DSNS), rồi mới đến tài khoản.
     * Tránh lệch khi sửa tên ở DSNS mà tài khoản (QLTK) chưa đồng bộ.
     */
    static String personName(Employee emp, UserAccount acc, String fallback) {
        if (emp != null && emp.getFullName() != null && !emp.getFullName().isBlank()) {
            return emp.getFullName();
        }
        if (acc != null) {
            return displayName(acc);
        }
        return fallback;
    }

    // ===== Báo cáo / số liệu =====

    @Transactional(readOnly = true)
    public ProjectDto.ReportResponse report(String projectId) {
        get(projectId);
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId);
        Set<String> parentIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                parentIds.add(t.getParentId());
            }
        }

        int totalTasks = tasks.size(), doneTasks = 0;
        int leafTasks = 0, leafDoneTasks = 0, bugCount = 0, overdue = 0;
        double totalEstimate = 0, doneEstimate = 0, totalSpent = 0;   // trên TẤT CẢ task (số liệu chung)
        double leafEstimate = 0, leafDoneEstimate = 0;       // trên task LÁ (dùng tính completion)
        LocalDate today = LocalDate.now();

        Map<String, Integer> byStatus = newEnumMap(TaskStatus.values());
        Map<String, Integer> byType = newEnumMap(TaskType.values());
        Map<String, int[]> assigneeAgg = new LinkedHashMap<>();      // userId -> [total, done]
        Map<String, Double> assigneeEst = new HashMap<>();           // userId -> estimate

        for (ProjectTask t : tasks) {
            boolean done = t.getStatus() == TaskStatus.DONE;
            boolean leaf = !parentIds.contains(t.getId());

            byStatus.merge(t.getStatus().name(), 1, Integer::sum);
            byType.merge(t.getType().name(), 1, Integer::sum);
            totalEstimate += t.getEstimateHours();
            totalSpent += t.getSpentHours();
            if (done) {
                doneTasks++;
                doneEstimate += t.getEstimateHours();
            }
            if (t.getType() == TaskType.BUG) {
                bugCount++;
            }
            if (t.getDueDate() != null && t.getDueDate().isBefore(today) && !done) {
                overdue++;
            }
            if (leaf) {
                leafTasks++;
                leafEstimate += t.getEstimateHours();
                if (done) {
                    leafDoneTasks++;
                    leafDoneEstimate += t.getEstimateHours();
                }
            }
            String uid = t.getAssigneeUserId();
            if (uid != null) {
                int[] agg = assigneeAgg.computeIfAbsent(uid, k -> new int[2]);
                agg[0]++;
                if (done) {
                    agg[1]++;
                }
                assigneeEst.merge(uid, t.getEstimateHours(), Double::sum);
            }
        }

        List<ProjectDto.AssigneeStat> byAssignee = new ArrayList<>();
        for (Map.Entry<String, int[]> e : assigneeAgg.entrySet()) {
            String uid = e.getKey();
            UserAccount acc = userRepo.findById(uid).orElse(null);
            byAssignee.add(new ProjectDto.AssigneeStat(uid, acc != null ? displayName(acc) : uid,
                    e.getValue()[0], e.getValue()[1], assigneeEst.getOrDefault(uid, 0.0)));
        }

        double pct = completionFrom(leafEstimate, leafDoneEstimate, leafTasks, leafDoneTasks);
        return new ProjectDto.ReportResponse(pct, totalTasks, doneTasks, leafTasks, leafDoneTasks,
                round2(totalEstimate), round2(doneEstimate), round2(totalSpent), bugCount, overdue,
                byStatus, byType, byAssignee);
    }

    /** Tỷ lệ hoàn thành (0..100, làm tròn 2 chữ số) từ danh sách task của một dự án. */
    double completionPct(List<ProjectTask> tasks) {
        Set<String> parentIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                parentIds.add(t.getParentId());
            }
        }
        double leafEst = 0, leafDoneEst = 0;
        int leaf = 0, leafDone = 0;
        for (ProjectTask t : tasks) {
            if (parentIds.contains(t.getId())) {
                continue; // không phải lá
            }
            leaf++;
            double w = t.effectiveHours(); // ưu tiên est; không có est → duration ngày công × 8
            leafEst += w;
            if (t.getStatus() == TaskStatus.DONE) {
                leafDone++;
                leafDoneEst += w;
            }
        }
        return completionFrom(leafEst, leafDoneEst, leaf, leafDone);
    }

    private static double completionFrom(double leafEst, double leafDoneEst, int leaf, int leafDone) {
        if (leaf == 0) {
            return 0.0;
        }
        double pct = leafEst > 0 ? (leafDoneEst / leafEst) * 100.0 : ((double) leafDone / leaf) * 100.0;
        return round2(pct);
    }

    // ===== People (chọn thành viên / assignee) =====

    /** Tài khoản đang hoạt động + empCode nếu có Employee liên kết. Sắp xếp theo tên. */
    @Transactional(readOnly = true)
    public List<ProjectDto.PersonResponse> people() {
        Map<String, Employee> empByUser = new HashMap<>();
        for (Employee e : employeeRepo.findAll()) {
            if (e.getUserAccountId() != null) {
                empByUser.put(e.getUserAccountId(), e);
            }
        }
        List<ProjectDto.PersonResponse> out = new ArrayList<>();
        for (UserAccount a : userRepo.findAll()) {
            if (a.getStatus() != AccountStatus.ACTIVE) {
                continue;
            }
            Employee e = empByUser.get(a.getId());
            out.add(new ProjectDto.PersonResponse(a.getId(), personName(e, a, a.getUsername()),
                    e == null ? null : e.getEmpCode(),
                    e == null ? null : e.getJobPosition(),
                    e == null ? null : e.getTitle(),
                    e == null ? null : e.getDeptCode()));
        }
        out.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        return out;
    }

    // ===== helpers =====

    /** Bắn thông báo, bọc try/catch để lỗi thông báo không chặn nghiệp vụ. */
    private void safeNotify(String recipientUserId, String type, String title, String body, String link) {
        try {
            notificationService.notify(recipientUserId, type, title, body, link);
        } catch (Exception ignored) {
            // không chặn nghiệp vụ vì lỗi thông báo
        }
    }

    private String ownerName(String userId) {
        if (userId == null) {
            return null;
        }
        return userRepo.findById(userId).map(ProjectService::displayName).orElse(null);
    }

    static String displayName(UserAccount a) {
        return (a.getFullName() != null && !a.getFullName().isBlank()) ? a.getFullName() : a.getUsername();
    }

    private static Map<String, Integer> newEnumMap(Enum<?>[] values) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Enum<?> v : values) {
            m.put(v.name(), 0);
        }
        return m;
    }

    private static ProjectStatus parseStatus(String s, ProjectStatus fallback) {
        if (blank(s)) {
            return fallback;
        }
        try {
            return ProjectStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Trạng thái dự án không hợp lệ: " + s);
        }
    }

    private static ProjectRoleInProject parseRole(String s, ProjectRoleInProject fallback) {
        if (blank(s)) {
            return fallback;
        }
        try {
            return ProjectRoleInProject.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Vai trò trong dự án không hợp lệ: " + s);
        }
    }

    private static LocalDate parseDate(String v, String label) {
        if (blank(v)) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim(), DMY);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " sai định dạng dd/MM/yyyy (\"" + v + "\")");
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String require(String v, String label) {
        if (blank(v)) {
            throw new IllegalArgumentException("Thiếu " + label);
        }
        return v.trim();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s.trim(); }
}
