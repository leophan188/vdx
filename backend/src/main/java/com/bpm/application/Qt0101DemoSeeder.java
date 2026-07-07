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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cấu hình quy trình nghiệp vụ QT01.01 – "Tạo và xử lý nhiệm vụ (Tham gia ý kiến, góp ý)" lên BPM.
 * 8 bước tuần tự theo đặc tả (3 vai trò gốc: Người tạo / Người duyệt / Người phối hợp):
 *   1 Tạo mới nhiệm vụ → 2 Phê duyệt nhiệm vụ → 3a Phân công phối hợp → 3b Tham gia ý kiến
 *   → 4 Xây dựng & tổng hợp dự thảo → 5 Phê duyệt dự thảo → 6 Hoàn thiện văn bản → 7 Ký, ban hành.
 * Mỗi bước có biểu mẫu riêng (các trường theo tài liệu), hành động, SLA; gán người theo vai trò
 * (Mục E — ở đây gán USER thật; bước ký gán "cứng" 1 lãnh đạo). Nhánh phối hợp hiện mô hình tuyến tính
 * (luôn chạy) — có thể chuyển thành rẽ nhánh có điều kiện bằng cổng gateway trong Trình thiết kế.
 *
 * <p>Kích hoạt: POST /api/v1/system/seed-qt0101 (ADMIN) hoặc cờ bpm.seed.qt0101.onboot=true
 * (kèm bpm.seed.qt0101.reset=true để tạo lại).
 */
