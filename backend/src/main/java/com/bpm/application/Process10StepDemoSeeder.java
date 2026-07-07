package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.workflow.WorkflowInstance;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProcessDefinitionRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seed dữ liệu DEMO cho MỘT QUY TRÌNH 10 BƯỚC chạy thật qua BPM (Flowable) trên dữ liệu THẬT.
 * Quy trình mẫu: "Mua sắm – Thanh toán" (Purchase-to-Pay) gồm 10 bước tuần tự, gán tới NHÂN SỰ THẬT.
 * Nhiều hồ sơ được rải ĐỦ 10 bước (mỗi bước có ít nhất 1 hồ sơ đang dừng) + vài hồ sơ hoàn thành + quá hạn.
 *
 * <p>Kích hoạt: ADMIN bấm nút (POST /api/v1/system/seed-process-demo) — HOẶC tự chạy khi khởi động nếu
 * đặt cờ {@code bpm.seed.process10.onboot=true} (chỉ dùng khi seed thủ công qua BE local, mặc định TẮT).
 *
 * <p>Nguyên tắc (giống {@link HrDemoSeeder}): CHỈ THÊM, idempotent theo key, gán USER thật, mô phỏng
 * quá hạn bằng cách lùi đồng hồ Flowable.
 */
@Service
public class Process10StepDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(Process10StepDemoSeeder.class);

    /** Khoá nhận diện đã-seed (idempotent). */
    private static final String GUARD_KEY = "mua-sam-p2p-10b";
    private static final String FORM_KEY = "de-nghi-mua-sam-p2p";

    /** Tên 10 bước (tuần tự). */
    private static final String[] STEP_NAMES = {
            "Lập đề nghị mua sắm",
            "Trưởng bộ phận duyệt nhu cầu",
            "Khảo giá & lấy báo giá",
            "So sánh & chọn nhà cung cấp",
            "Giám đốc phê duyệt ngân sách",
            "Lập & ký hợp đồng/PO",
            "Nhận hàng & kiểm tra chất lượng",
            "Nghiệm thu",
            "Kế toán thanh toán",
            "Lưu hồ sơ & đóng hồ sơ"
    };
    /** Hành động CHÍNH của từng bước (dùng khi hoàn thành để tiến bước). */
    private static final String[] STEP_ACTION = {
            "SUBMIT", "APPROVE", "DONE", "DONE", "APPROVE", "DONE", "DONE", "ACCEPT", "PAID", "CLOSE"
    };
    /** Tập hành động (nút) cấu hình cho từng bước. */
    private static final String[][] STEP_ACTIONS = {
            {"SUBMIT"}, {"APPROVE", "REJECT"}, {"DONE"}, {"DONE"}, {"APPROVE", "REJECT"},
            {"DONE"}, {"DONE"}, {"ACCEPT", "REJECT"}, {"PAID"}, {"CLOSE"}
    };
    /** SLA (giờ) từng bước. */
    private static final int[] STEP_SLA = {4, 16, 24, 16, 24, 24, 48, 16, 24, 8};

    /** Các trường của biểu mẫu đề nghị mua sắm. */
    private static final String[] FIELD_KEYS = {
            "tieu_de", "hang_muc", "so_luong", "don_gia", "thanh_tien", "ncc_de_xuat", "ly_do", "can_truoc_ngay"
    };

    private final ProcessService processService;
    private final FormService formService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final ProcessEngine processEngine;
    private final ProcessDefinitionRepository processRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort auditPort;

    @Value("${bpm.seed.process10.onboot:false}")
    private boolean seedOnBoot;

    public Process10StepDemoSeeder(ProcessService processService, FormService formService,
                                   WorkflowService workflowService, TaskService taskService,
                                   ProcessEngine processEngine, ProcessDefinitionRepository processRepo,
                                   EmployeeRepository employeeRepo, UserAccountRepository userRepo,
                                   AuditPort auditPort) {
        this.processService = processService;
        this.formService = formService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.processEngine = processEngine;
        this.processRepo = processRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.auditPort = auditPort;
    }

    /** Tự seed khi khởi động nếu bật cờ (dùng seed qua BE local, không cần đăng nhập admin). */
    @EventListener(ApplicationReadyEvent.class)
    void maybeSeedOnBoot() {
        if (!seedOnBoot) {
            return;
        }
        try {
            SeedResult r = seed("system");
            log.info("[Process10StepDemoSeeder] onboot: {}", r.message());
        } catch (Exception e) {
            log.warn("[Process10StepDemoSeeder] onboot seed lỗi: {}", e.toString());
        }
    }

    /** Tóm tắt kết quả seed (trả về cho FE). */
    public record SeedResult(boolean seeded, int steps, int instances, String message) {
    }

    /**
     * Tạo 1 quy trình 10 bước + biểu mẫu + nhiều hồ sơ dừng ở đủ 10 bước (+ hoàn thành + quá hạn). Idempotent.
     */
    public SeedResult seed(String actor) {
        if (processRepo.existsByProcessKey(GUARD_KEY)) {
            log.info("[Process10StepDemoSeeder] Đã có quy trình 10 bước demo (key={}), bỏ qua.", GUARD_KEY);
            auditPort.record("PROCESS10_DEMO_SEED_SKIPPED", "System", null, actor, "Đã tồn tại — không seed lại.");
            return new SeedResult(false, 0, 0,
                    "Đã có dữ liệu quy trình 10 bước demo từ trước — bỏ qua (không nhân đôi).");
        }

        // Nhân sự thật đang làm việc + có tài khoản (để gán người thực hiện + là người nộp hồ sơ).
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        if (active.isEmpty()) {
            return new SeedResult(false, 0, 0,
                    "Chưa có nhân sự (đang làm việc + có tài khoản) để gán người thực hiện. Hãy import nhân sự trước.");
        }
        Picker people = new Picker(active);

        // ===================== BIỂU MẪU =====================
        String formId = form(FORM_KEY, "Đơn đề nghị mua sắm", """
                {"fields": [
                  {"key": "tieu_de", "label": "Tiêu đề đề nghị", "type": "text", "required": true},
                  {"key": "hang_muc", "label": "Hạng mục / hàng hoá", "type": "text", "required": true},
                  {"key": "so_luong", "label": "Số lượng", "type": "number", "required": true, "validation": {"min": 1}},
                  {"key": "don_gia", "label": "Đơn giá dự kiến (VNĐ)", "type": "number"},
                  {"key": "thanh_tien", "label": "Thành tiền dự kiến (VNĐ)", "type": "number"},
                  {"key": "ncc_de_xuat", "label": "Nhà cung cấp đề xuất", "type": "text"},
                  {"key": "ly_do", "label": "Lý do / mục đích mua sắm", "type": "richtext", "required": true},
                  {"key": "can_truoc_ngay", "label": "Cần trước ngày", "type": "datetime"}
                ]}""");

        // ===================== QUY TRÌNH 10 BƯỚC =====================
        String[] taskIds = new String[STEP_NAMES.length];
        String[] userIds = new String[STEP_NAMES.length];
        for (int i = 0; i < STEP_NAMES.length; i++) {
            taskIds[i] = "Task_" + String.format("%02d", i + 1);
            userIds[i] = pick(people); // mỗi bước một người thật (rải qua nhiều người)
        }
        String bpmn = bpmnLinear("muasamp2p", "Quy trình Mua sắm – Thanh toán", taskIds, STEP_NAMES);
        String meta = buildStepsMeta(taskIds, userIds, formId);
        ProcessDefinition p = publish(GUARD_KEY, "Quy trình Mua sắm – Thanh toán (10 bước)", bpmn, meta);

        // ===================== HỒ SƠ ĐANG CHẠY (rải đủ 10 bước) =====================
        AtomicInteger created = new AtomicInteger(0);
        try {
            seedRunningInstances(p, people, created);
        } catch (Exception e) {
            log.warn("[Process10StepDemoSeeder] Seed hồ sơ chạy gặp lỗi (bỏ qua, cấu hình quy trình vẫn đủ): {}",
                    e.toString());
        }

        int instances = created.get();
        auditPort.record("PROCESS10_DEMO_SEEDED", "System", null, actor,
                "process=" + GUARD_KEY + ", steps=10, instances=" + instances);
        log.info("[Process10StepDemoSeeder] Seed quy trình 10 bước: 1 quy trình, {} hồ sơ.", instances);
        return new SeedResult(true, 10, instances,
                "Đã tạo 1 quy trình 10 bước (Mua sắm – Thanh toán) và " + instances
                        + " hồ sơ demo rải đủ các bước (gồm hoàn thành & quá hạn).");
    }

    /**
     * Danh mục hàng hoá + độ sâu (số bước đã hoàn thành) để rải hồ sơ dừng ở đủ 10 bước.
     * depth = số việc đã complete; hồ sơ đang dừng ở bước (depth+1). depth=10 → hoàn thành.
     */
    private void seedRunningInstances(ProcessDefinition p, Picker people, AtomicInteger created) {
        Object[][] items = {
                // {tiêu đề, hạng mục, số lượng, đơn giá, depth, backdatedHoursOrZero}
                {"Mua 15 laptop cho phòng Kỹ thuật", "Laptop Dell Latitude 5540", 15, 22_000_000L, 0, 0},
                {"Mua bàn ghế văn phòng tầng 5", "Bàn ghế công thái học", 30, 3_500_000L, 1, 0},
                {"Trang bị điện thoại cho đội Kinh doanh", "iPhone 15 cấp cho Sale", 8, 24_000_000L, 1, 0},
                {"Thuê dịch vụ kiểm thử bảo mật hệ thống", "Pentest hệ thống BPM", 1, 80_000_000L, 2, 0},
                {"Mua license phần mềm thiết kế", "Adobe Creative Cloud (năm)", 10, 15_000_000L, 3, 0},
                {"Nâng cấp máy chủ ảo hoá trung tâm dữ liệu", "Server Dell PowerEdge R760", 2, 250_000_000L, 4, 0},
                {"Mua vật tư tiêu hao văn phòng Quý III", "Giấy, mực in, văn phòng phẩm", 1, 12_000_000L, 5, 0},
                {"Mua thiết bị mạng cho chi nhánh Hà Nội", "Switch + Access Point Cisco", 12, 9_000_000L, 6, 0},
                {"Mua máy chiếu 4K cho phòng họp lớn", "Máy chiếu Epson 4K", 3, 18_000_000L, 7, 0},
                {"Mua UPS cho phòng máy chủ", "UPS 10kVA", 2, 55_000_000L, 8, 0},
                {"Gia hạn tên miền & chứng chỉ SSL", "Domain + SSL wildcard", 1, 6_000_000L, 9, 0},
                {"Đặt in ấn brochure marketing 2026", "Brochure + catalogue", 5000, 8_000L, 10, 0},
                {"Mua ghế công thái học cho Ban giám đốc", "Ghế Herman Miller", 5, 28_000_000L, 10, 0},
                {"Thuê hội trường tổ chức sự kiện năm", "Hội trường 300 khách", 1, 45_000_000L, 2, 40},
                {"Thuê xe đưa đón team building", "Xe 45 chỗ x3 ngày", 3, 15_000_000L, 4, 60},
        };
        for (Object[] it : items) {
            String tieuDe = (String) it[0];
            String hangMuc = (String) it[1];
            int soLuong = (int) it[2];
            long donGia = (long) it[3];
            int depth = (int) it[4];
            int backdated = (int) it[5];
            Map<String, Object> data = mua(tieuDe, hangMuc, soLuong, donGia);
            String submitter = username(people.next());
            if (backdated > 0) {
                runBackdated(p, backdated, data, submitter, depth, created);
            } else {
                run(p, data, submitter, depth, created);
            }
        }
    }

    // ===================== Khởi tạo & tiến bước =====================

    /** Khởi tạo hồ sơ rồi hoàn thành {@code completes} bước đầu (dùng hành động chính của mỗi bước). */
    private WorkflowInstance run(ProcessDefinition p, Map<String, Object> initial, String submitter,
                                 int completes, AtomicInteger created) {
        WorkflowInstance wi = workflowService.start(p.getId(), initial, submitter);
        created.incrementAndGet();
        for (int k = 0; k < completes; k++) {
            completeActive(wi, STEP_ACTION[Math.min(k, STEP_ACTION.length - 1)]);
        }
        return wi;
    }

    /** Như {@link #run} nhưng lùi đồng hồ engine để việc được tạo "trong quá khứ" (mô phỏng quá hạn). */
    private WorkflowInstance runBackdated(ProcessDefinition p, int hoursAgo, Map<String, Object> initial,
                                          String submitter, int completes, AtomicInteger created) {
        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(hoursAgo))));
        try {
            return run(p, initial, submitter, completes, created);
        } finally {
            clock.reset();
        }
    }

    private void completeActive(WorkflowInstance wi, String action) {
        List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
        if (!ts.isEmpty()) {
            workflowService.complete(ts.get(0).getId(), action, Map.of(), "system");
        }
    }

    // ===================== Dữ liệu form =====================

    private static Map<String, Object> mua(String tieuDe, String hangMuc, int soLuong, long donGia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tieu_de", tieuDe);
        m.put("hang_muc", hangMuc);
        m.put("so_luong", soLuong);
        m.put("don_gia", donGia);
        m.put("thanh_tien", donGia * soLuong);
        m.put("ncc_de_xuat", "Nhà cung cấp dự kiến");
        m.put("ly_do", "Phục vụ nhu cầu vận hành/đầu tư của đơn vị theo kế hoạch năm 2026.");
        m.put("can_truoc_ngay", LocalDate.now().plusDays(21) + "T17:00");
        return m;
    }

    // ===================== Helpers cấu hình =====================

    private String form(String key, String name, String schema) {
        var f = formService.create(key, name, "system");
        formService.saveSchema(f.getId(), schema, "system");
        return f.getId();
    }

    private ProcessDefinition publish(String key, String name, String bpmn, String meta) {
        ProcessDefinition p = processService.create(key, name, "system");
        processService.saveDesign(p.getId(), bpmn, meta, "system");
        processService.publish(p.getId(), "system");
        return p;
    }

    /** Vòng tròn chọn người trong nhóm (rải hồ sơ/bước qua nhiều người thật). */
    private static final class Picker {
        private final List<Employee> list;
        private int i = 0;
        Picker(List<Employee> list) { this.list = list; }
        Employee next() { return list.get((i++) % list.size()); }
    }

    private String pick(Picker p) { return p.next().getUserAccountId(); }

    private String username(Employee e) {
        return userRepo.findById(e.getUserAccountId()).map(UserAccount::getUsername).orElse("system");
    }

    /** stepsMeta JSON: mỗi bước gán USER thật + SLA + hành động + biểu mẫu; bước 1 EDIT, các bước sau READONLY. */
    private String buildStepsMeta(String[] taskIds, String[] userIds, String formId) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < taskIds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            boolean editable = (i == 0);
            sb.append('"').append(taskIds[i]).append("\":{")
                    .append("\"assigneeType\":\"USER\",")
                    .append("\"assigneeId\":\"").append(userIds[i]).append("\",")
                    .append("\"slaHours\":").append(STEP_SLA[i]).append(',')
                    .append("\"formId\":\"").append(formId).append("\",")
                    .append("\"actions\":").append(jsonArray(STEP_ACTIONS[i])).append(',')
                    .append("\"fieldPerms\":").append(fieldPerms(editable))
                    .append('}');
        }
        return sb.append('}').toString();
    }

    private static String jsonArray(String[] items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(items[i]).append('"');
        }
        return sb.append(']').toString();
    }

    private static String fieldPerms(boolean editable) {
        String v = editable ? "EDIT" : "READONLY";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < FIELD_KEYS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(FIELD_KEYS[i]).append("\":\"").append(v).append('"');
        }
        return sb.append('}').toString();
    }

    // ===================== BPMN (sinh tuyến tính N bước, kèm DI) =====================

    /** Sơ đồ tuyến tính: Bắt đầu → task[0] → … → task[N-1] → Kết thúc, kèm DI để hiển thị. */
    private static String bpmnLinear(String pid, String procName, String[] taskIds, String[] taskNames) {
        int n = taskIds.length;
        StringBuilder proc = new StringBuilder();
        StringBuilder di = new StringBuilder();

        proc.append("<bpmn:startEvent id=\"StartEvent_1\" name=\"Bắt đầu\"><bpmn:outgoing>Flow_0</bpmn:outgoing></bpmn:startEvent>");
        for (int i = 0; i < n; i++) {
            proc.append("<bpmn:userTask id=\"").append(taskIds[i]).append("\" name=\"").append(xml(taskNames[i]))
                    .append("\"><bpmn:incoming>Flow_").append(i).append("</bpmn:incoming>")
                    .append("<bpmn:outgoing>Flow_").append(i + 1).append("</bpmn:outgoing></bpmn:userTask>");
        }
        proc.append("<bpmn:endEvent id=\"EndEvent_1\" name=\"Hoàn thành\"><bpmn:incoming>Flow_").append(n)
                .append("</bpmn:incoming></bpmn:endEvent>");
        // sequenceFlows: Flow_0 (start→t0), Flow_i (t_{i-1}→t_i), Flow_n (t_{n-1}→end)
        proc.append("<bpmn:sequenceFlow id=\"Flow_0\" sourceRef=\"StartEvent_1\" targetRef=\"").append(taskIds[0]).append("\" />");
        for (int i = 1; i < n; i++) {
            proc.append("<bpmn:sequenceFlow id=\"Flow_").append(i).append("\" sourceRef=\"").append(taskIds[i - 1])
                    .append("\" targetRef=\"").append(taskIds[i]).append("\" />");
        }
        proc.append("<bpmn:sequenceFlow id=\"Flow_").append(n).append("\" sourceRef=\"").append(taskIds[n - 1])
                .append("\" targetRef=\"EndEvent_1\" />");

        // ---- DI ----
        int y = 200, taskW = 120, taskH = 80, gap = 40, startX = 152;
        int firstTaskX = startX + 60; // 212
        di.append("<bpmndi:BPMNShape id=\"StartEvent_1_di\" bpmnElement=\"StartEvent_1\"><dc:Bounds x=\"")
                .append(startX).append("\" y=\"").append(y - 18).append("\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>");
        int[] taskX = new int[n];
        for (int i = 0; i < n; i++) {
            taskX[i] = firstTaskX + i * (taskW + gap);
            di.append("<bpmndi:BPMNShape id=\"").append(taskIds[i]).append("_di\" bpmnElement=\"").append(taskIds[i])
                    .append("\"><dc:Bounds x=\"").append(taskX[i]).append("\" y=\"").append(y - taskH / 2)
                    .append("\" width=\"").append(taskW).append("\" height=\"").append(taskH).append("\" /></bpmndi:BPMNShape>");
        }
        int endX = taskX[n - 1] + taskW + gap;
        di.append("<bpmndi:BPMNShape id=\"EndEvent_1_di\" bpmnElement=\"EndEvent_1\"><dc:Bounds x=\"")
                .append(endX).append("\" y=\"").append(y - 18).append("\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>");
        // edges
        di.append(edge("Flow_0", startX + 36, y, taskX[0], y));
        for (int i = 1; i < n; i++) {
            di.append(edge("Flow_" + i, taskX[i - 1] + taskW, y, taskX[i], y));
        }
        di.append(edge("Flow_" + n, taskX[n - 1] + taskW, y, endX, y));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + " id=\"Definitions_" + pid + "\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "<bpmn:process id=\"Process_" + pid + "\" name=\"" + xml(procName) + "\" isExecutable=\"true\">"
                + proc + "</bpmn:process>"
                + "<bpmndi:BPMNDiagram id=\"BPMNDiagram_1\"><bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_"
                + pid + "\">" + di + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram>"
                + "</bpmn:definitions>";
    }

    private static String edge(String id, int x1, int y1, int x2, int y2) {
        return "<bpmndi:BPMNEdge id=\"" + id + "_di\" bpmnElement=\"" + id + "\"><di:waypoint x=\"" + x1
                + "\" y=\"" + y1 + "\" /><di:waypoint x=\"" + x2 + "\" y=\"" + y2 + "\" /></bpmndi:BPMNEdge>";
    }

    /** Thoát ký tự XML cho attribute/text. */
    private static String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
