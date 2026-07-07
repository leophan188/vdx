package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.workflow.WorkflowInstance;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProcessDefinitionRepository;
import com.bpm.infrastructure.UserAccountRepository;
import com.bpm.infrastructure.WorkflowInstanceRepository;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seed dữ liệu DEMO quy trình "Đánh giá năng lực nhân sự" — minh hoạ TRƯỜNG BẢNG CHẤM ĐIỂM (scoretable):
 * nhiều tiêu chí + trọng số → tự tính điểm trung bình có trọng số. Mỗi bước có bộ tiêu chí RIÊNG
 * (Nhân viên tự đánh giá → Quản lý trực tiếp đánh giá → HR đánh giá & xác nhận).
 *
 * <p>Kích hoạt: POST /api/v1/system/seed-eval-demo (ADMIN) hoặc cờ khởi động bpm.seed.eval.onboot=true
 * (kèm bpm.seed.eval.reset=true để tạo lại). Cấu hình là dữ liệu THẬT lưu DB.
 */
@Service
public class HrEvalDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(HrEvalDemoSeeder.class);

    private static final String GUARD_KEY = "danh-gia-nang-luc";
    private static final String FORM_PREFIX = "dgnl-buoc-";

    private static final String[] STEP_NAMES = {
            "Nhân viên tự đánh giá", "Quản lý trực tiếp đánh giá", "HR đánh giá & xác nhận"
    };
    private static final String[] STEP_ACTION = {"SUBMIT", "APPROVE", "APPROVE"};
    private static final String[][] STEP_ACTIONS = {
            {"SUBMIT"}, {"APPROVE", "RETURN"}, {"APPROVE", "RETURN"}
    };
    private static final int[] STEP_SLA = {24, 24, 16};

    /** Biểu mẫu từng bước (JSON schema) — mỗi bước có 1 bảng chấm điểm với bộ tiêu chí riêng. */
    private static final String[] STEP_FORMS = {
            // Bước 1: Nhân viên tự đánh giá
            """
            {"fields":[
              {"key":"nhan_vien","label":"Nhân viên","type":"text","required":true},
              {"key":"ky_danh_gia","label":"Kỳ đánh giá","type":"dropdown","optionSource":"STATIC","options":"Quý I, Quý II, Quý III, Quý IV, Cả năm","required":true},
              {"key":"tu_cham_diem","label":"Nhân viên tự chấm điểm","type":"scoretable","scoreMax":10,"criteria":[
                {"key":"c1","label":"Tuân thủ quy định & quy trình công ty","weight":25},
                {"key":"c2","label":"Chuyên cần & chấp hành nội quy","weight":25},
                {"key":"c3","label":"Phù hợp & lan tỏa văn hóa","weight":20},
                {"key":"c4","label":"Mức độ hoàn thành công việc","weight":30}]},
              {"key":"diem_manh","label":"Điểm mạnh","type":"textarea"},
              {"key":"can_cai_thien","label":"Cần cải thiện","type":"textarea"}
            ]}""",
            // Bước 2: Quản lý trực tiếp đánh giá (bộ tiêu chí KHÁC)
            """
            {"fields":[
              {"key":"ql_cham_diem","label":"Quản lý chấm điểm","type":"scoretable","scoreMax":10,"criteria":[
                {"key":"m1","label":"Năng lực chuyên môn","weight":30},
                {"key":"m2","label":"Kỹ năng giải quyết vấn đề","weight":25},
                {"key":"m3","label":"Tinh thần trách nhiệm & chủ động","weight":20},
                {"key":"m4","label":"Làm việc nhóm & giao tiếp","weight":25}]},
              {"key":"nhan_xet_ql","label":"Nhận xét của quản lý","type":"textarea"},
              {"key":"ket_qua","label":"Kết quả đánh giá","type":"dropdown","optionSource":"STATIC","options":"PASS, FAIL, Xuất sắc","required":true}
            ]}""",
            // Bước 3: HR đánh giá & xác nhận
            """
            {"fields":[
              {"key":"hr_cham_diem","label":"HR chấm điểm","type":"scoretable","scoreMax":10,"criteria":[
                {"key":"h1","label":"Tuân thủ quy định & quy trình công ty","weight":25},
                {"key":"h2","label":"Chuyên cần & chấp hành nội quy","weight":25},
                {"key":"h3","label":"Phù hợp & lan tỏa văn hóa","weight":20},
                {"key":"h4","label":"Mức độ hoàn thành công việc","weight":30}]},
              {"key":"ket_luan","label":"Kết luận của HR","type":"textarea"}
            ]}"""
    };

    private final ProcessService processService;
    private final FormService formService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessDefinitionRepository processRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort auditPort;

    @Value("${bpm.seed.eval.onboot:false}")
    private boolean seedOnBoot;
    @Value("${bpm.seed.eval.reset:false}")
    private boolean resetFirst;

    public HrEvalDemoSeeder(ProcessService processService, FormService formService, WorkflowService workflowService,
                            TaskService taskService, RuntimeService runtimeService, HistoryService historyService,
                            ProcessDefinitionRepository processRepo, WorkflowInstanceRepository instanceRepo,
                            EmployeeRepository employeeRepo, UserAccountRepository userRepo, AuditPort auditPort) {
        this.processService = processService;
        this.formService = formService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.processRepo = processRepo;
        this.instanceRepo = instanceRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.auditPort = auditPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    void maybeSeedOnBoot() {
        if (!seedOnBoot) {
            return;
        }
        try {
            if (resetFirst) {
                reset("system");
            }
            SeedResult r = seed("system");
            log.info("[HrEvalDemoSeeder] onboot: {}", r.message());
        } catch (Exception e) {
            log.warn("[HrEvalDemoSeeder] onboot seed lỗi: {}", e.toString());
        }
    }

    public record SeedResult(boolean seeded, int steps, int instances, String message) {
    }

    public void reset(String actor) {
        processRepo.findAll().stream().filter(p -> GUARD_KEY.equals(p.getProcessKey())).findFirst().ifPresent(p -> {
            for (WorkflowInstance wi : instanceRepo.findByProcessId(p.getId())) {
                try {
                    if ("RUNNING".equals(wi.getStatus())) {
                        runtimeService.deleteProcessInstance(wi.getFlowableInstanceId(), "reset demo");
                    }
                } catch (Exception ignore) { /* */ }
                try {
                    historyService.deleteHistoricProcessInstance(wi.getFlowableInstanceId());
                } catch (Exception ignore) { /* */ }
                instanceRepo.delete(wi);
            }
            processService.delete(p.getId(), actor);
            log.info("[HrEvalDemoSeeder] Đã xoá demo cũ (key={}).", GUARD_KEY);
        });
        formService.list().stream()
                .filter(f -> f.getFormKey() != null && f.getFormKey().startsWith(FORM_PREFIX))
                .forEach(f -> {
                    try {
                        formService.delete(f.getId(), actor);
                    } catch (Exception ignore) { /* */ }
                });
    }

    public SeedResult seed(String actor) {
        if (processRepo.existsByProcessKey(GUARD_KEY)) {
            return new SeedResult(false, 0, 0, "Đã có quy trình đánh giá năng lực demo — bỏ qua (dùng reset để tạo lại).");
        }
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        if (active.isEmpty()) {
            return new SeedResult(false, 0, 0, "Chưa có nhân sự (đang làm việc + có tài khoản) để gán người thực hiện.");
        }
        Picker people = new Picker(active);

        String[] formIds = new String[STEP_NAMES.length];
        String[] taskIds = new String[STEP_NAMES.length];
        String[] userIds = new String[STEP_NAMES.length];
        for (int i = 0; i < STEP_NAMES.length; i++) {
            var f = formService.create(FORM_PREFIX + String.format("%02d", i + 1),
                    "Đánh giá năng lực — " + STEP_NAMES[i], "system");
            formService.saveSchema(f.getId(), STEP_FORMS[i], "system");
            formIds[i] = f.getId();
            taskIds[i] = "Task_" + String.format("%02d", i + 1);
            userIds[i] = people.next().getUserAccountId();
        }
        String bpmn = bpmnLinear("dgnl", "Quy trình Đánh giá năng lực nhân sự", taskIds, STEP_NAMES);
        String meta = buildStepsMeta(taskIds, userIds, formIds);
        ProcessDefinition p = processService.create(GUARD_KEY, "Quy trình Đánh giá năng lực nhân sự", "system");
        processService.saveDesign(p.getId(), bpmn, meta, "system");
        processService.publish(p.getId(), "system");

        AtomicInteger created = new AtomicInteger(0);
        try {
            seedInstances(p, people, created);
        } catch (Exception e) {
            log.warn("[HrEvalDemoSeeder] seed hồ sơ lỗi: {}", e.toString());
        }
        int n = created.get();
        auditPort.record("EVAL_DEMO_SEEDED", "System", null, actor, "process=" + GUARD_KEY + ", instances=" + n);
        log.info("[HrEvalDemoSeeder] Seed quy trình đánh giá năng lực: 1 quy trình, {} hồ sơ.", n);
        return new SeedResult(true, 3, n,
                "Đã tạo quy trình 'Đánh giá năng lực nhân sự' (3 bước, mỗi bước có BẢNG CHẤM ĐIỂM tiêu chí + trọng số) và "
                        + n + " hồ sơ demo rải các bước.");
    }

    private void seedInstances(ProcessDefinition p, Picker people, AtomicInteger created) {
        String[] tu = {"{\"c1\":9,\"c2\":8,\"c3\":9,\"c4\":10}", "{\"c1\":8,\"c2\":8,\"c3\":7,\"c4\":9}",
                "{\"c1\":10,\"c2\":9,\"c3\":9,\"c4\":8}", "{\"c1\":7,\"c2\":8,\"c3\":8,\"c4\":9}"};
        String[] ql = {"{\"m1\":8,\"m2\":9,\"m3\":8,\"m4\":9}", "{\"m1\":9,\"m2\":8,\"m3\":9,\"m4\":8}",
                "{\"m1\":10,\"m2\":9,\"m3\":9,\"m4\":9}"};
        String hr = "{\"h1\":9,\"h2\":9,\"h3\":8,\"h4\":10}";
        String[] names = {"Trần Diễm Xuân", "Nguyễn Văn An", "Lê Thị Bình", "Phạm Thanh Cường",
                "Hoàng Minh Đức", "Vũ Thị Hà", "Đặng Quốc Huy", "Bùi Thu Trang"};
        String[] ky = {"Quý II", "Cả năm", "Quý I", "Quý III"};

        // depth: số bước đã hoàn thành (0 → đang ở bước 1; 3 → hoàn thành).
        int[] depths = {0, 0, 1, 1, 2, 2, 3, 3};
        for (int i = 0; i < depths.length; i++) {
            Map<String, Object> step1 = new LinkedHashMap<>();
            step1.put("nhan_vien", names[i % names.length]);
            step1.put("ky_danh_gia", ky[i % ky.length]);
            step1.put("tu_cham_diem", tu[i % tu.length]);
            step1.put("diem_manh", "Chủ động, trách nhiệm cao, hoàn thành tốt nhiệm vụ.");
            step1.put("can_cai_thien", "Nâng cao kỹ năng trình bày.");

            String submitter = username(people.next());
            WorkflowInstance wi = workflowService.start(p.getId(), step1, submitter);
            created.incrementAndGet();

            int depth = depths[i];
            if (depth >= 1) {
                completeActive(wi, STEP_ACTION[0], Map.of()); // hoàn thành bước 1 (dữ liệu đã nhập ở start)
            }
            if (depth >= 2) {
                Map<String, Object> step2 = new LinkedHashMap<>();
                step2.put("ql_cham_diem", ql[i % ql.length]);
                step2.put("nhan_xet_ql", "Đáp ứng tốt yêu cầu công việc, phối hợp nhóm hiệu quả.");
                step2.put("ket_qua", i % 3 == 0 ? "Xuất sắc" : "PASS");
                completeActive(wi, STEP_ACTION[1], step2);
            }
            if (depth >= 3) {
                Map<String, Object> step3 = new LinkedHashMap<>();
                step3.put("hr_cham_diem", hr);
                step3.put("ket_luan", "Xác nhận kết quả đánh giá; đề xuất khen thưởng kỳ tới.");
                completeActive(wi, STEP_ACTION[2], step3);
            }
        }
    }

    private void completeActive(WorkflowInstance wi, String action, Map<String, Object> data) {
        List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
        if (!ts.isEmpty()) {
            workflowService.complete(ts.get(0).getId(), action, data, "system");
        }
    }

    private static final class Picker {
        private final List<Employee> list;
        private int i = 0;
        Picker(List<Employee> list) { this.list = list; }
        Employee next() { return list.get((i++) % list.size()); }
    }

    private String username(Employee e) {
        return userRepo.findById(e.getUserAccountId()).map(UserAccount::getUsername).orElse("system");
    }

    private static String buildStepsMeta(String[] taskIds, String[] userIds, String[] formIds) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < taskIds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            StringBuilder acts = new StringBuilder("[");
            for (int j = 0; j < STEP_ACTIONS[i].length; j++) {
                if (j > 0) {
                    acts.append(',');
                }
                acts.append('"').append(STEP_ACTIONS[i][j]).append('"');
            }
            acts.append(']');
            sb.append('"').append(taskIds[i]).append("\":{")
                    .append("\"assigneeType\":\"USER\",\"assigneeId\":\"").append(userIds[i]).append("\",")
                    .append("\"slaHours\":").append(STEP_SLA[i]).append(',')
                    .append("\"formId\":\"").append(formIds[i]).append("\",")
                    .append("\"actions\":").append(acts)
                    .append('}');
        }
        return sb.append('}').toString();
    }

    /** Sơ đồ tuyến tính N bước (Bắt đầu → task... → Kết thúc) kèm DI đơn giản. */
    private static String bpmnLinear(String pid, String procName, String[] taskIds, String[] names) {
        int n = taskIds.length;
        StringBuilder proc = new StringBuilder();
        StringBuilder di = new StringBuilder();
        proc.append("<bpmn:startEvent id=\"StartEvent_1\" name=\"Bắt đầu\"><bpmn:outgoing>Flow_0</bpmn:outgoing></bpmn:startEvent>");
        for (int i = 0; i < n; i++) {
            proc.append("<bpmn:userTask id=\"").append(taskIds[i]).append("\" name=\"").append(xml(names[i]))
                    .append("\"><bpmn:incoming>Flow_").append(i).append("</bpmn:incoming><bpmn:outgoing>Flow_")
                    .append(i + 1).append("</bpmn:outgoing></bpmn:userTask>");
        }
        proc.append("<bpmn:endEvent id=\"EndEvent_1\" name=\"Hoàn thành\"><bpmn:incoming>Flow_").append(n)
                .append("</bpmn:incoming></bpmn:endEvent>");
        proc.append("<bpmn:sequenceFlow id=\"Flow_0\" sourceRef=\"StartEvent_1\" targetRef=\"").append(taskIds[0]).append("\" />");
        for (int i = 1; i < n; i++) {
            proc.append("<bpmn:sequenceFlow id=\"Flow_").append(i).append("\" sourceRef=\"").append(taskIds[i - 1])
                    .append("\" targetRef=\"").append(taskIds[i]).append("\" />");
        }
        proc.append("<bpmn:sequenceFlow id=\"Flow_").append(n).append("\" sourceRef=\"").append(taskIds[n - 1])
                .append("\" targetRef=\"EndEvent_1\" />");

        int y = 200, w = 120, h = 80, gap = 40, sx = 152, fx = sx + 60;
        di.append(shape("StartEvent_1", sx, y - 18, 36, 36));
        int[] tx = new int[n];
        for (int i = 0; i < n; i++) {
            tx[i] = fx + i * (w + gap);
            di.append(shape(taskIds[i], tx[i], y - h / 2, w, h));
        }
        int ex = tx[n - 1] + w + gap;
        di.append(shape("EndEvent_1", ex, y - 18, 36, 36));
        di.append(edge("Flow_0", sx + 36, y, tx[0], y));
        for (int i = 1; i < n; i++) {
            di.append(edge("Flow_" + i, tx[i - 1] + w, y, tx[i], y));
        }
        di.append(edge("Flow_" + n, tx[n - 1] + w, y, ex, y));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + " id=\"Definitions_" + pid + "\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "<bpmn:process id=\"Process_" + pid + "\" name=\"" + xml(procName) + "\" isExecutable=\"true\">"
                + proc + "</bpmn:process>"
                + "<bpmndi:BPMNDiagram id=\"BPMNDiagram_1\"><bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_"
                + pid + "\">" + di + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>";
    }

    private static String shape(String el, int x, int y, int w, int h) {
        return "<bpmndi:BPMNShape id=\"" + el + "_di\" bpmnElement=\"" + el + "\"><dc:Bounds x=\"" + x + "\" y=\"" + y
                + "\" width=\"" + w + "\" height=\"" + h + "\" /></bpmndi:BPMNShape>";
    }

    private static String edge(String id, int x1, int y1, int x2, int y2) {
        return "<bpmndi:BPMNEdge id=\"" + id + "_di\" bpmnElement=\"" + id + "\"><di:waypoint x=\"" + x1 + "\" y=\"" + y1
                + "\" /><di:waypoint x=\"" + x2 + "\" y=\"" + y2 + "\" /></bpmndi:BPMNEdge>";
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
