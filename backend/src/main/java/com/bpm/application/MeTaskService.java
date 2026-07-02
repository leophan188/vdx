package com.bpm.application;

import com.bpm.api.dto.MeTaskDto;
import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.personal.PersonalTask;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.PersonalTaskRepository;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BACKLOG CÁ NHÂN — nhân sự có task RIÊNG (không thuộc dự án) và có thể thêm task GẮN DỰ ÁN
 * (đồng bộ vào backlog chung, giao cho chính mình). Chủ thể luôn từ Authentication.getName().
 * Task riêng lưu ở {@link PersonalTask}; task gắn dự án tái dùng {@link ProjectTaskService#create}
 * (không viết lại logic seq/orderIndex/rollup).
 */
@Service
public class MeTaskService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PersonalTaskRepository personalRepo;
    private final ProjectMemberRepository memberRepo;
    private final ProjectRepository projectRepo;
    private final UserAccountRepository userRepo;
    private final ProjectTaskRepository taskRepo;
    private final EmployeeRepository employeeRepo;
    private final ProjectTaskService projectTaskService;
    private final AuditPort auditPort;

    public MeTaskService(PersonalTaskRepository personalRepo, ProjectMemberRepository memberRepo,
                         ProjectRepository projectRepo, UserAccountRepository userRepo,
                         ProjectTaskRepository taskRepo, EmployeeRepository employeeRepo,
                         ProjectTaskService projectTaskService, AuditPort auditPort) {
        this.personalRepo = personalRepo;
        this.memberRepo = memberRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
        this.taskRepo = taskRepo;
        this.employeeRepo = employeeRepo;
        this.projectTaskService = projectTaskService;
        this.auditPort = auditPort;
    }

    /** Task RIÊNG của tôi (mới → cũ). */
    @Transactional(readOnly = true)
    public List<MeTaskDto.PersonalTaskView> listPersonal(String actor) {
        UserAccount me = require(actor);
        List<MeTaskDto.PersonalTaskView> out = new ArrayList<>();
        for (PersonalTask t : personalRepo.findByUserIdOrderByCreatedAtDesc(me.getId())) {
            out.add(MeTaskDto.PersonalTaskView.of(t));
        }
        return out;
    }

    /**
     * Dropdown dự án khi tạo nhanh / thêm task gắn dự án.
     * ADMIN (oversight) → MỌI dự án; nhân sự thường → chỉ dự án mình là thành viên.
     */
    @Transactional(readOnly = true)
    public List<MeTaskDto.ProjectOption> myProjects(String actor) {
        UserAccount me = require(actor);
        List<MeTaskDto.ProjectOption> out = new ArrayList<>();
        if (me.isAdmin()) {
            for (Project p : projectRepo.findAllByOrderByCreatedAtDesc()) {
                out.add(new MeTaskDto.ProjectOption(p.getId(), p.getCode(), p.getName()));
            }
            return out;
        }
        for (ProjectMember m : memberRepo.findByUserId(me.getId())) {
            projectRepo.findById(m.getProjectId()).ifPresent(p ->
                    out.add(new MeTaskDto.ProjectOption(p.getId(), p.getCode(), p.getName())));
        }
        return out;
    }

    /**
     * Tạo task.
     * <ul>
     *   <li>projectId RỖNG/null → tạo {@link PersonalTask} (status TODO). Trả về "PERSONAL".</li>
     *   <li>projectId có → KIỂM tài khoản là THÀNH VIÊN dự án; nếu không → ném lỗi.
     *       Nếu có → tạo task trong backlog chung (status BACKLOG, giao cho chính tôi) qua
     *       {@link ProjectTaskService#create}. Trả về "PROJECT".</li>
     * </ul>
     */
    @Transactional
    public MeTaskDto.CreateResult create(String actor, String title, Double estimateHours,
                                         LocalDate deadline, String projectId) {
        UserAccount me = require(actor);
        String cleanTitle = requireTitle(title);
        double est = estimateHours == null ? 0.0 : estimateHours;

        if (blank(projectId)) {
            PersonalTask t = new PersonalTask(me.getId(), displayName(me), cleanTitle, est,
                    deadline, TaskStatus.TODO.name(), null);
            PersonalTask saved = personalRepo.save(t);
            auditPort.record("PERSONAL_TASK_CREATED", "PersonalTask", saved.getId(), actor,
                    "title=" + saved.getTitle());
            return MeTaskDto.CreateResult.personal(MeTaskDto.PersonalTaskView.of(saved));
        }

        String pid = projectId.trim();
        if (!me.isAdmin() && memberRepo.findByProjectIdAndUserId(pid, me.getId()).isEmpty()) {
            throw new IllegalArgumentException("Bạn không thuộc dự án này");
        }
        ProjectDto.TaskRequest req = new ProjectDto.TaskRequest(
                null, cleanTitle, null, "TASK", "BACKLOG", "MEDIUM",
                me.getId(), est, null, deadline == null ? null : deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                null, null, null, null, null, null, null, null);
        ProjectDto.TaskResponse created = projectTaskService.create(pid, req, actor);
        return MeTaskDto.CreateResult.project(created.id(), pid);
    }

    /** Sửa task cá nhân — chỉ chủ sở hữu. */
    @Transactional
    public MeTaskDto.PersonalTaskView updatePersonal(String actor, String id, String title,
                                                     Double estimateHours, LocalDate deadline, String status) {
        PersonalTask t = requireOwned(actor, id);
        t.setTitle(requireTitle(title));
        t.setEstimateHours(estimateHours == null ? 0.0 : estimateHours);
        t.setDeadline(deadline);
        if (!blank(status)) {
            t.setStatus(parseStatus(status));
        }
        t.touch();
        PersonalTask saved = personalRepo.save(t);
        auditPort.record("PERSONAL_TASK_UPDATED", "PersonalTask", saved.getId(), actor,
                "title=" + saved.getTitle());
        return MeTaskDto.PersonalTaskView.of(saved);
    }

    /** Đổi trạng thái task cá nhân — chỉ chủ sở hữu. */
    @Transactional
    public MeTaskDto.PersonalTaskView setPersonalStatus(String actor, String id, String status) {
        PersonalTask t = requireOwned(actor, id);
        t.setStatus(parseStatus(status));
        t.touch();
        PersonalTask saved = personalRepo.save(t);
        auditPort.record("PERSONAL_TASK_STATUS_CHANGED", "PersonalTask", saved.getId(), actor,
                "status=" + saved.getStatus());
        return MeTaskDto.PersonalTaskView.of(saved);
    }

    /** Xóa task cá nhân — chỉ chủ sở hữu. */
    @Transactional
    public void deletePersonal(String actor, String id) {
        PersonalTask t = requireOwned(actor, id);
        personalRepo.delete(t);
        auditPort.record("PERSONAL_TASK_DELETED", "PersonalTask", id, actor, null);
    }

    // ===== Log bug/issue CÁ NHÂN (/api/v1/me/bugs) =====

    /**
     * BUG/ISSUE LIÊN QUAN tôi (xuyên dự án, KHÔNG trùng):
     * (a) tôi LOG (reporterUserId = me) + (b) được GIAO cho tôi (assigneeUserId = me).
     * role = REPORTER | ASSIGNEE | BOTH. Resolve project + tên người theo HỒ SƠ NHÂN SỰ.
     */
    @Transactional(readOnly = true)
    public List<MeTaskDto.MyBugView> myBugs(String actor) {
        UserAccount me = require(actor);
        String meId = me.getId();
        List<ProjectTask> tasks = taskRepo.findRelatedByTypes(meId, List.of(TaskType.BUG, TaskType.ISSUE));

        // Resolve project + tên (assignee/reporter) theo lô, tránh N+1.
        Map<String, Project> projectById = new HashMap<>();
        Map<String, String> nameByUser = new HashMap<>();
        for (ProjectTask t : tasks) {
            projectById.computeIfAbsent(t.getProjectId(), pid -> projectRepo.findById(pid).orElse(null));
            cacheName(nameByUser, t.getAssigneeUserId());
            cacheName(nameByUser, t.getReporterUserId());
            cacheName(nameByUser, t.getTesterUserId());
        }

        List<MeTaskDto.MyBugView> out = new ArrayList<>();
        for (ProjectTask t : tasks) {
            boolean reporter = Objects.equals(t.getReporterUserId(), meId);
            boolean assignee = Objects.equals(t.getAssigneeUserId(), meId);
            String role = (reporter && assignee) ? "BOTH" : (reporter ? "REPORTER" : "ASSIGNEE");
            Project p = projectById.get(t.getProjectId());
            String projectCode = p == null ? null : p.getCode();
            String projectName = p == null ? null : p.getName();
            out.add(new MeTaskDto.MyBugView(
                    t.getId(), t.getProjectId(), projectCode, projectName,
                    projectCode == null ? null : projectCode + "-" + t.getSeq(),
                    t.getTitle(), t.getType().name(), t.getStatus().name(), t.getPriority().name(),
                    t.getAssigneeUserId(), nameByUser.get(t.getAssigneeUserId()),
                    t.getReporterUserId(), nameByUser.get(t.getReporterUserId()),
                    t.getTesterUserId(), nameByUser.get(t.getTesterUserId()),
                    t.getDueDate() == null ? null : t.getDueDate().format(DMY),
                    role));
        }
        return out;
    }

    /**
     * Log BUG/ISSUE cá nhân: KIỂM tôi là thành viên projectId → tạo qua {@link ProjectTaskService#create}
     * (status TODO, priority mặc định MEDIUM, PIC = assigneeUserId). reporterUserId tự set = tôi (trong create).
     */
    @Transactional
    public MeTaskDto.CreateResult logBug(String actor, MeTaskDto.LogBugRequest req) {
        UserAccount me = require(actor);
        if (req == null || blank(req.projectId())) {
            throw new IllegalArgumentException("Thiếu dự án");
        }
        String pid = req.projectId().trim();
        if (!me.isAdmin() && memberRepo.findByProjectIdAndUserId(pid, me.getId()).isEmpty()) {
            throw new IllegalArgumentException("Bạn không thuộc dự án này");
        }
        String type = parseBugType(req.type());
        String cleanTitle = requireTitle(req.title());
        String priority = blank(req.priority()) ? "MEDIUM" : req.priority().trim().toUpperCase();
        double est = req.estimateHours() == null ? 0.0 : req.estimateHours();
        String dueDate = req.dueDate() == null ? null : toDmy(req.dueDate());

        ProjectDto.TaskRequest taskReq = new ProjectDto.TaskRequest(
                blankToNull(req.parentId()), cleanTitle, blankToNull(req.description()), type, "TODO", priority,
                blankToNull(req.assigneeUserId()), est, null, dueDate,
                null, null, null, null, null, null, null, blankToNull(req.testerUserId()));
        ProjectDto.TaskResponse created = projectTaskService.create(pid, taskReq, actor);
        auditPort.record("ME_QUICK_CREATED", "ProjectTask", created.id(), actor,
                "projectId=" + pid + ", type=" + type);
        return MeTaskDto.CreateResult.project(created.id(), pid);
    }

    private void cacheName(Map<String, String> cache, String userId) {
        if (userId == null || userId.isBlank() || cache.containsKey(userId)) {
            return;
        }
        UserAccount acc = userRepo.findById(userId).orElse(null);
        Employee emp = employeeRepo.findByUserAccountId(userId).orElse(null);
        cache.put(userId, ProjectService.personName(emp, acc, userId));
    }

    /** Tạo nhanh: chấp nhận đủ 6 loại (mặc định TASK nếu rỗng); loại khác → lỗi. */
    private static String parseBugType(String s) {
        if (blank(s)) {
            return "TASK";
        }
        String v = s.trim().toUpperCase();
        java.util.Set<String> ok = java.util.Set.of("EPIC", "STORY", "TASK", "SUBTASK", "BUG", "ISSUE");
        if (!ok.contains(v)) {
            throw new IllegalArgumentException("Loại không hợp lệ");
        }
        return v;
    }

    /** Nhận ISO yyyy-MM-dd (hoặc đã là dd/MM/yyyy) → chuẩn dd/MM/yyyy cho TaskRequest. */
    private static String toDmy(String v) {
        String s = v.trim();
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).format(DMY);
        } catch (Exception ignored) {
            // đã là dd/MM/yyyy → giữ nguyên (ProjectTaskService sẽ validate)
            return s;
        }
    }

    private static String blankToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    // ===== helpers =====

    private UserAccount require(String actor) {
        return userRepo.findByUsername(actor)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
    }

    private PersonalTask requireOwned(String actor, String id) {
        UserAccount me = require(actor);
        PersonalTask t = personalRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc"));
        if (!t.getUserId().equals(me.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền với công việc này");
        }
        return t;
    }

    private static String displayName(UserAccount a) {
        if (a.getFullName() != null && !a.getFullName().isBlank()) {
            return a.getFullName();
        }
        return a.getUsername();
    }

    private static String parseStatus(String s) {
        if (blank(s)) {
            throw new IllegalArgumentException("Thiếu trạng thái");
        }
        try {
            return TaskStatus.valueOf(s.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + s);
        }
    }

    private static String requireTitle(String v) {
        if (blank(v)) {
            throw new IllegalArgumentException("Thiếu tiêu đề");
        }
        return v.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