@Service
public class Qt0101DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(Qt0101DemoSeeder.class);

    private static final String GUARD_KEY = "qt01-01-tham-gia-y-kien";
    private static final String FORM_PREFIX = "qt0101-buoc-";

    private static final String[] STEP_NAMES = {
            "Tạo mới nhiệm vụ",
            "Phê duyệt nhiệm vụ",
            "Phân công phối hợp",
            "Tham gia ý kiến (góp ý)",
            "Xây dựng & tổng hợp dự thảo",
            "Phê duyệt dự thảo",
            "Hoàn thiện thông tin văn bản",
            "Ký, ban hành văn bản"
    };
    /** Vai trò gốc mỗi bước: 0=Người tạo, 1=Người duyệt, 2=Người phối hợp, 3=Người ký (duyệt cấp ký). */
    private static final int[] STEP_ROLE = {0, 1, 2, 2, 0, 1, 0, 3};
    private static final String[] STEP_ACTION = {"SUBMIT", "APPROVE", "DELEGATE", "SUBMIT", "SUBMIT", "APPROVE", "SUBMIT", "APPROVE"};
    private static final String[][] STEP_ACTIONS = {
            {"SUBMIT", "CANCEL"},
            {"APPROVE", "REJECT"},
            {"DELEGATE", "RETURN"},
            {"SUBMIT", "RETURN"},
            {"SUBMIT"},
            {"APPROVE", "RETURN"},
            {"SUBMIT"},
            {"APPROVE"}
    };
    private static final int[] STEP_SLA = {8, 24, 16, 24, 48, 24, 16, 24};

    private static final String[] STEP_FORMS = {
            // BƯỚC 1 — Tạo mới nhiệm vụ (Người tạo)
            """
            {"fields":[
              {"key":"sec_nv","label":"Thông tin nhiệm vụ","type":"section"},
              {"key":"ten_nhiem_vu","label":"Tên nhiệm vụ","type":"textarea","required":true},
              {"key":"so_ky_hieu","label":"Văn bản yêu cầu — Số ký hiệu","type":"text"},
              {"key":"trich_yeu","label":"Văn bản yêu cầu — Trích yếu","type":"text"},
              {"key":"nguon_vb","label":"Nguồn (Hệ ĐHTN)","type":"text"},
              {"key":"ngay_giao","label":"Ngày giao nhiệm vụ","type":"date"},
              {"key":"han_xu_ly","label":"Hạn xử lý","type":"date","required":true},
              {"key":"van_ban_can_cu","label":"Văn bản căn cứ","type":"text","required":true},
              {"key":"loai_nhiem_vu","label":"Loại nhiệm vụ","type":"text"},
              {"key":"don_vi_chu_tri","label":"Đơn vị chủ trì","type":"text"},
              {"key":"lanh_dao_phe_duyet","label":"Lãnh đạo phê duyệt","type":"text"},
              {"key":"sec_ph","label":"Đơn vị / cá nhân phối hợp (nếu có)","type":"section"},
              {"key":"co_phoi_hop","label":"Có phối hợp","type":"boolean"},
              {"key":"don_vi_phoi_hop","label":"Tên đơn vị phối hợp","type":"text"},
              {"key":"noi_dung_phoi_hop","label":"Nội dung đề nghị phối hợp","type":"textarea"},
              {"key":"han_phoi_hop","label":"Hạn xử lý phối hợp","type":"date"},
              {"key":"ca_nhan_phoi_hop","label":"Cá nhân phối hợp","type":"text"}
            ]}""",
            // BƯỚC 2 — Phê duyệt nhiệm vụ (Người duyệt)
            """
            {"fields":[
              {"key":"y_kien_duyet_nv","label":"Ý kiến phê duyệt nhiệm vụ","type":"textarea","required":true}
            ]}""",
            // BƯỚC 3a — Phân công phối hợp (Người phối hợp cấp phân công)
            """
            {"fields":[
              {"key":"cb_thuc_hien","label":"Họ tên cán bộ thực hiện","type":"text","required":true},
              {"key":"chuc_vu_cb","label":"Chức vụ","type":"text"},
              {"key":"noi_dung_phan_cong","label":"Nội dung phân công","type":"textarea"},
              {"key":"thoi_han_phan_cong","label":"Thời hạn","type":"date"}
            ]}""",
            // BƯỚC 3b — Tham gia ý kiến (Người phối hợp cấp thực hiện)
            """
            {"fields":[
              {"key":"noi_dung_y_kien","label":"Nội dung ý kiến tham gia","type":"richtext","required":true},
              {"key":"tai_lieu_kem","label":"Tài liệu kèm theo","type":"text"}
            ]}""",
            // BƯỚC 4 — Xây dựng dự thảo & tổng hợp, tiếp thu ý kiến (Người tạo)
            """
            {"fields":[
              {"key":"sec_dt","label":"Nội dung dự thảo","type":"section"},
              {"key":"noi_dung_du_thao","label":"Nội dung dự thảo (công văn tham gia ý kiến)","type":"richtext","required":true},
              {"key":"sec_th","label":"Tổng hợp & tiếp thu ý kiến","type":"section"},
              {"key":"tong_hop_y_kien","label":"Tổng hợp ý kiến phối hợp","type":"richtext"},
              {"key":"muc_tiep_thu","label":"Mức tiếp thu","type":"dropdown","optionSource":"STATIC","options":"Tiếp thu, Tiếp thu một phần, Không tiếp thu"},
              {"key":"ly_do_giai_trinh","label":"Lý do / giải trình (nếu không / một phần)","type":"textarea"}
            ]}""",
            // BƯỚC 5 — Phê duyệt dự thảo (Người duyệt)
            """
            {"fields":[
              {"key":"y_kien_duyet_dt","label":"Ý kiến phê duyệt dự thảo","type":"textarea","required":true}
            ]}""",
            // BƯỚC 6 — Hoàn thiện thông tin văn bản (Người tạo)
            """
            {"fields":[
              {"key":"sec_vb","label":"Thông tin văn bản","type":"section"},
              {"key":"do_mat","label":"Độ mật","type":"dropdown","optionSource":"STATIC","options":"Thường, Mật, Tối mật, Tuyệt mật","required":true},
              {"key":"do_khan","label":"Độ khẩn","type":"dropdown","optionSource":"STATIC","options":"Bình thường, Khẩn, Thượng khẩn, Hỏa tốc","required":true},
              {"key":"the_loai_vb","label":"Thể loại văn bản","type":"dropdown","optionSource":"STATIC","options":"Công văn, Báo cáo, Tờ trình, Phiếu trình","required":true},
              {"key":"tao_phieu_trinh","label":"Tạo phiếu trình ký","type":"boolean"},
              {"key":"trich_yeu_noi_dung","label":"Trích yếu nội dung văn bản","type":"textarea","required":true},
              {"key":"nguoi_xu_ly_tiep","label":"Người xử lý tiếp theo","type":"text"}
            ]}""",
            // BƯỚC 7 — Ký, ban hành (Người duyệt cấp ký)
            """
            {"fields":[
              {"key":"y_kien_ky","label":"Ý kiến ký duyệt","type":"textarea"},
              {"key":"so_vb_ban_hanh","label":"Số văn bản ban hành","type":"text"},
              {"key":"ngay_ban_hanh","label":"Ngày ban hành","type":"date"}
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

    @Value("${bpm.seed.qt0101.onboot:false}")
    private boolean seedOnBoot;
    @Value("${bpm.seed.qt0101.reset:false}")
    private boolean resetFirst;

    public Qt0101DemoSeeder(ProcessService processService, FormService formService, WorkflowService workflowService,
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
            log.info("[Qt0101DemoSeeder] onboot: {}", r.message());
        } catch (Exception e) {
            log.warn("[Qt0101DemoSeeder] onboot seed lỗi: {}", e.toString());
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
            log.info("[Qt0101DemoSeeder] Đã xoá cấu hình cũ (key={}).", GUARD_KEY);
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
            return new SeedResult(false, 0, 0, "Đã cấu hình quy trình QT01.01 — bỏ qua (dùng reset để tạo lại).");
        }
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        if (active.isEmpty()) {
            return new SeedResult(false, 0, 0, "Chưa có nhân sự (đang làm việc + có tài khoản) để gán người thực hiện.");
        }
        // Gán 4 vai trò gốc vào người thật (đủ khác nhau nếu có ≥4 người).
        int n = active.size();
        String[] roleUser = {
                active.get(0).getUserAccountId(),
                active.get(1 % n).getUserAccountId(),
                active.get(2 % n).getUserAccountId(),
                active.get(3 % n).getUserAccountId()
        };
        String creatorUsername = username(active.get(0));

        String[] formIds = new String[STEP_NAMES.length];
        String[] taskIds = new String[STEP_NAMES.length];
        String[] userIds = new String[STEP_NAMES.length];
        for (int i = 0; i < STEP_NAMES.length; i++) {
            var f = formService.create(FORM_PREFIX + String.format("%02d", i + 1),
                    "QT01.01 — B" + (i + 1) + ": " + STEP_NAMES[i], "system");
            formService.saveSchema(f.getId(), STEP_FORMS[i], "system");
            formIds[i] = f.getId();
            taskIds[i] = "Task_" + String.format("%02d", i + 1);
            userIds[i] = roleUser[STEP_ROLE[i]];
        }
        String bpmn = bpmnLinear("qt0101", "QT01.01 – Tạo và xử lý nhiệm vụ (Tham gia ý kiến, góp ý)", taskIds, STEP_NAMES);
        String meta = buildStepsMeta(taskIds, userIds, formIds);
        ProcessDefinition p = processService.create(GUARD_KEY,
                "QT01.01 – Tạo và xử lý nhiệm vụ (Tham gia ý kiến, góp ý)", "system");
        processService.saveDesign(p.getId(), bpmn, meta, "system");
        processService.publish(p.getId(), "system");

        AtomicInteger created = new AtomicInteger(0);
        try {
            seedInstances(p, creatorUsername, created);
        } catch (Exception e) {
            log.warn("[Qt0101DemoSeeder] seed hồ sơ lỗi: {}", e.toString());
        }
        int inst = created.get();
        auditPort.record("QT0101_SEEDED", "System", null, actor, "process=" + GUARD_KEY + ", instances=" + inst);
        log.info("[Qt0101DemoSeeder] Cấu hình QT01.01: 1 quy trình 8 bước, {} biểu mẫu, {} hồ sơ.", STEP_NAMES.length, inst);
        return new SeedResult(true, STEP_NAMES.length, inst,
                "Đã cấu hình quy trình QT01.01 (8 bước, " + STEP_NAMES.length + " biểu mẫu, gán theo vai trò Người tạo/duyệt/phối hợp/ký) và "
                        + inst + " hồ sơ mẫu rải các bước.");
    }

    /** Dữ liệu mẫu của từng bước (khớp key biểu mẫu bước đó). */
    private static Map<String, Object> stepData(int step, int seq) {
        LocalDate today = LocalDate.now();
        String[] chuDe = {"Quy chế chi tiêu nội bộ", "Kế hoạch chuyển đổi số", "Đề án sắp xếp tổ chức bộ máy",
                "Dự thảo Quy định quản lý tài sản", "Quy trình tuyển dụng"};
        String cd = chuDe[seq % chuDe.length];
        Map<String, Object> m = new LinkedHashMap<>();
        switch (step) {
            case 0 -> {
                m.put("ten_nhiem_vu", "Tham gia ý kiến dự thảo " + cd);
                m.put("so_ky_hieu", (100 + seq) + "/CV-ĐHTN");
                m.put("trich_yeu", "V/v tham gia ý kiến dự thảo " + cd);
                m.put("nguon_vb", "Hệ ĐHTN");
                m.put("ngay_giao", today.toString());
                m.put("han_xu_ly", today.plusDays(7).toString());
                m.put("van_ban_can_cu", "Công văn " + (100 + seq) + "/CV-ĐHTN");
                m.put("loai_nhiem_vu", "QT01.01: Tham gia ý kiến, góp ý");
                m.put("don_vi_chu_tri", "Phòng Tổng hợp");
                m.put("lanh_dao_phe_duyet", "Lãnh đạo đơn vị");
                m.put("co_phoi_hop", true);
                m.put("don_vi_phoi_hop", "Phòng Pháp chế");
                m.put("noi_dung_phoi_hop", "Đề nghị góp ý Mục 2 và Mục 3 của dự thảo.");
                m.put("han_phoi_hop", today.plusDays(3).toString());
                m.put("ca_nhan_phoi_hop", "Nguyễn Văn A");
            }
            case 1 -> m.put("y_kien_duyet_nv", "Đồng ý chủ trương, giao thực hiện theo kế hoạch.");
            case 2 -> {
                m.put("cb_thuc_hien", "Trần Thị B");
                m.put("chuc_vu_cb", "Chuyên viên");
                m.put("noi_dung_phan_cong", "Nghiên cứu, góp ý Mục 2 và Mục 3 dự thảo.");
                m.put("thoi_han_phan_cong", today.plusDays(3).toString());
            }
            case 3 -> {
                m.put("noi_dung_y_kien", "Nhất trí nội dung dự thảo; đề nghị bổ sung khoản 3 Điều 5 về trách nhiệm phối hợp.");
                m.put("tai_lieu_kem", "Ban_gop_y_chi_tiet.docx");
            }
            case 4 -> {
                m.put("noi_dung_du_thao", "CÔNG VĂN\nV/v tham gia ý kiến dự thảo " + cd + "\nKính gửi: Cơ quan chủ trì soạn thảo. ...");
                m.put("tong_hop_y_kien", "Phòng Pháp chế: nhất trí; đề nghị bổ sung khoản 3 Điều 5.");
                m.put("muc_tiep_thu", "Tiếp thu");
                m.put("ly_do_giai_trinh", "");
            }
            case 5 -> m.put("y_kien_duyet_dt", "Đồng ý nội dung dự thảo, chuyển hoàn thiện để trình ký.");
            case 6 -> {
                m.put("do_mat", "Thường");
                m.put("do_khan", "Bình thường");
                m.put("the_loai_vb", "Công văn");
                m.put("tao_phieu_trinh", true);
                m.put("trich_yeu_noi_dung", "Tham gia ý kiến dự thảo " + cd);
                m.put("nguoi_xu_ly_tiep", "Văn thư đơn vị");
            }
            case 7 -> {
                m.put("y_kien_ky", "Ký ban hành.");
                m.put("so_vb_ban_hanh", (400 + seq) + "/CV-ĐV");
                m.put("ngay_ban_hanh", today.plusDays(8).toString());
            }
            default -> { }
        }
        return m;
    }

    private void seedInstances(ProcessDefinition p, String submitter, AtomicInteger created) {
        int[] depths = {0, 1, 2, 4, 6, 8}; // dừng ở bước 1,2,3,5,7 + hoàn thành
        for (int seq = 0; seq < depths.length; seq++) {
            WorkflowInstance wi = workflowService.start(p.getId(), stepData(0, seq), submitter);
            created.incrementAndGet();
            for (int k = 0; k < depths[seq]; k++) {
                completeActive(wi, STEP_ACTION[Math.min(k, STEP_ACTION.length - 1)], stepData(k, seq));
            }
        }
    }

    private void completeActive(WorkflowInstance wi, String action, Map<String, Object> data) {
        List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
        if (!ts.isEmpty()) {
            workflowService.complete(ts.get(0).getId(), action, data, "system");
        }
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

        int y = 200, w = 130, h = 80, gap = 40, sx = 152, fx = sx + 60;
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
