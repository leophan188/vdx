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
    private final FormService formService;
    private final com.bpm.infrastructure.OrgUnitRepository orgUnitRepo;
    private final DocumentService documentService;

    /** Cache thứ tự bước (userTask) theo processDefinitionId của Flowable + nhãn trường theo formId. */
    private final Map<String, java.util.LinkedHashMap<String, String>> stepOrderCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, JsonNode> formFieldsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public WorkflowService(RepositoryService repositoryService, RuntimeService runtimeService,
                           TaskService taskService, HistoryService historyService, ProcessService processService,
                           AssignmentService assignmentService, ProcessVersionRepository versionRepo,
                           WorkflowInstanceRepository instanceRepo, UserAccountRepository userRepo,
                           PositionRepository positionRepo, TaskAssignmentRepository assignmentRepo,
                           RoleService roleService, NotificationService notificationService,
                           MailPort mailPort, BpmnConditionInjector conditionInjector,
                           AuditPort auditPort, ObjectMapper objectMapper, FormService formService,
                           com.bpm.infrastructure.OrgUnitRepository orgUnitRepo, DocumentService documentService) {
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
        this.formService = formService;
        this.orgUnitRepo = orgUnitRepo;
        this.documentService = documentService;
    }

    /**
     * Thứ tự các bước (userTask) của một định nghĩa quy trình Flowable — đi từ Bắt đầu theo luồng.
     * Trả LinkedHashMap key(userTask id) → tên bước. Cache theo processDefinitionId.
     */
    private java.util.LinkedHashMap<String, String> orderedUserTasks(String processDefinitionId) {
        if (processDefinitionId == null) {
            return new java.util.LinkedHashMap<>();
        }
        return stepOrderCache.computeIfAbsent(processDefinitionId, pdId -> {
            java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
            try {
                org.flowable.bpmn.model.BpmnModel model = repositoryService.getBpmnModel(pdId);
                org.flowable.bpmn.model.Process proc = model.getMainProcess();
                org.flowable.bpmn.model.FlowElement start = null;
                for (org.flowable.bpmn.model.FlowElement fe : proc.getFlowElements()) {
                    if (fe instanceof org.flowable.bpmn.model.StartEvent) {
                        start = fe;
                        break;
                    }
                }
                if (start == null) { // fallback: mọi userTask theo thứ tự khai báo
                    for (org.flowable.bpmn.model.FlowElement fe : proc.getFlowElements()) {
                        if (fe instanceof org.flowable.bpmn.model.UserTask ut) {
                            out.put(ut.getId(), ut.getName());
                        }
                    }
                    return out;
                }
                java.util.Deque<String> queue = new java.util.ArrayDeque<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                queue.add(start.getId());
                seen.add(start.getId());
                while (!queue.isEmpty()) {
                    org.flowable.bpmn.model.FlowElement fe = proc.getFlowElement(queue.poll());
                    if (fe instanceof org.flowable.bpmn.model.FlowNode fn) {
                        for (org.flowable.bpmn.model.SequenceFlow sf : fn.getOutgoingFlows()) {
                            String tgt = sf.getTargetRef();
                            if (tgt != null && seen.add(tgt)) {
                                org.flowable.bpmn.model.FlowElement te = proc.getFlowElement(tgt);
                                if (te instanceof org.flowable.bpmn.model.UserTask ut) {
                                    out.put(ut.getId(), ut.getName());
                                }
                                queue.add(tgt);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[order] không dựng được thứ tự bước cho {}: {}", pdId, e.getMessage());
            }
            return out;
        });
    }

    /** Các trường (JsonNode) của một biểu mẫu theo thứ tự schema — để đọc key/label/type/criteria. Cache theo formId. */
    private JsonNode formFieldsNode(String formId) {
        if (formId == null) {
            return objectMapper.createArrayNode();
        }
        return formFieldsCache.computeIfAbsent(formId, fid -> {
            try {
                return objectMapper.readTree(formService.get(fid).getSchemaJson()).path("fields");
            } catch (Exception e) {
                return objectMapper.createArrayNode();
            }
        });
    }

    /**
     * Trường của biểu mẫu ƯU TIÊN theo snapshot của instance (đóng băng lúc khởi tạo). Nếu snapshot không có
     * (instance cũ trước khi có tính năng) mới fallback về form hiện hành. Nhờ vậy dữ liệu cũ không đứt đoạn.
     */
    private JsonNode formFieldsNode(String formId, JsonNode formsSnapshot) {
        if (formId != null && formsSnapshot != null && formsSnapshot.hasNonNull(formId)) {
            try {
                return objectMapper.readTree(formsSnapshot.get(formId).asText()).path("fields");
            } catch (Exception ignore) {
                // schema snapshot hỏng — fallback form hiện hành
            }
        }
        return formFieldsNode(formId);
    }

    /** Parse snapshot biểu mẫu {formId: schemaJson} của instance thành node (null nếu trống). */
    private JsonNode parseFormsSnapshot(String formsJson) {
        if (formsJson == null || formsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(formsJson);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lấy biểu mẫu BƯỚC ĐẦU của quy trình mà KHÔNG tạo instance — để nhập nháp (chỉ tạo hồ sơ khi Gửi/soạn
     * thảo tài liệu, tránh sinh hồ sơ rác khi chỉ chọn quy trình). Bước đầu = bước có người thực hiện, theo
     * thứ tự khoá tự nhiên (khớp versionSteps). Instance thật vẫn do start()+firstOpenTaskId quyết định.
     */
    @Transactional(readOnly = true)
    public com.bpm.api.dto.WorkflowDto.StartForm startForm(String processId) {
        ProcessDefinition p = processService.get(processId);
        if (p.getStatus() == ProcessStatus.RETIRED) {
            throw new IllegalStateException("Quy trình đã ngừng dùng — không thể khởi tạo đơn mới");
        }
        ProcessVersion v = versionRepo.findFirstByProcessIdAndStatusOrderByVersionDesc(processId, ProcessStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Quy trình chưa được ban hành (publish) — không thể khởi tạo"));
        JsonNode meta;
        try {
            meta = objectMapper.readTree(v.getStepsMetaJson());
        } catch (Exception e) {
            throw new IllegalStateException("Cấu hình bước của quy trình lỗi JSON");
        }
        String firstKey = null;
        List<String> keys = new ArrayList<>();
        meta.fieldNames().forEachRemaining(keys::add);
        keys.sort(java.util.Comparator.naturalOrder());
        for (String k : keys) {
            JsonNode s = meta.get(k);
            if (s != null && s.has("assigneeType")) { firstKey = k; break; }
        }
        if (firstKey == null) {
            throw new IllegalStateException("Quy trình không có bước nhập liệu đầu tiên");
        }
        JsonNode step = meta.get(firstKey);
        String formId = blankToNull(step.path("formId").asText(null));
        String formSchemaJson = null;
        // Đơn MỚI chưa có dữ liệu → ưu tiên schema HIỆN HÀNH của biểu mẫu để GIÁ TRỊ MẶC ĐỊNH / sửa form
        // có hiệu lực NGAY (không phải ban hành lại quy trình). Fallback: snapshot đóng băng của phiên bản.
        if (formId != null) {
            try {
                formSchemaJson = blankToNull(formService.get(formId).getSchemaJson());
            } catch (Exception ignore) { /* form không còn → dùng snapshot */ }
        }
        if (formSchemaJson == null) {
            String formsJson = v.getFormsJson() != null ? v.getFormsJson()
                    : processService.buildFormsSnapshot(v.getStepsMetaJson());
            JsonNode formsSnapshot = parseFormsSnapshot(formsJson);
            if (formId != null && formsSnapshot != null && formsSnapshot.hasNonNull(formId)) {
                formSchemaJson = formsSnapshot.get(formId).asText();
            }
        }
        Map<String, String> fieldPerms = null;
        if (step.has("fieldPerms") && step.get("fieldPerms").isObject()) {
            fieldPerms = objectMapper.convertValue(step.get("fieldPerms"),
                    objectMapper.getTypeFactory().constructMapType(java.util.LinkedHashMap.class, String.class, String.class));
        }
        List<String> actions = new ArrayList<>();
        if (step.has("actions") && step.get("actions").isArray()) {
            step.get("actions").forEach(a -> actions.add(a.asText()));
        }
        if (actions.isEmpty()) {
            actions.add("Hoàn thành");
        }
        boolean officeDoc = step.path("officeDoc").asBoolean(false);
        String stepName = step.path("name").asText(step.path("statusLabel").asText(firstKey));
        return new com.bpm.api.dto.WorkflowDto.StartForm(processId, p.getName(), v.getVersion(),
                firstKey, stepName, formId, formSchemaJson, fieldPerms, actions, officeDoc);
    }

    /** Khởi tạo nhiệm vụ từ quy trình đã publish + dữ liệu form bước đầu (FR-D01). */
    @Transactional
    public WorkflowInstance start(String processId, Map<String, Object> formData, String actor) {
        ProcessDefinition p = processService.get(processId);
        if (p.getStatus() == ProcessStatus.RETIRED) {
            throw new IllegalStateException("Quy trình đã ngừng dùng — không thể khởi tạo đơn mới (Ban hành lại để tiếp tục)");
        }
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

        // Đóng băng schema biểu mẫu vào instance: version mới đã có sẵn (publish); version cũ thì dựng tại đây.
        String formsSnapshot = v.getFormsJson() != null ? v.getFormsJson()
                : processService.buildFormsSnapshot(v.getStepsMetaJson());
        WorkflowInstance wi = instanceRepo.save(
                new WorkflowInstance(processId, v.getVersion(), inst.getId(), v.getStepsMetaJson(), formsSnapshot, actor));
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
        return inbox(username, false);
    }

    /**
     * Hộp thư việc. Người thường: chỉ việc của mình + việc vai trò chờ nhận.
     * ADMIN ({@code seeAll}=true): xem HẾT việc đang mở của mọi người (không phân quyền theo người giao).
     */
    @Transactional(readOnly = true)
    public List<TaskDto.InboxItem> inbox(String username, boolean seeAll) {
        if (seeAll) {
            List<TaskDto.InboxItem> all = new ArrayList<>();
            for (Task t : taskService.createTaskQuery().orderByTaskCreateTime().desc().list()) {
                all.add(toInboxItem(t, false));
            }
            return all;
        }
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
        // Vị trí bước hiện tại trong tổng thể quy trình (vd "Bước 6/10").
        java.util.LinkedHashMap<String, String> ordered = orderedUserTasks(t.getProcessDefinitionId());
        int total = ordered.size();
        int idx = 0, i = 1;
        for (String k : ordered.keySet()) {
            if (k.equals(t.getTaskDefinitionKey())) {
                idx = i;
                break;
            }
            i++;
        }
        return new TaskDto.InboxItem(
                t.getId(), t.getName(),
                wi != null ? safeProcessName(wi.getProcessId()) : "—",
                wi != null ? wi.getId() : null,
                t.getCreateTime() != null ? t.getCreateTime().toInstant().toString() : null,
                step != null ? blankToNull(step.path("formId").asText(null)) : null,
                sla,
                due != null ? due.toString() : null,
                due != null && Instant.now().isAfter(due),
                claimable, idx, total);
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
        String formSchemaJson = null;
        String priorEditFieldsJson = null;
        int procVersion = 0;
        Object fieldPerms = null;
        List<String> actions = new ArrayList<>();
        boolean officeDoc = false;
        String procName = "—";
        if (wi != null) {
            procName = safeProcessName(wi.getProcessId());
            procVersion = wi.getProcessVersion();
            JsonNode step = stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey());
            if (step != null) {
                formId = blankToNull(step.path("formId").asText(null));
                // Trường của BƯỚC TRƯỚC được phép SỬA ở bước này (cấu hình editPriorKeys).
                if (step.has("editPriorKeys") && step.get("editPriorKeys").isArray() && step.get("editPriorKeys").size() > 0) {
                    Map<String, JsonNode> defs = collectFieldDefs(wi);
                    com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
                    for (JsonNode k : step.get("editPriorKeys")) {
                        String key = k.asText(null);
                        if (key != null && defs.containsKey(key)) {
                            arr.add(defs.get(key));
                        }
                    }
                    if (arr.size() > 0) {
                        priorEditFieldsJson = arr.toString();
                    }
                }
                // Đóng băng schema theo phiên bản instance: form của bước hiện tại lấy từ snapshot (nếu có).
                JsonNode formsSnapshot = parseFormsSnapshot(wi.getFormsJson());
                if (formId != null && formsSnapshot != null && formsSnapshot.hasNonNull(formId)) {
                    formSchemaJson = formsSnapshot.get(formId).asText();
                }
                if (step.has("fieldPerms") && step.get("fieldPerms").isObject()) {
                    fieldPerms = objectMapper.convertValue(step.get("fieldPerms"), Map.class);
                }
                if (step.has("actions") && step.get("actions").isArray()) {
                    step.get("actions").forEach(a -> actions.add(a.asText()));
                }
                officeDoc = step.path("officeDoc").asBoolean(false);
            }
        }
        if (actions.isEmpty()) {
            actions.add("Hoàn thành"); // luôn có ít nhất 1 hành động
        }
        // Dữ liệu các BƯỚC TRƯỚC đã hoàn thành (để người xử lý xem lại ngữ cảnh trước khi làm bước này).
        List<TaskDto.StepView> priorSteps = List.of();
        if (wi != null) {
            priorSteps = buildStepViews(wi).stream()
                    .filter(s -> "DONE".equals(s.status()))
                    .toList();
        }
        return new TaskDto.Detail(t.getId(), t.getName(), t.getTaskDefinitionKey(), procName,
                wi != null ? wi.getId() : null, formId, formSchemaJson, procVersion, fieldPerms, actions, vars,
                priorSteps, priorEditFieldsJson, officeDoc);
    }

    /** Lấy/tạo tài liệu OnlyOffice của BƯỚC hiện tại (bước bật officeDoc). Trả documentId để FE nhúng editor. */
    @Transactional
    public String officeDocForTask(String taskId, String actor) {
        Task t = requireTask(taskId);
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
        if (wi == null) {
            throw new IllegalStateException("Việc không thuộc phiên chạy nào");
        }
        JsonNode step = stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey());
        if (step == null || !step.path("officeDoc").asBoolean(false)) {
            throw new IllegalStateException("Bước này không bật soạn thảo tài liệu");
        }
        String docName = safeProcessName(wi.getProcessId()) + " — " + t.getName();
        String templateId = blankToNull(step.path("officeTemplateId").asText(null));
        // Dữ liệu Hồ sơ để TRỘN vào mẫu (thay «tênTrường»): biến đã thu ở các bước (form data theo key trường).
        Map<String, String> mergeValues = mergeValues(taskId, wi);
        return documentService.getOrCreateForStep(wi.getId(), t.getTaskDefinitionKey(), docName, templateId,
                mergeValues, actor).getId();
    }

    /**
     * Lấy/tạo TẤT CẢ tài liệu OnlyOffice của bước (cấu hình NHIỀU mẫu ở Designer → step.officeDocs).
     * Mỗi tài liệu 1 file riêng (slot = taskKey#index). Không cấu hình officeDocs → 1 tài liệu theo officeTemplateId (cũ).
     */
    @Transactional
    public java.util.List<java.util.Map<String, Object>> officeDocsForTask(String taskId, String actor) {
        Task t = requireTask(taskId);
        WorkflowInstance wi = instanceRepo.findByFlowableInstanceId(t.getProcessInstanceId()).orElse(null);
        if (wi == null) {
            throw new IllegalStateException("Việc không thuộc phiên chạy nào");
        }
        JsonNode step = stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey());
        if (step == null || !step.path("officeDoc").asBoolean(false)) {
            throw new IllegalStateException("Bước này không bật soạn thảo tài liệu");
        }
        Map<String, String> mergeValues = mergeValues(taskId, wi);
        String procName = safeProcessName(wi.getProcessId());
        java.util.List<java.util.Map<String, Object>> out = new ArrayList<>();
        JsonNode docs = step.path("officeDocs");
        if (docs.isArray() && docs.size() > 0) {
            int i = 0;
            for (JsonNode d : docs) {
                String name = blankToNull(d.path("name").asText(null));
                String tpl = blankToNull(d.path("templateId").asText(null));
                String label = name != null ? name : (t.getName() + " " + (i + 1));
                String slotKey = t.getTaskDefinitionKey() + "#" + i;
                String id = documentService.getOrCreateForStep(wi.getId(), slotKey, procName + " — " + label,
                        tpl, mergeValues, actor).getId();
                out.add(docRef(id, label));
                i++;
            }
        } else {
            String tpl = blankToNull(step.path("officeTemplateId").asText(null));
            String id = documentService.getOrCreateForStep(wi.getId(), t.getTaskDefinitionKey(),
                    procName + " — " + t.getName(), tpl, mergeValues, actor).getId();
            out.add(docRef(id, t.getName()));
        }
        return out;
    }

    private static java.util.Map<String, Object> docRef(String id, String name) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    /** Bản đồ key trường → GIÁ TRỊ hiển thị (từ biến phiên chạy) để trộn vào mẫu docx. */
    private Map<String, String> mergeValues(String taskId, WorkflowInstance wi) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        Map<String, Object> vars = taskService.getVariables(taskId);
        Map<String, JsonNode> defs = collectFieldDefs(wi);
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            out.put(e.getKey(), formatMergeValue(e.getValue(), defs.get(e.getKey())));
        }
        return out;
    }

    /** Chuyển giá trị biến sang chuỗi hiển thị: boolean→Có/Không; tập hợp→nối phẩy; còn lại→toString. */
    private String formatMergeValue(Object v, JsonNode def) {
        if (v == null) {
            return "";
        }
        if (v instanceof Boolean b) {
            return b ? "Có" : "Không";
        }
        if (v instanceof java.util.Collection<?> c) {
            return c.stream().map(x -> x == null ? "" : String.valueOf(x)).collect(java.util.stream.Collectors.joining(", "));
        }
        return String.valueOf(v);
    }

    /** Bản đồ key trường → định nghĩa (JsonNode) gộp từ mọi biểu mẫu của instance (snapshot ưu tiên, fallback form hiện hành). */
    private Map<String, JsonNode> collectFieldDefs(WorkflowInstance wi) {
        Map<String, JsonNode> map = new HashMap<>();
        JsonNode snap = parseFormsSnapshot(wi.getFormsJson());
        if (snap != null) {
            snap.fields().forEachRemaining(e -> indexFields(map, e.getValue().asText()));
        } else {
            JsonNode meta;
            try {
                meta = objectMapper.readTree(wi.getStepsMetaJson());
            } catch (Exception ex) {
                return map;
            }
            java.util.Set<String> formIds = new java.util.LinkedHashSet<>();
            meta.forEach(s -> {
                String fid = s.path("formId").asText(null);
                if (fid != null && !fid.isBlank()) {
                    formIds.add(fid);
                }
            });
            for (String fid : formIds) {
                try {
                    indexFields(map, formService.get(fid).getSchemaJson());
                } catch (Exception ignore) { /* form không còn */ }
            }
        }
        return map;
    }

    private void indexFields(Map<String, JsonNode> map, String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        try {
            JsonNode fields = objectMapper.readTree(schemaJson).path("fields");
            if (fields.isArray()) {
                for (JsonNode f : fields) {
                    String k = f.path("key").asText(null);
                    if (k != null && !k.isBlank() && !map.containsKey(k)) {
                        map.put(k, f);
                    }
                }
            }
        } catch (Exception ignore) { /* schema hỏng */ }
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
        // Đảm bảo mọi biến ĐIỀU KIỆN GATEWAY tồn tại (mặc định false) → tránh Flowable ném
        // "Cannot resolve identifier" khi trường điều kiện chưa được nhập (kể cả điều kiện cứng trong BPMN).
        WorkflowInstance wiPre = instanceRepo.findByFlowableInstanceId(piid).orElse(null);
        if (wiPre != null) {
            Map<String, Object> existing = runtimeService.getVariables(piid);
            for (String key : conditionFieldKeys(wiPre.getStepsMetaJson())) {
                if (!vars.containsKey(key) && (existing == null || !existing.containsKey(key))) {
                    vars.put(key, false);
                }
            }
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

    /** Key các trường xuất hiện trong ĐIỀU KIỆN gateway (stepsMeta[*].condition.field) — để đảm bảo biến tồn tại. */
    private java.util.Set<String> conditionFieldKeys(String stepsMetaJson) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (stepsMetaJson == null || stepsMetaJson.isBlank()) {
            return keys;
        }
        try {
            JsonNode meta = objectMapper.readTree(stepsMetaJson);
            meta.forEach(s -> {
                JsonNode c = s.path("condition");
                if (c.isObject()) {
                    String f = c.path("field").asText(null);
                    if (f != null && !f.isBlank()) {
                        keys.add(f);
                    }
                }
            });
        } catch (Exception ignore) { /* meta lỗi → bỏ qua */ }
        return keys;
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
        List<TaskDto.InstanceListItem> out = new ArrayList<>();
        for (WorkflowInstance wi : instanceRepo.findAll().stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt())).toList()) {
            try {
                out.add(toInstanceItem(wi));
            } catch (Exception e) {
                log.warn("[tracking] bỏ qua hồ sơ lỗi {}: {}", wi.getId(), e.toString());
            }
        }
        return out;
    }

    /** Cấu hình các bước của MỘT phiên bản đã ban hành (để xem lại bản cũ) — assignee đã resolve ra tên. */
    @Transactional(readOnly = true)
    public List<com.bpm.api.dto.ProcessDto.VersionStep> versionSteps(String processId, int version) {
        List<com.bpm.api.dto.ProcessDto.VersionStep> out = new ArrayList<>();
        ProcessVersion v = versionRepo.findByProcessIdOrderByVersionDesc(processId).stream()
                .filter(pv -> pv.getVersion() == version).findFirst().orElse(null);
        if (v == null || v.getStepsMetaJson() == null || v.getStepsMetaJson().isBlank()) {
            return out;
        }
        JsonNode meta;
        try {
            meta = objectMapper.readTree(v.getStepsMetaJson());
        } catch (Exception e) {
            return out;
        }
        Map<String, String> roleNames = new HashMap<>();
        roleService.listRoles().forEach(r -> roleNames.put(r.getCode(), r.getName()));
        List<String> keys = new ArrayList<>();
        meta.fieldNames().forEachRemaining(keys::add);
        keys.sort(java.util.Comparator.naturalOrder());
        for (String k : keys) {
            JsonNode s = meta.get(k);
            if (s == null || !s.has("assigneeType")) {
                continue; // bỏ qua flow/gateway (không có người thực hiện)
            }
            String type = s.path("assigneeType").asText("");
            String aid = blankToNull(s.path("assigneeId").asText(null));
            String assignee = switch (type) {
                case "USER" -> aid != null ? userDisplay(aid) : "—";
                case "POSITION" -> aid != null ? positionRepo.findById(aid)
                        .map(com.bpm.domain.position.Position::getTitle).orElse(aid) : "—";
                case "ROLE" -> aid != null ? "Vai trò: " + roleNames.getOrDefault(aid, aid) : "—";
                default -> "—";
            };
            String label = s.path("statusLabel").asText(s.path("name").asText(k));
            out.add(new com.bpm.api.dto.ProcessDto.VersionStep(k, label, type, assignee));
        }
        return out;
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
        String currentStatus = null;
        boolean overdue = false;
        if ("RUNNING".equals(wi.getStatus())) {
            List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
            if (!ts.isEmpty()) {
                currentStep = ts.stream().map(Task::getName).distinct().collect(java.util.stream.Collectors.joining(", "));
                currentWho = ts.stream().map(t -> userDisplay(t.getAssignee())).distinct().collect(java.util.stream.Collectors.joining(", "));
                // Trạng thái nghiệp vụ = statusLabel của bước đang mở (nếu cấu hình).
                currentStatus = ts.stream()
                        .map(t -> stepStatusLabel(wi.getStepsMetaJson(), t.getTaskDefinitionKey()))
                        .filter(s -> s != null).distinct().collect(java.util.stream.Collectors.joining(", "));
                if (currentStatus.isBlank()) {
                    currentStatus = null;
                }
                Instant now = Instant.now();
                for (Task t : ts) {
                    Instant due = effectiveDue(t, slaHours(stepMeta(wi.getStepsMetaJson(), t.getTaskDefinitionKey())));
                    if (due != null && now.isAfter(due)) {
                        overdue = true;
                        break;
                    }
                }
            }
        } else if ("COMPLETED".equals(wi.getStatus())) {
            currentStatus = "Đã hoàn thành";
        } else if ("CANCELLED".equals(wi.getStatus())) {
            currentStatus = "Hủy nhiệm vụ";
        }
        String[] ts = titleAndSearch(wi);
        return new TaskDto.InstanceListItem(wi.getId(), safeProcessName(wi.getProcessId()), ts[0],
                wi.getProcessVersion(), wi.getStatus(), userDisplay(wi.getStartedBy()),
                wi.getStartedAt().toString(), currentStep, currentStatus, currentWho, overdue, ts[1]);
    }

    /** statusLabel (trạng thái nghiệp vụ) cấu hình cho một bước, null nếu không có. */
    private String stepStatusLabel(String stepsMetaJson, String stepKey) {
        JsonNode step = stepMeta(stepsMetaJson, stepKey);
        if (step != null && step.has("statusLabel")) {
            String s = step.get("statusLabel").asText(null);
            return (s != null && !s.isBlank()) ? s : null;
        }
        return null;
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

    /**
     * Tổng quan quy trình theo TOÀN BỘ các bước (kể cả chưa tới): mỗi bước có trạng thái
     * DONE/ACTIVE/PENDING + người + thời gian + DỮ LIỆU đã nhập ở bước (đọc từ biến tiến trình + nhãn biểu mẫu bước).
     */
    @Transactional(readOnly = true)
    public TaskDto.InstanceOverview overview(String instanceId) {
        WorkflowInstance wi = get(instanceId);
        List<TaskDto.StepView> steps = buildStepViews(wi);
        int currentIndex = 0;
        for (TaskDto.StepView s : steps) {
            if ("ACTIVE".equals(s.status()) && currentIndex == 0) {
                currentIndex = s.index();
            }
        }
        if (currentIndex == 0 && !"RUNNING".equals(wi.getStatus())) {
            currentIndex = steps.size();
        }
        return new TaskDto.InstanceOverview(wi.getId(), safeProcessName(wi.getProcessId()),
                titleAndSearch(wi)[0], wi.getStatus(), steps.size(), currentIndex, wi.getProcessVersion(), steps);
    }

    /** Dựng danh sách bước (đủ, theo thứ tự) của một phiên chạy: trạng thái + người + thời gian + dữ liệu. */
    private List<TaskDto.StepView> buildStepViews(WorkflowInstance wi) {
        String flowInst = wi.getFlowableInstanceId();
        String pdId = null;
        var hpi = historyService.createHistoricProcessInstanceQuery().processInstanceId(flowInst).singleResult();
        if (hpi != null) {
            pdId = hpi.getProcessDefinitionId();
        }
        java.util.LinkedHashMap<String, String> ordered = orderedUserTasks(pdId);

        Map<String, HistoricActivityInstance> actByKey = new HashMap<>();
        for (HistoricActivityInstance a : historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(flowInst).activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc().list()) {
            actByKey.put(a.getActivityId(), a);
        }
        java.util.Set<String> activeKeys = new java.util.HashSet<>();
        for (Task t : taskService.createTaskQuery().processInstanceId(flowInst).list()) {
            activeKeys.add(t.getTaskDefinitionKey());
        }
        Map<String, Object> vars = new java.util.LinkedHashMap<>();
        historyService.createHistoricVariableInstanceQuery().processInstanceId(flowInst)
                .list().forEach(v -> vars.put(v.getVariableName(), v.getValue()));

        JsonNode formsSnapshot = parseFormsSnapshot(wi.getFormsJson());
        List<TaskDto.StepView> steps = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            index++;
            String key = e.getKey();
            HistoricActivityInstance a = actByKey.get(key);
            boolean done = a != null && a.getEndTime() != null;
            boolean active = activeKeys.contains(key) || (a != null && a.getEndTime() == null);
            String status = done ? "DONE" : (active ? "ACTIVE" : "PENDING");
            String assignee = a != null ? userDisplay(a.getAssignee()) : null;
            String startedAt = a != null && a.getStartTime() != null ? a.getStartTime().toInstant().toString() : null;
            String endedAt = done ? a.getEndTime().toInstant().toString() : null;
            steps.add(new TaskDto.StepView(index, key, e.getValue(), assignee, startedAt, endedAt, status,
                    stepFieldValues(wi.getStepsMetaJson(), formsSnapshot, key, vars)));
        }
        return steps;
    }

    /** Dữ liệu đã nhập ở một bước: các trường của biểu mẫu bước đó có giá trị (Nhãn → Giá trị). */
    private List<TaskDto.FieldValue> stepFieldValues(String stepsMetaJson, JsonNode formsSnapshot,
                                                     String stepKey, Map<String, Object> vars) {
        List<TaskDto.FieldValue> out = new ArrayList<>();
        JsonNode step = stepMeta(stepsMetaJson, stepKey);
        String formId = step != null ? blankToNull(step.path("formId").asText(null)) : null;
        if (formId == null) {
            return out;
        }
        JsonNode fields = formFieldsNode(formId, formsSnapshot);
        if (!fields.isArray()) {
            return out;
        }
        for (JsonNode f : fields) {
            String key = f.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }
            String label = f.path("label").asText(key);
            String type = f.path("type").asText("");
            Object v = vars.get(key);
            switch (type) {
                case "scoretable" -> appendScoreTable(out, label, f.path("criteria"), v);
                case "table" -> appendTable(out, label, f.path("columns"), v);
                case "file" -> {
                    String names = fileNames(v);
                    if (names != null) {
                        out.add(new TaskDto.FieldValue(label, names));
                    }
                }
                case "orgtree" -> {
                    String txt = resolveOrgValue(f.path("pickMode").asText("user"), v);
                    if (txt != null) {
                        out.add(new TaskDto.FieldValue(label, txt));
                    }
                }
                case "unitstaff" -> {
                    String txt = resolveOrgValue("user", v);
                    if (txt != null) {
                        out.add(new TaskDto.FieldValue(label, txt));
                    }
                }
                default -> {
                    if (v != null && !v.toString().isBlank()) {
                        out.add(new TaskDto.FieldValue(label, v.toString()));
                    }
                }
            }
        }
        return out;
    }

    /** Giá trị orgtree (id) → tên đọc được theo loại chọn (người/chức danh/đơn vị). */
    private String resolveOrgValue(String pickMode, Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        String id = value.toString();
        return switch (pickMode) {
            case "unit" -> orgUnitRepo.findById(id).map(com.bpm.domain.org.OrgUnit::getName).orElse(id);
            case "position" -> positionRepo.findById(id).map(com.bpm.domain.position.Position::getTitle).orElse(id);
            default -> {
                String u = userDisplay(id);
                yield "(chưa gán)".equals(u) ? id : u;
            }
        };
    }

    /** Tên các tệp đính kèm (JSON [{id,name,...}]) → chuỗi "a.pdf, b.docx"; null nếu rỗng. */
    private String fileNames(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            JsonNode arr = objectMapper.readTree(value.toString());
            if (!arr.isArray() || arr.size() == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode f : arr) {
                String n = f.path("name").asText("");
                if (!n.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(n);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Bung bảng nhiều dòng thành text đọc được (mỗi dòng: "cột: giá trị · …"). */
    private void appendTable(List<TaskDto.FieldValue> out, String label, JsonNode columns, Object value) {
        if (value == null) {
            return;
        }
        JsonNode rows;
        try {
            rows = objectMapper.readTree(value.toString());
        } catch (Exception e) {
            return;
        }
        if (!rows.isArray() || rows.size() == 0) {
            return;
        }
        int i = 0;
        for (JsonNode row : rows) {
            i++;
            StringBuilder sb = new StringBuilder();
            if (columns.isArray()) {
                for (JsonNode c : columns) {
                    String ck = c.path("key").asText(null);
                    if (ck == null) {
                        continue;
                    }
                    String cl = c.path("label").asText(ck);
                    String cv = row.path(ck).asText("");
                    if (!cv.isBlank()) {
                        // Cột chọn cây → hiển thị TÊN; cột Có/Không → nhãn dễ đọc.
                        String ctype = c.path("type").asText("");
                        String disp = switch (ctype) {
                            case "orgtree" -> resolveOrgValue(c.path("pickMode").asText("user"), cv);
                            case "unitstaff" -> resolveOrgValue("user", cv);
                            case "boolean" -> "true".equals(cv) ? "Có" : "Không";
                            default -> cv;
                        };
                        if (disp == null || disp.isBlank()) {
                            disp = cv;
                        }
                        if (sb.length() > 0) {
                            sb.append(" · ");
                        }
                        sb.append(cl).append(": ").append(disp);
                    }
                }
            }
            if (sb.length() > 0) {
                out.add(new TaskDto.FieldValue(label + " #" + i, sb.toString()));
            }
        }
    }

    /** Bung 1 bảng chấm điểm thành các dòng đọc được: mỗi tiêu chí (điểm → quy đổi) + điểm trung bình. */
    private void appendScoreTable(List<TaskDto.FieldValue> out, String label, JsonNode criteria, Object value) {
        if (value == null || !criteria.isArray() || criteria.size() == 0) {
            return;
        }
        JsonNode scores;
        try {
            scores = objectMapper.readTree(value.toString());
        } catch (Exception e) {
            return;
        }
        double total = 0;
        boolean any = false;
        for (JsonNode c : criteria) {
            String ck = c.path("key").asText(null);
            if (ck == null) {
                continue;
            }
            String cl = c.path("label").asText(ck);
            int w = c.path("weight").asInt(0);
            JsonNode s = scores.get(ck);
            if (s != null && s.isNumber()) {
                double conv = s.asDouble() * w / 100.0;
                total += conv;
                any = true;
                out.add(new TaskDto.FieldValue(label + " · " + cl + " (" + w + "%)",
                        fmtNum(s.asDouble()) + " → " + String.format(java.util.Locale.US, "%.2f", conv)));
            }
        }
        if (any) {
            out.add(new TaskDto.FieldValue(label + " · Điểm trung bình", String.format(java.util.Locale.US, "%.2f", total)));
        }
    }

    private static String fmtNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
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

    /** Xoá HẲN một hồ sơ (mọi trạng thái): huỷ Flowable runtime nếu đang chạy + xoá history + xoá bản ghi. Chỉ admin. */
    @Transactional
    public void deleteInstance(String instanceId, String actor) {
        WorkflowInstance wi = get(instanceId);
        // Huỷ snapshot phân công của các việc đang mở trước khi xoá.
        for (Task t : taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list()) {
            assignmentRepo.findByTaskId(t.getId()).ifPresent(ta -> {
                if (ta.getStatus() != com.bpm.domain.assignment.AssignmentStatus.CANCELLED) {
                    ta.cancel();
                    assignmentRepo.save(ta);
                }
            });
        }
        try {
            if ("RUNNING".equals(wi.getStatus())) {
                runtimeService.deleteProcessInstance(wi.getFlowableInstanceId(), "Xoá bởi " + actor);
            }
        } catch (Exception ignore) { /* có thể đã kết thúc */ }
        try {
            historyService.deleteHistoricProcessInstance(wi.getFlowableInstanceId());
        } catch (Exception ignore) { /* */ }
        instanceRepo.delete(wi);
        auditPort.record("INSTANCE_DELETED", "WorkflowInstance", wi.getId(), actor,
                "status=" + wi.getStatus() + ", flowableInstance=" + wi.getFlowableInstanceId());
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
