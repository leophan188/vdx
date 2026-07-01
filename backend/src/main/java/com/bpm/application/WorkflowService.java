package com.bpm.application;

import com.bpm.api.dto.TaskDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.assignment.TaskAssignment;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.position.Position;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.process.ProcessStatus;
import com.bpm.domain.process.ProcessVersion;
import com.bpm.domain.role.PositionRole;
import com.bpm.domain.workflow.WorkflowInstance;
import com.bpm.infrastructure.BpmnConditionInjector;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.ProcessVersionRepository;
import com.bpm.infrastructure.TaskAssignmentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import com.bpm.infrastructure.WorkflowInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Khởi tạo & vận hành nhiệm vụ qua Flowable engine (Story 3.1, AD-1). Deploy định nghĩa BPMN của
 * phiên bản ĐÃ BAN HÀNH rồi start instance; snapshot phiên bản process (stepsMeta) lên app instance.
 * Phân công người thực hiện do listener TASK_CREATED xử lý (lõi phân công AD-14).
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ProcessService processService;
    private final AssignmentService assignmentService;
    private final ProcessVersionRepository versionRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final UserAccountRepository userRepo;
    private final PositionRepository positionRepo;
    private final TaskAssignmentRepository assignmentRepo;
    private final RoleService roleService;
    private final NotificationService notificationService;
    private final MailPort mailPort;
    private final BpmnConditionInjector conditionInjector;
    private final AuditPort auditPort;
    private final ObjectMapper objectMapper;

    public WorkflowService(RepositoryService repositoryService, RuntimeService runtimeService,
                           TaskService taskService, HistoryService historyService, ProcessService processService,
                           AssignmentService assignmentService, ProcessVersionRepository versionRepo,
                           WorkflowInstanceRepository instanceRepo, UserAccountRepository userRepo,
                           PositionRepository positionRepo, TaskAssignmentRepository assignmentRepo,
                           RoleService roleService, NotificationService notificationService,
                           MailPort mailPort, BpmnConditionInjector conditionInjector,
                           AuditPort auditPort, ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.processService = processService;
        this.assignmentService = assignmentService;
        this.versionRepo = versionRepo;
        this.instanceRepo = instanceRepo;
        this.userRepo = userRepo;
        this.positionRepo = positionRepo;
        this.assignmentRepo = assignmentRepo;
        this.roleService = roleService;
        this.notificationService = notificationService;
        this.mailPort = mailPort;
        this.conditionInjector = conditionInjector;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
    }

    /** Khởi tạo nhiệm vụ từ quy trình đã publish + dữ liệu form bước đầu (FR-D01). */
    @Transactional
    public WorkflowInstance start(String processId, Map<String, Object> formData, String actor) {
        ProcessDefinition p = processService.get(processId);
        ProcessVersion v = versionRepo.findFirstByProcessIdAndStatusOrderByVersionDesc(processId, ProcessStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Quy trình chưa được ban hành (publish) — không thể khởi tạo"));

        // Tiêm conditionExpression cho nhánh rẽ gateway (Story 3.5b) rồi deploy.
        String bpmn = conditionInjector.inject(v.getBpmnXml(), v.getStepsMetaJson());
        Deployment dep = repositoryService.createDeployment()
                .addString("proc-" + p.getProcessKey() + "-v" + v.getVersion() + ".bpmn20.xml", bpmn)
                .name(p.getProcessKey() + " v" + v.getVersion())
                .deploy();
        ProcessDefinitionQuery q = repositoryService.createProcessDefinitionQuery().deploymentId(dep.getId());
        var pd = q.singleResult();
        if (pd == null) {
            throw new IllegalStateException("Không tìm thấy định nghĩa quy trình sau khi deploy");
        }

        ProcessInstance inst = runtimeService.startProcessInstanceById(
                pd.getId(), formData != null ? formData : Map.of());

        WorkflowInstance wi = instanceRepo.save(
                new WorkflowInstance(processId, v.getVersion(), inst.getId(), v.getStepsMetaJson(), actor));
        auditPort.record("INSTANCE_STARTED", "WorkflowInstance", wi.getId(), actor,
                "process=" + p.getProcessKey() + " v" + v.getVersion() + ", flowableInstance=" + inst.getId());

        // Phân công các việc đang hoạt động theo metadata bước (lõi phân công AD-14).
        assignActiveTasks(inst.getId(), v.getStepsMetaJson(), actor);
        return wi;
    }

    /**
     * Resolve người thực hiện cho mọi việc đang mở của instance từ stepsMeta:
     * POSITION → qua AssignmentService (snapshot người + mirror Flowable); USER → assignee trực tiếp; ROLE → candidate-group.
     */
    private void assignActiveTasks(String flowableInstanceId, String stepsMetaJson, String actor) {
        if (stepsMetaJson == null || stepsMetaJson.isBlank()) {
            return;
        }
        JsonNode meta;
        try {
            meta = objectMapper.readTree(stepsMetaJson);
        } catch (Exception e) {
            log.warn("[workflow] stepsMeta lỗi JSON, bỏ qua phân công: {}", e.getMessage());
            return;
        }
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(flowableInstanceId).orElse(null);
        String procName = wi != null ? safeProcessName(wi.getProcessId()) : "quy trình";
        for (Task t : taskService.createTaskQuery().processInstanceId(flowableInstanceId).list()) {
            JsonNode step = meta.get(t.getTaskDefinitionKey());
            if (step == null) {
                continue;
            }
            String type = step.path("assigneeType").asText("POSITION");
            String aid = step.path("assigneeId").asText(null);
            if (aid == null || aid.isBlank()) {
                continue;
            }
            try {
                switch (type) {
                    case "POSITION" -> {
                        TaskAssignment ta = assignmentService.assignTaskToPosition(t.getId(), aid, actor);
                        notifyAssignee(ta.getAssigneeUserId(), t.getName(), procName);
                    }
                    case "USER" -> {
                        taskService.setAssignee(t.getId(), aid);
                        notifyAssignee(aid, t.getName(), procName);
                    }
                    case "ROLE" -> taskService.addCandidateGroup(t.getId(), aid);
                    default -> { }
                }
            } catch (Exception e) {
                log.warn("[workflow] không phân công được task {} ({}): {}", t.getTaskDefinitionKey(), type, e.getMessage());
            }
        }
    }

    /** Bắn thông báo in-app "việc mới" cho người được giao (Story 4.9). */
    private void notifyAssignee(String userId, String taskName, String procName) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        notificationService.notify(userId, "TASK_ASSIGNED", "Việc mới: " + taskName,
                "Quy trình " + procName + " — bạn có việc cần xử lý.", "/inbox");
    }

    // ===================== Hộp thư việc (Story 3.2) =====================

    /** Việc giao trực tiếp (assignee) + việc theo vai trò đang chờ nhận (candidate-group, Story 3.x). */
    @Transactional(readOnly = true)
    public List<TaskDto.InboxItem> inbox(String username) {
        String userId = userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
        if (userId == null) {
            return List.of();
        }
        List<TaskDto.InboxItem> out = new ArrayList<>();
        // 1) Việc đã giao cho tôi
        for (Task t : taskService.createTaskQuery().taskAssignee(userId)
                .orderByTaskCreateTime().desc().list()) {
            out.add(toInboxItem(t, false));
        }
        // 2) Việc theo vai trò (candidate-group = role code của vị trí tôi giữ), chưa ai nhận
        Set<String> roleCodes = roleService.roleCodesForUser(userId);
        if (!roleCodes.isEmpty()) {
            for (Task t : taskService.createTaskQuery().taskCandidateGroupIn(new ArrayList<>(roleCodes))
                    .taskUnassigned().orderByTaskCreateTime().desc().list()) {
                out.add(toInboxItem(t, true));
            }
        }
        return out;
    }

    private TaskDto.InboxItem toInboxItem(Task t, boolean claimable) {
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
        JsonNode step = wi != null ? stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey()) : null;
        Integer sla = slaHours(step);
        Instant due = effectiveDue(t, sla);
        return new TaskDto.InboxItem(
                t.getId(), t.getName(),
                wi != null ? safeProcessName(wi.getProcessId()) : "—",
                wi != null ? wi.getId() : null,
                t.getCreateTime() != null ? t.getCreateTime().toInstant().toString() : null,
                step != null ? blankToNull(step.path("formId").asText(null)) : null,
                sla,
                due != null ? due.toString() : null,
                due != null && Instant.now().isAfter(due),
                claimable);
    }

    /** Nhận một việc theo vai trò (claim) — Story 3.x. Guard: user phải là ứng viên (giữ vai trò). */
    @Transactional
    public void claim(String taskId, String username) {
        Task t = requireTask(taskId);
        String userId = userRepo.findByUsername(username)
                .map(UserAccount::getId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        if (t.getAssignee() != null) {
            throw new IllegalStateException("Việc đã có người nhận");
        }
        Set<String> roles = roleService.roleCodesForUser(userId);
        long candidate = taskService.createTaskQuery().taskId(taskId)
                .taskCandidateGroupIn(new ArrayList<>(roles.isEmpty() ? List.of("__none__") : roles)).count();
        if (candidate == 0) {
            throw new IllegalStateException("Bạn không có vai trò để nhận việc này");
        }
        taskService.claim(taskId, userId);
        auditPort.record("TASK_CLAIMED", "Task", taskId, username, "step=" + t.getTaskDefinitionKey());
    }

    /** Xin gia hạn xử lý: cộng thêm giờ vào hạn SLA của việc (Story 3.8). GĐ1 tự phục vụ + ghi audit. */
    @Transactional
    public void extend(String taskId, int hours, String reason, String actor) {
        Task t = requireTask(taskId);
        if (hours <= 0) {
            throw new IllegalArgumentException("Số giờ gia hạn phải > 0");
        }
        Integer bonus = (Integer) taskService.getVariableLocal(taskId, "slaBonusHours");
        int total = (bonus != null ? bonus : 0) + hours;
        taskService.setVariableLocal(taskId, "slaBonusHours", total);
        auditPort.record("TASK_EXTENDED", "Task", taskId, actor,
                "+" + hours + "h (tổng " + total + "h), lý do=" + reason + ", step=" + t.getTaskDefinitionKey());
    }

    /** Chi tiết một việc: cấu hình bước (form, quyền trường, hành động) + dữ liệu hiện có. */
    @Transactional(readOnly = true)
    public TaskDto.Detail detail(String taskId) {
        Task t = requireTask(taskId);
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
        Map<String, Object> vars = taskService.getVariables(taskId);
        String formId = null;
        Object fieldPerms = null;
        List<String> actions = new ArrayList<>();
        String procName = "—";
        if (wi != null) {
            procName = safeProcessName(wi.getProcessId());
            JsonNode step = stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey());
            if (step != null) {
                formId = blankToNull(step.path("formId").asText(null));
                if (step.has("fieldPerms") && step.get("fieldPerms").isObject()) {
                    fieldPerms = objectMapper.convertValue(step.get("fieldPerms"), Map.class);
                }
                if (step.has("actions") && step.get("actions").isArray()) {
                    step.get("actions").forEach(a -> actions.add(a.asText()));
                }
            }
        }
        if (actions.isEmpty()) {
            actions.add("Hoàn thành"); // luôn có ít nhất 1 hành động
        }
        return new TaskDto.Detail(t.getId(), t.getName(), procName,
                wi != null ? wi.getId() : null, formId, fieldPerms, actions, vars);
    }

    /** Hoàn thành việc (Story 3.4): ghi dữ liệu form + hành động → luồng tiến → gán việc bước kế (Story 3.5). */
    @Transactional
    public void complete(String taskId, String action, Map<String, Object> formData, String actor) {
        Task t = requireTask(taskId);
        String piid = t.getProcessInstanceId();
        Map<String, Object> vars = new HashMap<>();
        if (formData != null) {
            vars.putAll(formData);
        }
        if (action != null && !action.isBlank()) {
            vars.put("lastAction", action);
        }
        taskService.complete(taskId, vars);
        auditPort.record("TASK_COMPLETED", "Task", taskId, actor,
                "action=" + action + ", instance=" + piid);

        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(piid).orElse(null);
        if (wi != null) {
            // Gán việc bước kế vừa sinh (assign-after — không cần listener, không reentrancy).
            assignActiveTasks(piid, wi.getStepsMetaJson(), actor);
            boolean ended = runtimeService.createProcessInstanceQuery().processInstanceId(piid).count() == 0;
            if (ended) {
                wi.setStatus("COMPLETED");
                instanceRepo.save(wi);
                auditPort.record("INSTANCE_COMPLETED", "WorkflowInstance", wi.getId(), actor, "instance=" + piid);
            }
        }
    }

    private Task requireTask(String taskId) {
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (t == null) {
            throw new IllegalArgumentException("Không tìm thấy việc: " + taskId);
        }
        return t;
    }

    private JsonNode stepMeta(String stepsMetaJson, String stepKey) {
        if (stepsMetaJson == null || stepsMetaJson.isBlank() || stepKey == null) {
            return null;
        }
        try {
            return objectMapper.readTree(stepsMetaJson).get(stepKey);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeProcessName(String processId) {
        try {
            return processService.get(processId).getName();
        } catch (Exception e) {
            return "—";
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** slaHours từ cấu hình bước (null nếu không đặt). */
    private static Integer slaHours(JsonNode step) {
        return (step != null && step.has("slaHours") && step.get("slaHours").canConvertToInt())
                ? step.get("slaHours").asInt() : null;
    }

    /** Hạn xử lý = giờ tạo việc + slaHours (null nếu thiếu dữ liệu). */
    private static Instant dueAt(Date createTime, Integer slaHours) {
        if (createTime == null || slaHours == null || slaHours <= 0) {
            return null;
        }
        return createTime.toInstant().plus(Duration.ofHours(slaHours));
    }

    /** Hạn thực tế = giờ tạo + (slaHours + slaBonusHours đã gia hạn) (Story 3.7 + 3.8). */
    private Instant effectiveDue(Task t, Integer slaHours) {
        if (slaHours == null) {
            return null;
        }
        Object bonus = taskService.getVariableLocal(t.getId(), "slaBonusHours");
        int eff = slaHours + (bonus instanceof Number n ? n.intValue() : 0);
        return dueAt(t.getCreateTime(), eff);
    }

    // ===================== Theo dõi quy trình (Story 3.3) + Hủy (Story 3.6) =====================

    /** Danh sách phiên chạy + bước hiện tại + người đang giữ (màn theo dõi). */
    @Transactional(readOnly = true)
    public List<TaskDto.InstanceListItem> instances() {
        return instanceRepo.findAll().stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .map(this::toInstanceItem).toList();
    }

    /** Hồ sơ do tôi tạo (Story 4.4 — theo dõi nhiệm vụ cá nhân). */
    @Transactional(readOnly = true)
    public List<TaskDto.InstanceListItem> myInstances(String username) {
        return instanceRepo.findAll().stream()
                .filter(wi -> username != null && username.equals(wi.getStartedBy()))
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .map(this::toInstanceItem).toList();
    }

    private TaskDto.InstanceListItem toInstanceItem(WorkflowInstance wi) {
        String currentStep = "—";
        String currentWho = "—";
        boolean overdue = false;
        if ("RUNNING".equals(wi.getStatus())) {
            List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
            if (!ts.isEmpty()) {
                currentStep = ts.stream().map(Task::getName).distinct().collect(java.util.stream.Collectors.joining(", "));
                currentWho = ts.stream().map(t -> userDisplay(t.getAssignee())).distinct().collect(java.util.stream.Collectors.joining(", "));
                Instant now = Instant.now();
                for (Task t : ts) {
                    Instant due = effectiveDue(t, slaHours(stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey())));
                    if (due != null && now.isAfter(due)) {
                        overdue = true;
                        break;
                    }
                }
            }
        }
        String[] ts = titleAndSearch(wi);
        return new TaskDto.InstanceListItem(wi.getId(), safeProcessName(wi.getProcessId()), ts[0],
                wi.getProcessVersion(), wi.getStatus(), userDisplay(wi.getStartedBy()),
                wi.getStartedAt().toString(), currentStep, currentWho, overdue, ts[1]);
    }

    /**
     * Suy ra [tiêu đề, chuỗi tra cứu] từ dữ liệu form (biến tiến trình — projection trường reportable, Story 4.1/4.5).
     * Tiêu đề lấy theo trường thường gặp; chuỗi tra cứu gom mọi giá trị text/số để tìm theo dữ liệu nghiệp vụ.
     */
    private String[] titleAndSearch(WorkflowInstance wi) {
        java.util.Map<String, Object> vars = new java.util.LinkedHashMap<>();
        try {
            historyService.createHistoricVariableInstanceQuery().processInstanceId(wi.getFlowableInstanceId())
                    .list().forEach(v -> vars.put(v.getVariableName(), v.getValue()));
        } catch (Exception e) {
            return new String[]{"—", ""};
        }
        String title = "—";
        for (String key : new String[]{"tieu_de", "so_van_ban", "hang_muc", "loai_nghi", "trich_yeu"}) {
            Object v = vars.get(key);
            if (v != null && !v.toString().isBlank()) {
                String s = v.toString();
                title = s.length() > 80 ? s.substring(0, 77) + "…" : s;
                break;
            }
        }
        StringBuilder search = new StringBuilder();
        for (Object v : vars.values()) {
            if (v != null && (v instanceof String || v instanceof Number)) {
                search.append(v).append(' ');
            }
        }
        return new String[]{title, search.toString().trim()};
    }

    /** Dòng thời gian các bước (đã xong + đang xử lý) của một phiên chạy. */
    @Transactional(readOnly = true)
    public TaskDto.InstanceTimeline timeline(String instanceId) {
        WorkflowInstance wi = get(instanceId);
        List<TaskDto.StepHistory> steps = new ArrayList<>();
        for (HistoricActivityInstance a : historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(wi.getFlowableInstanceId())
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc().list()) {
            boolean done = a.getEndTime() != null;
            steps.add(new TaskDto.StepHistory(
                    a.getActivityName(),
                    userDisplay(a.getAssignee()),
                    a.getStartTime() != null ? a.getStartTime().toInstant().toString() : null,
                    done ? a.getEndTime().toInstant().toString() : null,
                    done ? "DONE" : "ACTIVE"));
        }
        return new TaskDto.InstanceTimeline(wi.getId(), safeProcessName(wi.getProcessId()), wi.getStatus(), steps);
    }

    /** Hủy một phiên chạy đang RUNNING (Story 3.6) — xóa instance Flowable + đánh dấu CANCELLED + audit. */
    @Transactional
    public void cancel(String instanceId, String reason, String actor) {
        WorkflowInstance wi = get(instanceId);
        if (!"RUNNING".equals(wi.getStatus())) {
            throw new IllegalStateException("Chỉ hủy được phiên đang chạy (hiện: " + wi.getStatus() + ")");
        }
        // Cascade (Story 3.9): hủy snapshot phân công của các việc đang mở TRƯỚC khi xóa instance Flowable.
        int cascaded = 0;
        for (Task t : taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list()) {
            TaskAssignment ta = assignmentRepo.findByTaskId(t.getId()).orElse(null);
            if (ta != null && ta.getStatus() != com.bpm.domain.assignment.AssignmentStatus.CANCELLED) {
                ta.cancel();
                assignmentRepo.save(ta);
                cascaded++;
            }
        }
        runtimeService.deleteProcessInstance(wi.getFlowableInstanceId(),
                reason != null && !reason.isBlank() ? reason : "Hủy bởi " + actor);
        wi.setStatus("CANCELLED");
        instanceRepo.save(wi);
        auditPort.record("INSTANCE_CANCELLED", "WorkflowInstance", wi.getId(), actor,
                "reason=" + reason + ", cascadeAssignments=" + cascaded + ", flowableInstance=" + wi.getFlowableInstanceId());
    }

    /** userId → "Họ tên (username)"; rỗng → (chưa gán). */
    private String userDisplay(String userId) {
        if (userId == null || userId.isBlank()) {
            return "(chưa gán)";
        }
        return userRepo.findById(userId)
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank()
                        ? u.getFullName() : u.getUsername())
                .orElse(userId);
    }

    // ===================== Dashboard vận hành (Epic 4: 4.2/4.3) =====================

    /** Tổng quan: số hồ sơ theo trạng thái, số việc mở/quá hạn, phân rã theo quy trình. */
    @Transactional(readOnly = true)
    public com.bpm.api.dto.DashboardDto.Summary dashboardSummary() {
        long running = 0, completed = 0, cancelled = 0;
        java.util.Map<String, long[]> byProc = new java.util.LinkedHashMap<>(); // name -> [running, completed]
        for (WorkflowInstance wi : instanceRepo.findAll()) {
            String name = safeProcessName(wi.getProcessId());
            long[] c = byProc.computeIfAbsent(name, k -> new long[2]);
            switch (wi.getStatus()) {
                case "RUNNING" -> { running++; c[0]++; }
                case "COMPLETED" -> { completed++; c[1]++; }
                case "CANCELLED" -> cancelled++;
                default -> { }
            }
        }
        List<Task> tasks = taskService.createTaskQuery().list();
        long overdue = tasks.stream().filter(this::taskOverdue).count();
        List<com.bpm.api.dto.DashboardDto.ProcessStat> procStats = byProc.entrySet().stream()
                .map(e -> new com.bpm.api.dto.DashboardDto.ProcessStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.running() + b.completed(), a.running() + a.completed()))
                .toList();
        return new com.bpm.api.dto.DashboardDto.Summary(running + completed + cancelled, running, completed, cancelled,
                tasks.size(), overdue, procStats);
    }

    /** "Ai đang làm gì": tải việc đang mở + quá hạn theo từng người. */
    @Transactional(readOnly = true)
    public List<com.bpm.api.dto.DashboardDto.WorkloadItem> workload() {
        java.util.Map<String, long[]> by = new java.util.LinkedHashMap<>(); // userId -> [open, overdue]
        for (Task t : taskService.createTaskQuery().list()) {
            String a = t.getAssignee();
            if (a == null) {
                continue;
            }
            long[] cnt = by.computeIfAbsent(a, k -> new long[2]);
            cnt[0]++;
            if (taskOverdue(t)) {
                cnt[1]++;
            }
        }
        return by.entrySet().stream()
                .map(e -> new com.bpm.api.dto.DashboardDto.WorkloadItem(userDisplay(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.openTasks(), a.openTasks()))
                .toList();
    }

    /** Báo cáo 4 lát cắt (quy trình / trạng thái / người / thời gian) lọc theo khoảng tạo hồ sơ (Story 4.6). */
    @Transactional(readOnly = true)
    public com.bpm.api.dto.ReportDto.Report report(Instant from, Instant to) {
        // Lọc hồ sơ theo thời gian khởi tạo.
        List<WorkflowInstance> insts = instanceRepo.findAll().stream()
                .filter(wi -> inRange(wi.getStartedAt(), from, to))
                .toList();

        // Lát cắt quy trình + trạng thái + thời gian.
        java.util.Map<String, long[]> byProc = new java.util.LinkedHashMap<>(); // [total, running, completed, cancelled]
        long run = 0, done = 0, can = 0;
        java.util.Map<String, Long> byDate = new java.util.TreeMap<>();
        for (WorkflowInstance wi : insts) {
            long[] c = byProc.computeIfAbsent(safeProcessName(wi.getProcessId()), k -> new long[4]);
            c[0]++;
            switch (wi.getStatus()) {
                case "RUNNING" -> { c[1]++; run++; }
                case "COMPLETED" -> { c[2]++; done++; }
                case "CANCELLED" -> { c[3]++; can++; }
                default -> { }
            }
            String d = wi.getStartedAt().atZone(ZoneId.systemDefault()).toLocalDate().toString();
            byDate.merge(d, 1L, Long::sum);
        }
        List<com.bpm.api.dto.ReportDto.ProcessRow> procRows = byProc.entrySet().stream()
                .map(e -> new com.bpm.api.dto.ReportDto.ProcessRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted((a, b) -> Long.compare(b.total(), a.total())).toList();
        List<com.bpm.api.dto.ReportDto.TimeRow> timeRows = byDate.entrySet().stream()
                .map(e -> new com.bpm.api.dto.ReportDto.TimeRow(e.getKey(), e.getValue())).toList();

        // Lát cắt người: việc đã xử lý (hoàn thành trong kỳ) + việc đang giữ.
        java.util.Map<String, long[]> byUser = new java.util.LinkedHashMap<>(); // [processed, open]
        for (HistoricTaskInstance h : historyService.createHistoricTaskInstanceQuery().finished().list()) {
            if (h.getAssignee() == null || !inRange(h.getEndTime() != null ? h.getEndTime().toInstant() : null, from, to)) {
                continue;
            }
            byUser.computeIfAbsent(h.getAssignee(), k -> new long[2])[0]++;
        }
        for (Task t : taskService.createTaskQuery().list()) {
            if (t.getAssignee() != null) {
                byUser.computeIfAbsent(t.getAssignee(), k -> new long[2])[1]++;
            }
        }
        List<com.bpm.api.dto.ReportDto.PersonRow> personRows = byUser.entrySet().stream()
                .map(e -> new com.bpm.api.dto.ReportDto.PersonRow(userDisplay(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.processed() + b.open(), a.processed() + a.open())).toList();

        return new com.bpm.api.dto.ReportDto.Report(
                from != null ? from.toString() : null, to != null ? to.toString() : null,
                procRows, new com.bpm.api.dto.ReportDto.StatusSummary(run + done + can, run, done, can),
                personRows, timeRows);
    }

    private static boolean inRange(Instant t, Instant from, Instant to) {
        if (t == null) {
            return false;
        }
        if (from != null && t.isBefore(from)) {
            return false;
        }
        return to == null || !t.isAfter(to);
    }

    /** Một việc có quá hạn không (theo SLA bước + giờ gia hạn). */
    private boolean taskOverdue(Task t) {
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
        if (wi == null) {
            return false;
        }
        Instant due = effectiveDue(t, slaHours(stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey())));
        return due != null && Instant.now().isAfter(due);
    }

    /**
     * Quét việc quá hạn → nhắc người thực hiện qua thông báo in-app + email (Story 4.10).
     * Chống spam: mỗi việc chỉ nhắc lại sau {@code cooldownHours} giờ (lưu mốc ở task-local var).
     */
    @Transactional
    public int runOverdueReminders(int cooldownHours) {
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, cooldownHours) * 3_600_000L;
        int sent = 0;
        for (Task t : taskService.createTaskQuery().list()) {
            if (t.getAssignee() == null || !taskOverdue(t)) {
                continue;
            }
            Object last = taskService.getVariableLocal(t.getId(), "lastReminderAt");
            if (last instanceof Number n && now - n.longValue() < cooldownMs) {
                continue;
            }
            UserAccount u = userRepo.findById(t.getAssignee()).orElse(null);
            String name = u != null && u.getFullName() != null ? u.getFullName() : t.getAssignee();
            String email = u != null ? u.getEmail() : null;
            WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
            String proc = wi != null ? safeProcessName(wi.getProcessId()) : "quy trình";

            notificationService.notify(t.getAssignee(), "TASK_OVERDUE", "Quá hạn: " + t.getName(),
                    "Việc thuộc " + proc + " đã quá hạn xử lý.", "/inbox");
            mailPort.send(email, "[BPM] Nhắc hạn xử lý: " + t.getName(),
                    "Kính gửi " + name + ",\n\nViệc \"" + t.getName() + "\" thuộc " + proc
                            + " đã QUÁ HẠN xử lý. Vui lòng đăng nhập hệ thống để hoàn thành sớm.\n\nTrân trọng.");
            auditPort.record("TASK_OVERDUE_REMINDER", "Task", t.getId(), "system",
                    "assignee=" + name + ", step=" + t.getTaskDefinitionKey());
            taskService.setVariableLocal(t.getId(), "lastReminderAt", now);
            sent++;
        }
        if (sent > 0) {
            log.info("[reminder] đã nhắc {} việc quá hạn", sent);
        }
        return sent;
    }

    /** Việc đang mở đầu tiên của một phiên (để mở form ngay sau khi tạo yêu cầu). */
    @Transactional(readOnly = true)
    public String firstOpenTaskId(String flowableInstanceId) {
        List<Task> ts = taskService.createTaskQuery().processInstanceId(flowableInstanceId)
                .orderByTaskCreateTime().asc().list();
        return ts.isEmpty() ? null : ts.get(0).getId();
    }

    /** Quy trình đã ban hành mà mọi người dùng có thể tạo yêu cầu (màn "Tạo yêu cầu"). */
    @Transactional(readOnly = true)
    public List<com.bpm.api.dto.WorkflowDto.StartableProcess> startable() {
        List<com.bpm.api.dto.WorkflowDto.StartableProcess> out = new ArrayList<>();
        for (ProcessDefinition p : processService.list()) {
            if (p.getStatus() == ProcessStatus.PUBLISHED) {
                out.add(new com.bpm.api.dto.WorkflowDto.StartableProcess(p.getId(), p.getProcessKey(), p.getName()));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> byProcess(String processId) {
        return instanceRepo.findByProcessId(processId);
    }

    @Transactional(readOnly = true)
    public WorkflowInstance get(String id) {
        return instanceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên chạy"));
    }
}
