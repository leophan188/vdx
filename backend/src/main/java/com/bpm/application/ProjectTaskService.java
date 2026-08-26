package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.BugSeverity;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectDiary;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskActivity;
import com.bpm.domain.project.TaskPriority;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.domain.project.TaskWorkLog;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectDiaryRepository;
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

    /**
     * TRẦN GIỜ cho TASK LÁ (đơn vị làm việc nhỏ nhất): ước lượng và mỗi lần ghi giờ đều không
     * được quá 4h — buộc chia nhỏ công việc để ước lượng và chấm công còn bám sát thực tế.
     * Task CHA không áp trần: mọi con số của cha là TỔNG HỢP từ lá con, không nhập tay.
     */
    private static final double MAX_LEAF_HOURS = 4.0;

    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final TaskCommentRepository commentRepo;
    private final TaskAttachmentRepository attachmentRepo;
    private final TaskActivityRepository activityRepo;
    private final ProjectDiaryRepository diaryRepo;
    private final com.bpm.infrastructure.TaskWorkLogRepository workLogRepo;
    private final AuditPort auditPort;
    private final NotificationService notificationService;

    public ProjectTaskService(ProjectRepository projectRepo, ProjectTaskRepository taskRepo,
                              UserAccountRepository userRepo, EmployeeRepository employeeRepo,
                              TaskCommentRepository commentRepo,
                              TaskAttachmentRepository attachmentRepo, TaskActivityRepository activityRepo,
                              ProjectDiaryRepository diaryRepo,
                              com.bpm.infrastructure.TaskWorkLogRepository workLogRepo,
                              AuditPort auditPort, NotificationService notificationService) {
        this.workLogRepo = workLogRepo;
        this.projectRepo = projectRepo;
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.commentRepo = commentRepo;
        this.attachmentRepo = attachmentRepo;
        this.activityRepo = activityRepo;
        this.diaryRepo = diaryRepo;
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
                    String.valueOf(par.getSeq()), par.getTitle()));
            pid = par.getParentId();
        }
        return chain;
    }

    /** Dựng TaskResponse với assignee đã resolve sẵn (dùng cho list — tránh query từng task). */
    private ProjectDto.TaskResponse toDto(ProjectTask t, String projectCode, boolean leaf, double progressPct,
                                          Map<String, UserAccount> userById, Map<String, Employee> empByUser,
                                          List<ProjectDto.ParentRef> parentChain) {
        String uid = t.getAssigneeUserId();
        String name = null, code = null, position = null, title = null, dept = null;
        if (uid != null) {
            UserAccount acc = userById.get(uid);
            Employee emp = empByUser.get(uid);
            name = ProjectService.personName(emp, acc, uid); // tên theo HỒ SƠ NHÂN SỰ (DSNS)
            if (emp != null) {
                code = emp.getEmpCode();
                position = emp.getJobPosition();
                title = emp.getTitle();
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
        return ProjectDto.TaskResponse.of(t, projectCode, name, code, position, title, dept, leaf, progressPct,
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
            if (t.getStatus() == TaskStatus.CANCELLED) {
                pct.put(t.getId(), 0.0);
                return new double[]{0, 0, 0, 0}; // Huỷ = ngoài phạm vi, không góp vào rollup cha
            }
            // LÁ — quy tắc chung ở TaskProgress: trọng số = giờ hiệu lực, Kiểm thử được 0.8 điểm.
            // Epic/Story RỖNG là lá về mặt cây nhưng KHÔNG phải việc thực thi → không góp vào rollup.
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
                pct.put(t.getId(), 0.0);
                return new double[]{0, 0, 0, 0};
            }
            double w = TaskProgress.weight(t);
            double f = TaskProgress.factor(t);
            pct.put(t.getId(), Math.round(f * 10000.0) / 100.0);
            return new double[]{w, w * f, 1, f};
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
        applyFields(t, req, parentId, true); // task vừa tạo luôn là LÁ (chưa có con)
        requireValidParent(t.getType(), parentId, projectId); // ràng buộc: Story/Task/Sub-task/Bug/Issue phải có cha đúng loại
        requireReadyForProgress(t, null, t.getStatus(), projectId, null); // tạo thẳng ở Đang làm cũng phải đủ est + ngày
        t.setReporterUserId(userIdOf(actor)); // người LOG = actor (UserAccount id); dùng cho auto-reassign bug
        // Người kiểm thử MẶC ĐỊNH = người LOG khi tạo Bug/Issue chưa chọn — đồng bộ mọi màn (Tạo nhanh/Backlog/Bug/my-bugs).
        if ((t.getType() == TaskType.BUG || t.getType() == TaskType.ISSUE)
                && (t.getTesterUserId() == null || t.getTesterUserId().isBlank())) {
            t.setTesterUserId(t.getReporterUserId());
        }
        projectRepo.save(p); // lưu seq mới
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_CREATED", "ProjectTask", saved.getId(), actor,
                "projectId=" + projectId + ", code=" + saved.getSeq());
        recordActivity(saved, actor, TaskActivity.CREATED,
                "Tạo " + typeLabel(saved.getType()) + " " + code(p, saved));
        // Nếu tạo task có sẵn assignee → báo người được giao (trừ chính người thao tác).
        if (saved.getAssigneeUserId() != null) {
            notifyAssign(saved, p, code(p, saved), actor);
        }
        rollupFromParent(projectId, saved.getParentId(), actor); // thêm con → cập nhật trạng thái cha
        // BUG/ISSUE: bắt buộc ghi giờ tester đã bỏ ra để TÌM ra lỗi này.
        if (saved.getType() == TaskType.BUG || saved.getType() == TaskType.ISSUE) {
            if (req.testHours() == null || req.testHours() <= 0) {
                throw new IllegalArgumentException("Cần nhập số giờ đã bỏ ra để tìm & ghi nhận lỗi này");
            }
            requireHoursWithinCap(req.testHours());
            saveWorkLog(saved, TaskWorkLog.ROLE_TEST, req.testHours(), req.workDate(),
                    null, actor, TaskWorkLog.ACT_LOG_BUG);
        }
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
        applyFields(t, req, parentId, isLeaf(projectId, taskId));
        requireReadyForProgress(t, oldStatus, t.getStatus(), projectId, taskId); // PUT cũng không lách được rule Đang làm
        autoTesterForBug(t, t.getStatus());
        requireRolesForDone(t, t.getStatus(), projectId, taskId);
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_UPDATED", "ProjectTask", saved.getId(), actor,
                "projectId=" + projectId + ", code=" + saved.getSeq());
        recordActivity(saved, actor, TaskActivity.EDIT,
                "Sửa " + typeLabel(saved.getType()) + " " + code(p, saved));
        // Trạng thái đổi qua PUT cũng ghi nhận + thông báo assignee.
        if (saved.getStatus() != oldStatus) {
            recordStatusActivity(saved, actor,
                    statusLabel(oldStatus) + " → " + statusLabel(saved.getStatus()), oldStatus, saved.getStatus());
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
        return updateStatus(projectId, taskId, status, null, null, null, actor);
    }

    /**
     * Đổi trạng thái, kèm GHI GIỜ ở hai mốc bàn giao (nguồn timesheet):
     * sang Kiểm thử ghi giờ vai DEV, sang Hoàn thành ghi giờ vai TEST.
     */
    @Transactional
    public ProjectDto.TaskResponse updateStatus(String projectId, String taskId, String status,
                                                Double hours, String workDate, String note, String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        TaskStatus oldStatus = t.getStatus();
        TaskStatus newStatus = parseStatus(status);
        requireReadyForProgress(t, oldStatus, newStatus, projectId, taskId);
        autoTesterForBug(t, newStatus);           // bug/issue → tester = người log
        requireRolesForDone(t, newStatus, projectId, taskId); // Hoàn thành phải đủ dev + tester
        String workRole = workRoleFor(t, oldStatus, newStatus, projectId, taskId);
        if (workRole != null) {
            if (hours == null || hours <= 0) {
                String msg;
                if (TaskWorkLog.ROLE_DEV.equals(workRole)) {
                    msg = "Cần nhập số giờ đã làm trước khi bàn giao sang Kiểm thử";
                } else if (newStatus == TaskStatus.IN_PROGRESS) {
                    msg = "Cần nhập số giờ đã kiểm thử trước khi trả về Đang làm";
                } else {
                    msg = "Cần nhập số giờ đã kiểm thử trước khi chuyển sang Hoàn thành";
                }
                throw new IllegalArgumentException(msg);
            }
            requireHoursWithinCap(hours);
        }
        t.setStatus(newStatus);
        // HOÀN THÀNH mà chưa có hạn → lấy NGÀY THỰC TẾ hoàn thành làm hạn, để task không
        // nằm mãi trong nhóm "thiếu hạn" và các báo cáo theo ngày có mốc mà bám.
        // Ngày lấy từ ô "Ngày tính công" người dùng vừa nhập (mặc định hôm nay).
        LocalDate autoDue = null;
        if (newStatus == TaskStatus.DONE && t.getDueDate() == null && oldStatus != TaskStatus.DONE) {
            autoDue = parseWorkDate(workDate);
            t.setDueDate(autoDue);
        }
        t.touch();
        // KHÔNG đổi assignee: người thực hiện (lập trình) + người kiểm thử (tester) + người log (reporter)
        // là 3 field RIÊNG BIỆT, GIỮ NGUYÊN qua các trạng thái. FE hiển thị "chủ hiện tại" theo status
        // (Đang làm→lập trình, Kiểm thử→kiểm thử/tester, bug Kiểm thử→người log).
        ProjectTask saved = taskRepo.save(t);
        auditPort.record("PROJECT_TASK_STATUS_CHANGED", "ProjectTask", saved.getId(), actor,
                "status=" + saved.getStatus());
        if (saved.getStatus() != oldStatus) {
            recordStatusActivity(saved, actor,
                    statusLabel(oldStatus) + " → " + statusLabel(saved.getStatus()), oldStatus, saved.getStatus());
            if (workRole != null) {
                // Cùng vai TEST nhưng ý nghĩa khác hẳn: duyệt xong vs trả về sửa.
                String act;
                if (newStatus == TaskStatus.IN_REVIEW) {
                    act = TaskWorkLog.ACT_HANDOVER;
                } else if (newStatus == TaskStatus.DONE) {
                    act = TaskWorkLog.ACT_VERIFY_DONE;
                } else {
                    act = TaskWorkLog.ACT_REOPEN;
                }
                saveWorkLog(saved, workRole, hours, workDate, note, actor, act);
            }
            if (autoDue != null) {
                // Ghi vết rõ ràng: tự điền ngày là SỬA DỮ LIỆU, người dùng phải truy được.
                recordActivity(saved, actor, TaskActivity.EDIT,
                        "Tự điền Ngày hoàn thành = " + autoDue.format(DMY) + " (hoàn thành khi chưa có hạn)");
            }
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
                recordStatusActivity(parent, actor,
                        statusLabel(old) + " → " + statusLabel(derived) + " (tự tổng hợp từ task con)", old, derived);
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
        // Con đã HUỶ nằm ngoài phạm vi → không tính vào tổng hợp trạng thái cha.
        List<ProjectTask> active = new ArrayList<>();
        for (ProjectTask k : kids) {
            if (k.getStatus() != TaskStatus.CANCELLED) {
                active.add(k);
            }
        }
        if (active.isEmpty()) {
            return TaskStatus.CANCELLED; // tất cả con đã huỷ → cha cũng huỷ
        }
        boolean allDone = true, allBacklog = true, anyStartedOrDone = false, anyTodo = false;
        for (ProjectTask k : active) {
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
        workLogRepo.deleteByTaskId(taskId);
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

    // ===== GIỜ LÀM VIỆC THỰC TẾ (timesheet) =====

    /** Ghi giờ thủ công cho một task (nút "Ghi giờ" ở chi tiết công việc). */
    @Transactional
    public ProjectDto.WorkLogResponse addWorkLog(String projectId, String taskId, ProjectDto.WorkLogRequest req,
                                                 String actor) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        if (req.hours() == null || req.hours() <= 0) {
            throw new IllegalArgumentException("Số giờ phải lớn hơn 0");
        }
        requireHoursWithinCap(req.hours());
        // Task CHA tổng hợp giờ từ lá con — ghi thẳng vào cha sẽ cộng trùng với con.
        if (!isLeaf(projectId, taskId)) {
            throw new IllegalArgumentException(
                    "Không ghi giờ trực tiếp lên công việc cha — giờ của cha được tổng hợp từ các công việc con");
        }
        String role = TaskWorkLog.ROLE_TEST.equalsIgnoreCase(req.role())
                ? TaskWorkLog.ROLE_TEST : TaskWorkLog.ROLE_DEV;
        // Người bỏ công = người thao tác (tự ghi giờ của mình), không suy theo vai của task:
        // nút này dùng để ghi giờ HẰNG NGÀY nên ai bấm là giờ của người đó.
        String uid = userIdOf(actor);
        if (uid == null) {
            throw new IllegalArgumentException("Không xác định được người ghi giờ");
        }
        LocalDate d = parseWorkDate(req.workDate());
        String name = userRepo.findById(uid).map(ProjectService::displayName).orElse(null);
        TaskWorkLog saved = workLogRepo.save(new TaskWorkLog(projectId, taskId, uid, name, role, d,
                req.hours(), blankToNull(req.note()), actor, TaskWorkLog.ACT_MANUAL));
        t.addSpentHours(req.hours());
        taskRepo.save(t);
        recordActivity(t, actor, TaskActivity.SPENT,
                "+" + trim(req.hours()) + "h (" + (TaskWorkLog.ROLE_DEV.equals(role) ? "lập trình" : "kiểm thử")
                        + ", ngày " + d.format(DMY) + ")");
        auditPort.record("PROJECT_TASK_WORK_LOGGED", "ProjectTask", taskId, actor,
                "hours=" + req.hours() + ", role=" + role + ", date=" + d);
        return ProjectDto.WorkLogResponse.of(saved, code(p, t), t.getTitle());
    }

    /** Chuỗi cha "Epic › Story › Task cha" của một task; rỗng nếu là gốc. */
    private static String parentPathOf(ProjectTask t, Map<String, ProjectTask> byId) {
        java.util.LinkedList<String> chain = new java.util.LinkedList<>();
        String pid = t.getParentId();
        int guard = 0;
        while (pid != null && guard++ < 12) {
            ProjectTask par = byId.get(pid);
            if (par == null) {
                break;
            }
            chain.addFirst(par.getTitle());
            pid = par.getParentId();
        }
        return String.join(" › ", chain);
    }

    /** Giờ đã ghi trên một task (mới → cũ). */
    @Transactional(readOnly = true)
    public List<ProjectDto.WorkLogResponse> listTaskWorkLogs(String projectId, String taskId) {
        Project p = getProject(projectId);
        ProjectTask t = requireSameProjectTask(projectId, taskId);
        List<ProjectDto.WorkLogResponse> out = new ArrayList<>();
        for (TaskWorkLog w : workLogRepo.findByTaskIdOrderByWorkDateDescCreatedAtDesc(taskId)) {
            out.add(ProjectDto.WorkLogResponse.of(w, code(p, t), t.getTitle()));
        }
        return out;
    }

    /** Giờ của cả dự án trong khoảng ngày — nguồn dựng timesheet. */
    @Transactional(readOnly = true)
    public List<ProjectDto.WorkLogResponse> listProjectWorkLogs(String projectId, String from, String to) {
        Project p = getProject(projectId);
        LocalDate f = parseWorkDate(from);
        LocalDate t2 = parseWorkDate(to);
        Map<String, ProjectTask> byId = new HashMap<>();
        for (ProjectTask t : taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId)) {
            byId.put(t.getId(), t);
        }
        List<ProjectDto.WorkLogResponse> out = new ArrayList<>();
        for (TaskWorkLog w : workLogRepo.findByProjectIdAndWorkDateBetween(projectId, f, t2)) {
            ProjectTask t = byId.get(w.getTaskId());
            if (t == null) {
                out.add(ProjectDto.WorkLogResponse.of(w, "", ""));
                continue;
            }
            out.add(ProjectDto.WorkLogResponse.of(w, code(p, t), t.getTitle(),
                    t.getType().name(), parentPathOf(t, byId), t.getEstimateHours()));
        }
        return out;
    }

    /**
     * Đổi NGƯỜI của một dòng giờ đã ghi.
     *
     * Mỗi dòng giờ vốn gắn với một BƯỚC (vai Lập trình / Kiểm thử) và người đảm nhiệm bước đó tại thời
     * điểm ghi — nên đổi người thực hiện hay người kiểm thử của task KHÔNG kéo theo giờ cũ: công sức đã
     * bỏ ra vẫn thuộc người đã làm. Cách đó đúng khi bàn giao thật, nhưng khi gán nhầm người rồi mới sửa
     * thì dòng giờ nằm sai chỗ; hàm này để nắn lại đúng dòng đó, không đụng các dòng khác.
     */
    @Transactional
    public ProjectDto.WorkLogResponse changeWorkLogUser(String projectId, String workLogId,
                                                        String userId, String actor) {
        TaskWorkLog w = workLogRepo.findById(workLogId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dòng giờ"));
        if (!w.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Dòng giờ không thuộc dự án này");
        }
        String uid = blankToNull(userId);
        if (uid == null || userRepo.findById(uid).isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy người được chọn");
        }
        String old = w.getUserId();
        w.reassignTo(uid, userRepo.findById(uid).map(u -> u.getFullName()).orElse(null));
        TaskWorkLog saved = workLogRepo.save(w);
        auditPort.record("PROJECT_TASK_WORK_LOG_REASSIGNED", "ProjectTask", w.getTaskId(), actor,
                "workLogId=" + workLogId + ", từ=" + old + ", sang=" + uid + ", giờ=" + w.getHours());
        Project p = getProject(projectId);
        ProjectTask t = taskRepo.findById(saved.getTaskId()).orElse(null);
        return t == null
                ? ProjectDto.WorkLogResponse.of(saved, null, null)
                : ProjectDto.WorkLogResponse.of(saved, code(p, t), t.getTitle());
    }

    /** Xoá một dòng giờ ghi nhầm (trừ lại vào tổng giờ của task). */
    @Transactional
    public void deleteWorkLog(String projectId, String workLogId, String actor) {
        TaskWorkLog w = workLogRepo.findById(workLogId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dòng giờ"));
        if (!w.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Dòng giờ không thuộc dự án này");
        }
        taskRepo.findById(w.getTaskId()).ifPresent(t -> {
            t.addSpentHours(-w.getHours());
            taskRepo.save(t);
        });
        workLogRepo.delete(w);
        auditPort.record("PROJECT_TASK_WORK_LOG_DELETED", "ProjectTask", w.getTaskId(), actor,
                "hours=" + w.getHours());
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

    /**
     * Nhật ký hoạt động toàn dự án (mới → cũ, tối đa 300 dòng gần nhất).
     * Resolve taskCode (= seq) và taskTitle từ ProjectTask;
     * task đã xoá → taskCode/taskTitle null nhưng vẫn giữ dòng.
     */
    @Transactional(readOnly = true)
    public List<ProjectDto.ProjectActivityItem> listProjectActivity(String projectId) {
        Project p = getProject(projectId);
        List<TaskActivity> activities = activityRepo.findTop300ByProjectIdOrderByCreatedAtDesc(projectId);
        // Resolve task (code + title) bằng 1 query gộp theo taskId (tránh N+1).
        Set<String> taskIds = new HashSet<>();
        for (TaskActivity a : activities) {
            if (a.getTaskId() != null) {
                taskIds.add(a.getTaskId());
            }
        }
        Map<String, ProjectTask> taskById = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (ProjectTask t : taskRepo.findAllById(taskIds)) {
                taskById.put(t.getId(), t);
            }
        }
        List<ProjectDto.ProjectActivityItem> out = new ArrayList<>();
        for (TaskActivity a : activities) {
            ProjectTask t = taskById.get(a.getTaskId());
            String taskCode = t == null ? null : String.valueOf(t.getSeq());
            String taskTitle = t == null ? null : t.getTitle();
            out.add(new ProjectDto.ProjectActivityItem(a.getId(), a.getTaskId(), taskCode, taskTitle,
                    a.getActorName(), a.getAction(), a.getDetail(),
                    a.getCreatedAt() == null ? null : a.getCreatedAt().toString()));
        }
        // Gộp Nhật ký dự án vào cùng dòng thời gian (action = DIARY) để tab Log thấy được.
        for (ProjectDiary d : diaryRepo.findByProjectIdOrderByWorkDateDescCreatedAtDesc(projectId)) {
            out.add(new ProjectDto.ProjectActivityItem(d.getId(), null, null, diaryTitle(d),
                    d.getCreatedByName(), "DIARY", diaryDetail(d),
                    d.getCreatedAt() == null ? null : d.getCreatedAt().toString()));
        }
        // Sắp xếp lại theo thời gian giảm dần (createdAt ISO so sánh chuỗi được; null xuống cuối).
        out.sort((x, y) -> {
            String cx = x.createdAt(), cy = y.createdAt();
            if (cx == null && cy == null) return 0;
            if (cx == null) return 1;
            if (cy == null) return -1;
            return cy.compareTo(cx);
        });
        return out;
    }

    /** Tiêu đề hiển thị cho dòng nhật ký ở tab Log: "Nhật ký · {phân loại} ({ngày})". */
    private static String diaryTitle(ProjectDiary d) {
        StringBuilder sb = new StringBuilder("Nhật ký");
        if (d.getCategory() != null && !d.getCategory().isBlank()) {
            sb.append(" · ").append(d.getCategory());
        }
        if (d.getWorkDate() != null) {
            sb.append(" (").append(d.getWorkDate()).append(")");
        }
        return sb.toString();
    }

    /** Chi tiết dòng nhật ký: trích nội dung (rút gọn) để đọc nhanh trên timeline. */
    private static String diaryDetail(ProjectDiary d) {
        String c = d.getContent();
        if (c == null || c.isBlank()) {
            return "Ghi nhật ký buổi làm việc";
        }
        c = c.strip().replaceAll("\\s+", " ");
        return c.length() > 160 ? c.substring(0, 160) + "…" : c;
    }

    // ===== helpers (activity / notify) =====

    private void recordActivity(ProjectTask t, String actor, String action, String detail) {
        activityRepo.save(new TaskActivity(t.getId(), t.getProjectId(), actorName(actor), action, detail));
    }

    /**
     * Ghi nhật ký ĐỔI TRẠNG THÁI kèm mốc có cấu trúc (from/to + userId người thao tác).
     * Báo cáo đếm "dev bàn giao Kiểm thử" / "tester chuyển Hoàn thành" dựa vào đây.
     */
    private void recordStatusActivity(ProjectTask t, String actor, String detail,
                                      TaskStatus from, TaskStatus to) {
        activityRepo.save(new TaskActivity(t.getId(), t.getProjectId(), actorName(actor), userIdOf(actor),
                detail, from == null ? null : from.name(), to == null ? null : to.name()));
    }

    /**
     * Lần chuyển trạng thái này có phải MỐC BÀN GIAO cần ghi giờ không, và ghi với vai nào.
     * · → Kiểm thử  = dev xong phần code   → ghi giờ vai DEV
     * · → Hoàn thành = tester xong phần test → ghi giờ vai TEST
     * Trả null nếu không cần ghi (đổi trạng thái khác, task cha do rollup, Epic/Story).
     */
    private String workRoleFor(ProjectTask t, TaskStatus oldStatus, TaskStatus newStatus,
                               String projectId, String taskId) {
        if (newStatus == oldStatus) {
            return null;
        }
        if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
            return null;
        }
        if (taskId != null && !isLeaf(projectId, taskId)) {
            return null; // task cha: trạng thái do rollup, không ai bỏ công trực tiếp
        }
        if (newStatus == TaskStatus.IN_REVIEW) {
            return TaskWorkLog.ROLE_DEV;
        }
        // REOPEN (Kiểm thử -> Đang làm): tester đã bỏ công kiểm thử rồi mới kết luận CHƯA ĐẠT.
        // Không ghi thì toàn bộ công của những lượt kiểm thử fail bị mất trắng khỏi timesheet.
        if (oldStatus == TaskStatus.IN_REVIEW && newStatus == TaskStatus.IN_PROGRESS) {
            return TaskWorkLog.ROLE_TEST;
        }
        if (newStatus == TaskStatus.DONE) {
            // Việc không qua kiểm thử: người thực hiện tự hoàn thành → giờ tính vai LẬP TRÌNH,
            // nếu ghi vai TEST thì timesheet của họ sẽ hiện thành giờ kiểm thử, sai hoàn toàn.
            return t.requiresTest() ? TaskWorkLog.ROLE_TEST : TaskWorkLog.ROLE_DEV;
        }
        return null;
    }

    /**
     * Ghi một dòng giờ làm việc. Người bỏ công lấy theo VAI (dev = người thực hiện,
     * test = người kiểm thử) chứ không phải người bấm nút — PM bấm hộ thì công vẫn về đúng người.
     * {@code workDate} rỗng/sai định dạng → tính vào hôm nay.
     */
    private void saveWorkLog(ProjectTask t, String role, Double hours, String workDate,
                             String note, String actor, String action) {
        String uid = TaskWorkLog.ROLE_DEV.equals(role) ? t.getAssigneeUserId() : t.getTesterUserId();
        if (uid == null) {
            uid = userIdOf(actor); // không xác định được vai → quy về người thao tác, tránh mất giờ
        }
        if (uid == null || hours == null || hours <= 0) {
            return;
        }
        LocalDate d = parseWorkDate(workDate);
        String name = userRepo.findById(uid).map(ProjectService::displayName).orElse(null);
        workLogRepo.save(new TaskWorkLog(t.getProjectId(), t.getId(), uid, name, role, d, hours,
                blankToNull(note), actor, action));
        t.addSpentHours(hours); // giữ tổng giờ trên task cho các màn đang dùng spentHours
        taskRepo.save(t);
        recordActivity(t, actor, TaskActivity.SPENT,
                "+" + trim(hours) + "h (" + (TaskWorkLog.ROLE_DEV.equals(role) ? "lập trình" : "kiểm thử")
                        + ", ngày " + d.format(DMY) + ")");
    }

    /**
     * MỖI LẦN ghi giờ trên task lá không quá {@link #MAX_LEAF_HOURS}. Chặn từng lần chứ không
     * chặn tổng: task ước lượng 4h mà thực tế làm 6h (bị trả về sửa lại) thì vẫn phải ghi được
     * đúng thực tế, nếu chặn tổng thì timesheet buộc phải ghi sai.
     */
    private static void requireHoursWithinCap(double hours) {
        if (hours > MAX_LEAF_HOURS) {
            throw new IllegalArgumentException("Mỗi lần ghi giờ không được quá " + trim(MAX_LEAF_HOURS)
                    + " giờ — hãy tách nhỏ công việc hoặc ghi thành nhiều lần theo từng ngày");
        }
    }

    /** yyyy-MM-dd → LocalDate; rỗng/sai định dạng → hôm nay. */
    private static LocalDate parseWorkDate(String s) {
        if (s == null || s.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    /**
     * Vai bắt buộc theo quy trình: task chỉ được HOÀN THÀNH khi có đủ NGƯỜI THỰC HIỆN (dev)
     * và NGƯỜI KIỂM THỬ (tester). Chặn ở service nên mọi đường (PATCH status, PUT sửa,
     * POST tạo mới) đều không lách được. Epic/Story là cấp nhóm, trạng thái do rollup tự tính → miễn.
     */
    private void requireRolesForDone(ProjectTask t, TaskStatus newStatus, String projectId, String taskId) {
        if (newStatus != TaskStatus.DONE) {
            return;
        }
        if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
            return;
        }
        if (taskId != null && !isLeaf(projectId, taskId)) {
            return; // task cha: rollup tự đặt trạng thái
        }
        if (t.getAssigneeUserId() == null) {
            throw new IllegalArgumentException("Cần có Người thực hiện trước khi chuyển sang Hoàn thành");
        }
        if (t.requiresTest() && t.getTesterUserId() == null) {
            throw new IllegalArgumentException("Cần có Người kiểm thử trước khi chuyển sang Hoàn thành"
                    + " — hoặc đánh dấu \"Không cần kiểm thử\" nếu đây là việc không qua kiểm thử");
        }
    }

    /**
     * BUG/ISSUE chuyển sang Kiểm thử mà chưa có người kiểm thử → lấy luôn NGƯỜI LOG.
     * Đúng thói quen đang chạy (hệ thống vốn bàn giao ngầm cho người log để verify) và
     * giúp mọi task hoàn thành đều có đủ 2 vai mà không bắt tester tự chọn chính mình.
     */
    private void autoTesterForBug(ProjectTask t, TaskStatus newStatus) {
        if (newStatus != TaskStatus.IN_REVIEW || t.getTesterUserId() != null) {
            return;
        }
        if (t.getType() == TaskType.BUG || t.getType() == TaskType.ISSUE) {
            t.setTesterUserId(t.getReporterUserId());
        }
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
        return String.valueOf(t.getSeq());
    }

    /** Ràng buộc phân cấp: loại KHÁC Epic BẮT BUỘC chọn cha ĐÚNG loại. */
    private void requireValidParent(TaskType type, String parentId, String projectId) {
        List<TaskType> allowed = allowedParentTypes(type);
        if (allowed == null) {
            return; // EPIC — là gốc, không cần cha
        }
        if (parentId == null) {
            throw new IllegalArgumentException("Vui lòng chọn " + parentTypesText(allowed) + " cha cho " + typeLabel(type));
        }
        ProjectTask parent = requireSameProjectTask(projectId, parentId);
        if (!allowed.contains(parent.getType())) {
            throw new IllegalArgumentException(typeLabel(type) + " chỉ được thuộc " + parentTypesText(allowed));
        }
    }
    private static List<TaskType> allowedParentTypes(TaskType type) {
        switch (type) {
            case STORY: return List.of(TaskType.EPIC);
            case TASK: return List.of(TaskType.STORY, TaskType.EPIC);
            // Sub-task lồng trong Sub-task: cho phép nhiều cấp (việc lớn tách dần cho tới khi đủ nhỏ để log giờ).
            case SUBTASK: return List.of(TaskType.TASK, TaskType.SUBTASK);
            case BUG:
            case ISSUE: return List.of(TaskType.TASK, TaskType.SUBTASK);
            default: return null; // EPIC
        }
    }
    private static String parentTypesText(List<TaskType> types) {
        return types.stream().map(ProjectTaskService::typeLabel).collect(java.util.stream.Collectors.joining(" hoặc "));
    }

    /** Nhãn TRẠNG THÁI tiếng Việt để ghi Log (thay vì mã TODO/IN_REVIEW…). */
    private static String statusLabel(TaskStatus s) {
        if (s == null) {
            return "";
        }
        switch (s) {
            case BACKLOG: return "Backlog";
            case TODO: return "To Do";
            case IN_PROGRESS: return "In Progress";
            case IN_REVIEW: return "Testing";
            case DONE: return "Done";
            case CANCELLED: return "Cancelled";
            default: return s.name();
        }
    }

    /** Nhãn LOẠI công việc để ghi Log rõ ràng (Epic/Story/Task/Sub-task/Bug/Issue). */
    private static String typeLabel(TaskType type) {
        if (type == null) {
            return "công việc";
        }
        switch (type) {
            case EPIC: return "Epic";
            case STORY: return "Story";
            case TASK: return "Task";
            case SUBTASK: return "Sub-task";
            case BUG: return "Bug";
            case ISSUE: return "Issue";
            default: return "công việc";
        }
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

    /**
     * Chuyển sang ĐANG LÀM (task lá) BẮT BUỘC: Ước lượng (est) + Ngày bắt đầu + Ngày hoàn thành,
     * và Ngày bắt đầu ≤ Ngày hoàn thành. Áp cho MỌI đường: PATCH status, PUT sửa task, POST tạo mới.
     * MIỄN trong 2 trường hợp:
     * - Reopen (Kiểm thử/Hoàn thành/Huỷ → Đang làm): việc của dev, task đã ước lượng từ trước.
     * - Task CHA (không phải lá): trạng thái do rollup tự tính, không nhập tay.
     *
     * @param taskId null khi TẠO MỚI (task mới luôn là lá)
     * @param oldStatus null khi TẠO MỚI
     */
    private void requireReadyForProgress(ProjectTask t, TaskStatus oldStatus, TaskStatus newStatus,
                                         String projectId, String taskId) {
        if (newStatus != TaskStatus.IN_PROGRESS || oldStatus == TaskStatus.IN_PROGRESS) {
            return;
        }
        if (oldStatus == TaskStatus.IN_REVIEW || oldStatus == TaskStatus.DONE || oldStatus == TaskStatus.CANCELLED) {
            return; // Reopen — không bắt nhập lại
        }
        if (taskId != null && !isLeaf(projectId, taskId)) {
            return; // task cha: rollup tự đặt trạng thái
        }
        if (t.getEstimateHours() <= 0) {
            throw new IllegalArgumentException("Cần nhập Ước lượng (est) trước khi chuyển sang Đang làm");
        }
        if (t.getStartDate() == null) {
            throw new IllegalArgumentException("Cần nhập Ngày bắt đầu trước khi chuyển sang Đang làm");
        }
        if (t.getDueDate() == null) {
            throw new IllegalArgumentException("Cần nhập Ngày hoàn thành trước khi chuyển sang Đang làm");
        }
        if (t.getStartDate().isAfter(t.getDueDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng Ngày hoàn thành");
        }
    }

    private void applyFields(ProjectTask t, ProjectDto.TaskRequest req, String parentId, boolean leaf) {
        TaskType type = parseType(req.type());
        // Cờ "không cần kiểm thử" (việc PM/BA). KHÔNG cho bật khi task đã bàn giao kiểm thử
        // trở đi — bật lúc đó là bỏ qua quy trình giữa chừng và xoá công tester đã ghi nhận.
        boolean skipTest = Boolean.TRUE.equals(req.skipTest());
        if (skipTest && !t.isSkipTest()
                && (t.getStatus() == TaskStatus.IN_REVIEW || t.getStatus() == TaskStatus.DONE)) {
            throw new IllegalArgumentException(
                    "Task đã chuyển sang Kiểm thử thì không đánh dấu \"không cần kiểm thử\" được nữa");
        }
        double est = req.estimateHours() == null ? 0.0 : req.estimateHours();
        // Ràng buộc: TASK LÁ (đơn vị làm việc nhỏ nhất) ước lượng KHÔNG quá 4 giờ — lớn hơn thì tách nhỏ.
        // Task CHA không chặn: est của cha là TỔNG HỢP từ lá con, không nhập tay.
        // Epic/Story là cấp NHÓM nên cũng bỏ qua, kể cả khi chưa có con.
        //
        // MIỄN TRỪ DỮ LIỆU CŨ: chỉ chặn khi est THỰC SỰ THAY ĐỔI. Trần 4h thêm sau khi hệ thống
        // đã chạy nên còn 71 task lá mang est cũ > 4h; nếu chặn cả khi giữ nguyên thì mọi thao tác
        // sửa chúng (kể cả chỉ đổi tiêu đề hay gán người) đều bị từ chối — khoá cứng dữ liệu cũ.
        // Người dùng vẫn không thể ĐẶT một giá trị mới quá 4h.
        boolean estChanged = Math.abs(est - t.getEstimateHours()) > 0.0001;
        if (leaf && estChanged && type != TaskType.EPIC && type != TaskType.STORY && est > MAX_LEAF_HOURS) {
            throw new IllegalArgumentException("Ước lượng " + typeLabel(type) + " không được quá "
                    + trim(MAX_LEAF_HOURS) + " giờ — hãy tách nhỏ công việc");
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
                blankToNull(req.testerUserId()),
                skipTest);
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
        String name = null, code = null, position = null, title = null, dept = null;
        if (uid != null) {
            UserAccount acc = userRepo.findById(uid).orElse(null);
            Employee emp = employeeRepo.findByUserAccountId(uid).orElse(null);
            name = ProjectService.personName(emp, acc, uid); // tên theo HỒ SƠ NHÂN SỰ (DSNS)
            if (emp != null) {
                code = emp.getEmpCode();
                position = emp.getJobPosition();
                title = emp.getTitle();
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
                    String.valueOf(par.getSeq()), par.getTitle()));
            pid = par.getParentId();
        }
        return ProjectDto.TaskResponse.of(t, projectCode, name, code, position, title, dept, leaf, progressPct,
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
