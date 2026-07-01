package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.BugSeverity;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskActivity;
import com.bpm.domain.project.TaskPriority;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.TaskActivityRepository;
import com.bpm.infrastructure.TaskAttachmentRepository;
import com.bpm.infrastructure.TaskCommentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý công việc dự án (đa cấp). list PHẲNG (FE tự dựng cây). Sinh code "CODE-seq" qua {@link Project#nextSeq()}.
 * Validate: parentId cùng project, không tạo vòng (task không là tổ tiên của chính nó). Mọi mutation ghi audit.
 */
@Service
public class ProjectTaskService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final TaskCommentRepository commentRepo;
    private final TaskAttachmentRepository attachmentRepo;
    private final TaskActivityRepository activityRepo;
    private final AuditPort auditPort;
    private final NotificationService notificationService;

    public ProjectTaskService(ProjectRepository projectRepo, ProjectTaskRepository taskRepo,
                              UserAccountRepository userRepo, EmployeeRepository employeeRepo,
                              TaskCommentRepository commentRepo,
                              TaskAttachmentRepository attachmentRepo, TaskActivityRepository activityRepo,
                              AuditPort auditPort, NotificationService notificationService) {
        this.projectRepo = projectRepo;
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.commentRepo = commentRepo;
        this.attachmentRepo = attachmentRepo;
        this.activityRepo = activityRepo;
        this.auditPort = auditPort;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto.TaskResponse> list(String projectId) {
        Project p = getProject(projectId);
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId);
        Set<String> parentIds = collectParentIds(tasks);
        Map<String, Double> progress = computeProgress(tasks, parentIds);
        // Hiệu năng: resolve toàn bộ người liên quan (assignee + tester) (UserAccount + Employee) bằng 2 query gộp thay vì N+1.
        Set<String> personIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getAssigneeUserId() != null) {
                personIds.add(t.getAssigneeUserId());
            }
            if (t.getTesterUserId() != null && !t.getTesterUserId().isBlank()) {
                personIds.add(t.getTesterUserId());
            }
        }
        Map<String, UserAccount> userById = new HashMap<>();
        Map<String, Employee> empByUser = new HashMap<>();
        if (!personIds.isEmpty()) {
            for (UserAccount a : userRepo.findAllById(personIds)) {
                userById.put(a.getId(), a);
            }
            for (Employee e : employeeRepo.findByUserAccountIdIn(personIds)) {
                empByUser.put(e.getUserAccountId(), e);
            }
        }
        Map<String, ProjectTask> byId = new HashMap<>();
        for (ProjectTask t : tasks) {
            byId.put(t.getId(), t);
        }
        List<ProjectDto.TaskResponse> out = new ArrayList<>();
        for (ProjectTask t : tasks) {
            boolean leaf = !parentIds.contains(t.getId());
            out.add(toDto(t, p.getCode(), leaf, progress.getOrDefault(t.getId(), 0.0),
                    userById, empByUser, parentChainOf(t, byId, p.getCode())));
        }
        return out;
    }

    /** Chuỗi cha gốc→cha trực tiếp (Epic › Story › Task cha) của 1 task. */
    private List<ProjectDto.ParentRef> parentChainOf(ProjectTask t, Map<String, ProjectTask> byId, String projectCode) {
        java.util.LinkedList<ProjectDto.ParentRef> chain = new java.util.LinkedList<>();
        String pid = t.getParentId();
        int guard = 0;
        while (pid != null && guard++ < 12) {
            ProjectTask par = byId.get(pid);
            if (par == null) {
                break;
            }
            chain.addFirst(new ProjectDto.ParentRef(par.getType().name(),
                    projectCode + "-" + par.getSeq(), par.getTitle()));
            pid = par.getParentId();
        }
        return chain;
    }

    /** Dựng TaskResponse với assignee đã resolve sẵn (dùng cho list — tránh query từng task). */
    private ProjectDto.TaskResponse toDto(ProjectTask t, String projectCode, boolean leaf, double progressPct,
                                          Map<String, UserAccount> userById, Map<String, Employee> empByUser,
                                          List<ProjectDto.ParentRef> parentChain) {
        String uid = t.getAssigneeUserId();
        String name = null, code = null, position = null, dept = null;
        if (uid != null) {
            UserAccount acc = userById.get(uid);
            Employee emp = empByUser.get(uid);
            name = ProjectService.personName(emp, acc, uid); // tên theo HỒ SƠ NHÂN SỰ (DSNS)
            if (emp != null) {
                code = emp.getEmpCode();
                position = emp.getJobPosition();
                dept = emp.getDeptCode();
            } else {
                code = acc != null ? acc.getUsername() : uid; // không có Employee → dùng username làm mã
            }
        }
        // Tên người kiểm thử — resolve từ batch map (tránh N+1).
        String testerName = null;
        String tid = t.getTesterUserId();
        if (tid != null && !tid.isBlank()) {
            testerName = ProjectService.personName(empByUser.get(tid), userById.get(tid), tid);
        }
        return ProjectDto.TaskResponse.of(t, projectCode, name, code, position, dept, leaf, progressPct,
                parentChain, reporterName(t), testerName);
    }

    /**
     * % hoàn thành mỗi task (rollup theo est của LÁ).
     * <ul>
     *   <li>Task LÁ: DONE → 100, khác → 0.</li>
     *   <li>Task CHA: Σ(est lá DONE trong cây con) / Σ(est lá trong cây con) × 100;
     *       nếu Σ(est lá)=0 → (số lá DONE / tổng số lá) × 100. Làm tròn 2 chữ số.</li>
     * </ul>
     * Cùng cách tính với {@code ProjectService.completionPct} nên project.completionPct nhất quán.
     */
    private Map<String, Double> computeProgress(List<ProjectTask> tasks, Set<String> parentIds) {
        // children theo parentId
        Map<String, List<ProjectTask>> children = new HashMap<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                children.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<String, Double> pct = new HashMap<>();
        for (ProjectTask t : tasks) {
            if (parentIds.contains(t.getId())) {
                continue; // CHA — tính sau qua đệ quy bottom-up
            }
            pct.put(t.getId(), t.getStatus() == TaskStatus.DONE ? 100.0 : 0.0);
        }
        for (ProjectTask t : tasks) {
            if (t.getParentId() == null) {
                rollup(t, children, pct); // duyệt từ rễ, fill toàn cây
            }
        }
        return pct;
    }

    /** Trả [Σ est lá, Σ est lá DONE, số lá, số lá DONE] của cây con tại {@code t}; đồng thời set pct cho mọi node. */
    private double[] rollup(ProjectTask t, Map<String, List<ProjectTask>> children, Map<String, Double> pct) {
        List<ProjectTask> kids = children.get(t.getId());
        if (kids == null || kids.isEmpty()) {
            // LÁ
            boolean done = t.getStatus() == TaskStatus.DONE;
            double est = t.getEstimateHours();
            pct.put(t.getId(), done ? 100.0 : 0.0);
            return new double[]{est, done ? est : 0.0, 1, done ? 1 : 0};
        }
        double leafEst = 0, leafDoneEst = 0, leaf = 0, leafDone = 0;
        for (ProjectTask k : kids) {
            double[] r = rollup(k, children, pct);
            leafEst += r[0];
            leafDoneEst += r[1];
            leaf += r[2];
            leafDone += r[3];
        }
        double p;
        if (leaf == 0) {
            p = 0.0;
        } else if (leafEst > 0) {
            p = (leafDoneEst / leafEst) * 100.0;
        } else {
            p = (leafDone / leaf) * 100.0;
        }
        pct.put(t.getId(), round2(p));
        return new double[]{leafEst, leafDoneEst, leaf, leafDone};
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Transactional
    public ProjectDto.TaskResponse create(String projectId, ProjectDto.TaskRequest req, String actor) {
        Project p = getProject(projectId);
        String parentId = blankToNull(req.parentId());
        if (parentId != null) {
            requireSameProjectTask(projectId, parentId); // cha phải cùng dự án
        }
        ProjectTask t = new ProjectTask(projectId, p.nextSeq(), require(req.title(), "tiêu đề"), actor);
        applyFields(t, req, parentId);
        t.setReporterUserId(userIdOf(actor)); // người LOG = actor (UserAccount id); dùng cho auto-reassign bug
        projectRepo.save(p); // lưu seq mới
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_CREATED", "ProjectTask", saved.getId(), actor,
                "projectId=" + projectId + ", code=" + p.getCode() + "-" + saved.getSeq());
        recordActivity(saved, actor, TaskActivity.CREATED, "Tạo công việc " + code(p, saved));
        // Nếu tạo task có sẵn assignee → báo người được giao (trừ chính người thao tác).
        if (saved.getAssigneeUserId() != null) {
            notifyAssign(saved, p, code(p, saved), actor);
        }
        rollupFromParent(projectId, saved.getParentId(), actor); // thêm con → cập nhật trạng thái cha
        return toDto(saved, p.getCode(), true);
    }

    @Transactional
    public ProjectDto.TaskResponse update(String projectId, String taskId, ProjectDto.TaskRequest req, String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        String parentId = blankToNull(req.parentId());
        if (parentId != null) {
            if (parentId.equals(taskId)) {
                throw new IllegalArgumentException("Công việc không thể là cha của chính nó");
            }
            requireSameProjectTask(projectId, parentId);
            if (createsCycle(taskId, parentId)) {
                throw new IllegalArgumentException("Không thể đặt cha tạo thành vòng cha-con");
            }
        }
        String oldAssignee = t.getAssigneeUserId();
        TaskStatus oldStatus = t.getStatus();
        applyFields(t, req, parentId);
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_UPDATED", "ProjectTask", saved.getId(), actor,
                "projectId=" + projectId + ", code=" + p.getCode() + "-" + saved.getSeq());
        recordActivity(saved, actor, TaskActivity.EDIT, "Sửa công việc " + code(p, saved));
        // Trạng thái đổi qua PUT cũng ghi nhận + thông báo assignee.
        if (saved.getStatus() != oldStatus) {
            recordActivity(saved, actor, TaskActivity.STATUS,
                    oldStatus.name() + " → " + saved.getStatus().name());
            notifyStatus(saved, p, code(p, saved), actor);
            rollupFromParent(projectId, saved.getParentId(), actor); // trạng thái đổi qua sửa → cập nhật cha
        }
        // Người được giao thay đổi → ghi ASSIGN + báo người mới.
        String newAssignee = saved.getAssigneeUserId();
        if (!java.util.Objects.equals(oldAssignee, newAssignee)) {
            recordActivity(saved, actor, TaskActivity.ASSIGN, assignDetail(newAssignee));
            if (newAssignee != null) {
                notifyAssign(saved, p, code(p, saved), actor);
            }
        }
        return toDto(saved, p.getCode(), isLeaf(projectId, taskId));
    }

    @Transactional
    public ProjectDto.TaskResponse updateStatus(String projectId, String taskId, String status, String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        TaskStatus oldStatus = t.getStatus();
        TaskStatus newStatus = parseStatus(status);
        t.setStatus(newStatus);
        t.touch();
        // KHÔNG đổi assignee: người thực hiện (lập trình) + người kiểm thử (tester) + người log (reporter)
        // là 3 field RIÊNG BIỆT, GIỮ NGUYÊN qua các trạng thái. FE hiển thị "chủ hiện tại" theo status
        // (Đang làm→lập trình, Kiểm thử→kiểm thử/tester, bug Kiểm thử→người log).
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_STATUS_CHANGED", "ProjectTask", saved.getId(), actor,
                "status=" + saved.getStatus());
        if (saved.getStatus() != oldStatus) {
            recordActivity(saved, actor, TaskActivity.STATUS,
                    oldStatus.name() + " → " + saved.getStatus().name());
            notifyStatus(saved, p, code(p, saved), actor);
            rollupFromParent(projectId, saved.getParentId(), actor); // tự cập nhật trạng thái Epic/Story/cha
        }
        return toDto(saved, p.getCode(), isLeaf(projectId, taskId));
    }

    /**
     * Tự động cập nhật trạng thái các task CHA (Epic/Story/task cha) — đi từ {@code startParentId} lên gốc.
     * Quy tắc: tất cả con DONE → DONE; có con đang làm/review hoặc đã có con DONE (chưa xong hết) → IN_PROGRESS;
     * chưa khởi động nhưng có con TODO → TODO; tất cả con BACKLOG → BACKLOG. (Refetch phản ánh thay đổi đã lưu.)
     */
    private void rollupFromParent(String projectId, String startParentId, String actor) {
        if (startParentId == null) {
            return;
        }
        List<ProjectTask> all = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId);
        Map<String, ProjectTask> byId = new HashMap<>();
        Map<String, List<ProjectTask>> childrenOf = new HashMap<>();
        for (ProjectTask t : all) {
            byId.put(t.getId(), t);
            if (t.getParentId() != null) {
                childrenOf.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        String pid = startParentId;
        int guard = 0;
        while (pid != null && guard++ < 20) {
            ProjectTask parent = byId.get(pid);
            if (parent == null) {
                break;
            }
            TaskStatus derived = rollupStatus(childrenOf.getOrDefault(pid, List.of()));
            if (derived != null && derived != parent.getStatus()) {
                TaskStatus old = parent.getStatus();
                parent.setStatus(derived);
                parent.touch();
                taskRepo.save(parent);
                recordActivity(parent, actor, TaskActivity.STATUS,
                        old.name() + " → " + derived.name() + " (tự tổng hợp từ task con)");
                auditPort.record("PROJECT_TASK_STATUS_ROLLUP", "ProjectTask", parent.getId(), actor,
                        "status=" + derived);
            }
            pid = parent.getParentId();
        }
    }

    /** Trạng thái tổng hợp của 1 task cha theo danh sách con trực tiếp (null nếu không có con). */
    private TaskStatus rollupStatus(List<ProjectTask> kids) {
        if (kids.isEmpty()) {
            return null;
        }
        boolean allDone = true, allBacklog = true, anyStartedOrDone = false, anyTodo = false;
        for (ProjectTask k : kids) {
            TaskStatus s = k.getStatus();
            if (s != TaskStatus.DONE) {
                allDone = false;
            }
            if (s != TaskStatus.BACKLOG) {
                allBacklog = false;
            }
            if (s == TaskStatus.IN_PROGRESS || s == TaskStatus.IN_REVIEW || s == TaskStatus.DONE) {
                anyStartedOrDone = true;
            }
            if (s == TaskStatus.TODO) {
                anyTodo = true;
            }
        }
        if (allDone) {
            return TaskStatus.DONE;
        }
        if (anyStartedOrDone) {
            return TaskStatus.IN_PROGRESS;
        }
        if (anyTodo) {
            return TaskStatus.TODO;
        }
        return allBacklog ? TaskStatus.BACKLOG : TaskStatus.TODO;
    }

    @Transactional
    public ProjectDto.TaskResponse assign(String projectId, String taskId, String assigneeUserId, String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        String uid = blankToNull(assigneeUserId);
        if (uid != null && userRepo.findById(uid).isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy người được giao");
        }
        String oldAssignee = t.getAssigneeUserId();
        t.setAssigneeUserId(uid);
        t.touch();
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_ASSIGNED", "ProjectTask", saved.getId(), actor,
                "assigneeUserId=" + uid);
        if (!java.util.Objects.equals(oldAssignee, uid)) {
            recordActivity(saved, actor, TaskActivity.ASSIGN, assignDetail(uid));
            if (uid != null) {
                notifyAssign(saved, p, code(p, saved), actor);
            }
        }
        return toDto(saved, p.getCode(), isLeaf(projectId, taskId));
    }

    @Transactional
    public void reorder(String projectId, ProjectDto.ReorderRequest req, String actor) {
        getProject(projectId);
        if (req == null || req.items() == null) {
            return;
        }
        for (ProjectDto.ReorderItem item : req.items()) {
            ProjectTask t = requireSameProjectTask(projectId, item.taskId());
            String parentId = blankToNull(item.parentId());
            if (parentId != null) {
                if (parentId.equals(t.getId())) {
                    throw new IllegalArgumentException("Công việc không thể là cha của chính nó");
                }
                requireSameProjectTask(projectId, parentId);
                if (createsCycle(t.getId(), parentId)) {
                    throw new IllegalArgumentException("Không thể đặt cha tạo thành vòng cha-con");
                }
            }
            t.setParentId(parentId);
            t.setOrderIndex(item.orderIndex());
            t.touch();
            taskRepo.save(t);
        }
        auditPort.record("PROJECT_TASK_REORDERED", "Project", projectId, actor,
                "items=" + req.items().size());
    }

    @Transactional
    public void delete(String projectId, String taskId, String actor) {
        getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        if (taskRepo.countByParentId(taskId) > 0) {
            throw new IllegalArgumentException("Không thể xóa: công việc còn công việc con. Xóa/di chuyển con trước.");
        }
        String parentId = t.getParentId();
        commentRepo.deleteByTaskId(taskId);
        attachmentRepo.deleteByTaskId(taskId);
        activityRepo.deleteByTaskId(taskId);
        taskRepo.delete(t);
        auditPort.record("PROJECT_TASK_DELETED", "ProjectTask", taskId, actor, "projectId=" + projectId);
        rollupFromParent(projectId, parentId, actor); // xoá con → cập nhật trạng thái cha
    }

    // ===== Theo dõi thời gian thực tế (log work) =====

    /** Cộng thêm {@code hours} giờ vào spentHours của task (phải > 0). Ghi activity SPENT + audit. */
    @Transactional
    public ProjectDto.TaskResponse logWork(String projectId, String taskId, Double hours, String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        if (hours == null || hours <= 0) {
            throw new IllegalArgumentException("Số giờ log work phải lớn hơn 0");
        }
        t.addSpentHours(hours);
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_WORK_LOGGED", "ProjectTask", saved.getId(), actor,
                "hours=" + hours + ", spentHours=" + saved.getSpentHours());
        recordActivity(saved, actor, TaskActivity.SPENT,
                "+" + trim(hours) + "h (tổng " + trim(saved.getSpentHours()) + "h)");
        return toDto(saved, p.getCode(), isLeaf(projectId, taskId));
    }

    // ===== Lịch sử / hoạt động task (mới → cũ) =====

    @Transactional(readOnly = true)
    public List<ProjectDto.TaskActivityResponse> listActivity(String projectId, String taskId) {
        requireSameProjectTask(projectId, taskId);
        List<ProjectDto.TaskActivityResponse> out = new ArrayList<>();
        for (TaskActivity a : activityRepo.findByTaskIdOrderByCreatedAtDesc(taskId)) {
            out.add(ProjectDto.TaskActivityResponse.of(a));
        }
        return out;
    }

    // ===== helpers (activity / notify) =====

    private void recordActivity(ProjectTask t, String actor, String action, String detail) {
        activityRepo.save(new TaskActivity(t.getId(), t.getProjectId(), actorName(actor), action, detail));
    }

    /** Thông báo người được giao việc (trừ chính người thao tác). */
    private void notifyAssign(ProjectTask t, Project p, String code, String actor) {
        String assignee = t.getAssigneeUserId();
        if (assignee == null || assignee.equals(userIdOf(actor))) {
            return;
        }
        safeNotify(assignee, "PROJECT_TASK_ASSIGNED", "Bạn được giao việc",
                "Bạn được giao việc " + code + " " + t.getTitle() + " trong dự án " + p.getName(),
                "/projects/" + p.getId());
    }

    /** Thông báo assignee khi đổi trạng thái (nếu khác người thao tác). */
    private void notifyStatus(ProjectTask t, Project p, String code, String actor) {
        String assignee = t.getAssigneeUserId();
        if (assignee == null || assignee.equals(userIdOf(actor))) {
            return;
        }
        safeNotify(assignee, "PROJECT_TASK_STATUS", "Cập nhật trạng thái công việc",
                code + " " + t.getTitle() + " → " + t.getStatus().name(),
                "/projects/" + p.getId());
    }

    private void safeNotify(String recipientUserId, String type, String title, String body, String link) {
        try {
            notificationService.notify(recipientUserId, type, title, body, link);
        } catch (Exception ignored) {
            // lỗi thông báo không chặn nghiệp vụ
        }
    }

    private String assignDetail(String assigneeUserId) {
        if (assigneeUserId == null) {
            return "Bỏ gán người thực hiện";
        }
        UserAccount acc = userRepo.findById(assigneeUserId).orElse(null);
        return "Giao cho " + (acc != null ? ProjectService.displayName(acc) : assigneeUserId);
    }

    private String code(Project p, ProjectTask t) {
        return p.getCode() + "-" + t.getSeq();
    }

    private String actorName(String username) {
        if (username == null) {
            return "anonymous";
        }
        return userRepo.findByUsername(username).map(ProjectService::displayName).orElse(username);
    }

    /** UserId người LOG: reporterUserId nếu có; nếu null → thử resolve từ createdBy(username)→userId; vẫn null → null. */
    private String resolveReporter(ProjectTask t) {
        if (t.getReporterUserId() != null) {
            return t.getReporterUserId();
        }
        return userIdOf(t.getCreatedBy());
    }

    private String userIdOf(String username) {
        if (username == null) {
            return null;
        }
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    // ===== helpers =====

    private void applyFields(ProjectTask t, ProjectDto.TaskRequest req, String parentId) {
        TaskType type = parseType(req.type());
        double est = req.estimateHours() == null ? 0.0 : req.estimateHours();
        // Ràng buộc: SUB-TASK ước lượng KHÔNG quá 4 giờ (đơn vị công việc nhỏ, chia nhỏ nếu lớn hơn).
        if (type == TaskType.SUBTASK && est > 4.0) {
            throw new IllegalArgumentException("Ước lượng sub-task không được quá 4 giờ");
        }
        t.apply(parentId,
                require(req.title(), "tiêu đề"),
                blankToNull(req.description()),
                type,
                parseStatusOrDefault(req.status()),
                parsePriority(req.priority()),
                blankToNull(req.assigneeUserId()),
                est,
                parseDate(req.startDate(), "ngày bắt đầu"),
                parseDate(req.dueDate(), "ngày kết thúc"),
                req.orderIndex() == null ? 0 : req.orderIndex(),
                blankToNull(req.screen()),
                parseSeverity(req.severity()),
                blankToNull(req.stepsToReproduce()),
                blankToNull(req.expectedResult()),
                blankToNull(req.actualResult()),
                blankToNull(req.environment()),
                blankToNull(req.testerUserId()));
    }

    /** Parse mức độ nghiêm trọng AN TOÀN: rỗng/null/không hợp lệ → null (không ném lỗi). */
    private static BugSeverity parseSeverity(String s) {
        if (blank(s)) {
            return null;
        }
        try {
            return BugSeverity.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Project getProject(String projectId) {
        return projectRepo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án"));
    }

    private ProjectTask requireSameProjectTask(String projectId, String taskId) {
        ProjectTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc"));
        if (!t.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Công việc không thuộc dự án này");
        }
        return t;
    }

    /** parentId mới có khiến taskId trở thành tổ tiên của chính nó không (đi ngược lên từ parent). */
    private boolean createsCycle(String taskId, String parentId) {
        String cursor = parentId;
        Set<String> guard = new HashSet<>();
        while (cursor != null) {
            if (cursor.equals(taskId)) {
                return true;
            }
            if (!guard.add(cursor)) {
                return true; // dữ liệu đã có vòng — chặn
            }
            ProjectTask p = taskRepo.findById(cursor).orElse(null);
            cursor = p == null ? null : p.getParentId();
        }
        return false;
    }

    private boolean isLeaf(String projectId, String taskId) {
        return taskRepo.countByParentId(taskId) == 0;
    }

    private static Set<String> collectParentIds(List<ProjectTask> tasks) {
        Set<String> ids = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                ids.add(t.getParentId());
            }
        }
        return ids;
    }

    private ProjectDto.TaskResponse toDto(ProjectTask t, String projectCode, boolean leaf) {
        return toDto(t, projectCode, leaf, singleProgress(t, leaf));
    }

    /** Dựng TaskResponse cho 1 task (response của mutation): resolve assignee theo userId (1 task → vài query). */
    private ProjectDto.TaskResponse toDto(ProjectTask t, String projectCode, boolean leaf, double progressPct) {
        String uid = t.getAssigneeUserId();
        String name = null, code = null, position = null, dept = null;
        if (uid != null) {
            UserAccount acc = userRepo.findById(uid).orElse(null);
            Employee emp = employeeRepo.findByUserAccountId(uid).orElse(null);
            name = ProjectService.personName(emp, acc, uid); // tên theo HỒ SƠ NHÂN SỰ (DSNS)
            if (emp != null) {
                code = emp.getEmpCode();
                position = emp.getJobPosition();
                dept = emp.getDeptCode();
            } else {
                code = acc != null ? acc.getUsername() : uid;
            }
        }
        // Chuỗi cha (Epic › Story › Task cha) — walk lên qua repo (vài cấp, đường mutation không nóng).
        java.util.LinkedList<ProjectDto.ParentRef> chain = new java.util.LinkedList<>();
        String pid = t.getParentId();
        int guard = 0;
        while (pid != null && guard++ < 12) {
            ProjectTask par = taskRepo.findById(pid).orElse(null);
            if (par == null) {
                break;
            }
            chain.addFirst(new ProjectDto.ParentRef(par.getType().name(),
                    projectCode + "-" + par.getSeq(), par.getTitle()));
            pid = par.getParentId();
        }
        return ProjectDto.TaskResponse.of(t, projectCode, name, code, position, dept, leaf, progressPct,
                chain, reporterName(t), testerName(t));
    }

    /** Tên người LOG (reporter) để hiển thị — resolve theo HỒ SƠ NHÂN SỰ; null cho task cũ chưa có reporter. */
    private String reporterName(ProjectTask t) {
        String rid = resolveReporter(t);
        if (rid == null) {
            return null;
        }
        UserAccount acc = userRepo.findById(rid).orElse(null);
        Employee emp = employeeRepo.findByUserAccountId(rid).orElse(null);
        return ProjectService.personName(emp, acc, rid);
    }

    /** Tên người KIỂM THỬ (tester) để hiển thị — resolve theo HỒ SƠ NHÂN SỰ; null nếu chưa gán. */
    private String testerName(ProjectTask t) {
        String tid = t.getTesterUserId();
        if (tid == null || tid.isBlank()) {
            return null;
        }
        UserAccount acc = userRepo.findById(tid).orElse(null);
        Employee emp = employeeRepo.findByUserAccountId(tid).orElse(null);
        return ProjectService.personName(emp, acc, tid);
    }

    /** progressPct cho một task đơn (dùng ở các response mutation): lá → theo status; cha → rollup cây con. */
    private double singleProgress(ProjectTask t, boolean leaf) {
        if (leaf) {
            return t.getStatus() == TaskStatus.DONE ? 100.0 : 0.0;
        }
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(t.getProjectId());
        Set<String> parentIds = collectParentIds(tasks);
        return computeProgress(tasks, parentIds).getOrDefault(t.getId(), 0.0);
    }

    private static TaskType parseType(String s) {
        if (blank(s)) {
            return TaskType.TASK;
        }
        try {
            return TaskType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại công việc không hợp lệ: " + s);
        }
    }

    private static TaskStatus parseStatus(String s) {
        if (blank(s)) {
            throw new IllegalArgumentException("Thiếu trạng thái");
        }
        try {
            return TaskStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái công việc không hợp lệ: " + s);
        }
    }

    private static TaskStatus parseStatusOrDefault(String s) {
        return blank(s) ? TaskStatus.BACKLOG : parseStatus(s);
    }

    private static TaskPriority parsePriority(String s) {
        if (blank(s)) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Độ ưu tiên không hợp lệ: " + s);
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

    private static String require(String v, String label) {
        if (blank(v)) {
            throw new IllegalArgumentException("Thiếu " + label);
        }
        return v.trim();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s.trim(); }
}
