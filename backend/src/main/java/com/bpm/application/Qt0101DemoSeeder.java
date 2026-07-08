package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.workflow.WorkflowInstance;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.OrgUnitRepository;
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
 * 8 bước theo đặc tả (3 vai trò gốc), có RẼ NHÁNH PHỐI HỢP CÓ ĐIỀU KIỆN: sau Phê duyệt nhiệm vụ,
 * chỉ chạy bước Phân công/Tham gia ý kiến khi {@code co_phoi_hop = có} (cổng exclusiveGateway).
 * Trường chọn người / đơn vị dùng picker hệ thống (orgtree user/unit); có trường BẢNG nhiều dòng.
 * Mỗi bước mang statusLabel (trạng thái nghiệp vụ) hiển thị ở màn theo dõi.
 *
 * <p>Kích hoạt: POST /api/v1/system/seed-qt0101 hoặc cờ bpm.seed.qt0101.onboot=true (+ .reset=true).
 */
@Service
public class Qt0101DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(Qt0101DemoSeeder.class);

    private static final String GUARD_KEY = "qt01-01-tham-gia-y-kien";
    private static final String FORM_PREFIX = "qt0101-buoc-";

    /** 8 userTask theo thứ tự: Task_01..Task_08. */
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
    /** Trạng thái nghiệp vụ khi ĐANG DỪNG ở bước đó (hiển thị màn theo dõi). */
    private static final String[] STEP_STATUS = {
            "Soạn thảo nhiệm vụ",
            "Chờ phê duyệt nhiệm vụ",
            "Đang xử lý — phân công phối hợp",
            "Đang xử lý — tham gia ý kiến",
            "Đang xử lý — soạn dự thảo",
            "Chờ phê duyệt dự thảo",
            "Đang hoàn thiện văn bản",
            "Chờ trình ký / ký ban hành"
    };
    /** 0=Người tạo, 1=Người duyệt, 2=Người phối hợp, 3=Người ký. */
    private static final int[] STEP_ROLE = {0, 1, 2, 2, 0, 1, 0, 3};
    private static final String[] STEP_ACTION = {"SUBMIT", "APPROVE", "DELEGATE", "SUBMIT", "SUBMIT", "APPROVE", "SUBMIT", "APPROVE"};
    private static final String[][] STEP_ACTIONS = {
            {"SUBMIT", "CANCEL"}, {"APPROVE", "REJECT"}, {"DELEGATE", "RETURN"}, {"SUBMIT", "RETURN"},
            {"SUBMIT"}, {"APPROVE", "RETURN"}, {"SUBMIT"}, {"APPROVE"}
    };
    private static final int[] STEP_SLA = {8, 24, 16, 24, 48, 24, 16, 24};

    private static final String[] STEP_FORMS = {
            // BƯỚC 1 — Tạo mới nhiệm vụ (Người tạo)
            """
            {"fields":[
              {"key":"sec_nv","label":"Thông tin nhiệm vụ","type":"section"},
              {"key":"ten_nhiem_vu","label":"Tên nhiệm vụ","type":"textarea","required":true},
              {"key":"van_ban_yeu_cau","label":"Văn bản yêu cầu (Hệ ĐHTN)","type":"table","columns":[
                {"key":"so_ky_hieu","label":"Số ký hiệu"},{"key":"trich_yeu","label":"Trích yếu"},{"key":"nguon","label":"Nguồn"}]},
              {"key":"ngay_giao","label":"Ngày giao nhiệm vụ","type":"date"},
              {"key":"han_xu_ly","label":"Hạn xử lý","type":"date","required":true},
              {"key":"van_ban_can_cu","label":"Văn bản căn cứ","type":"text","required":true},
              {"key":"loai_nhiem_vu","label":"Loại nhiệm vụ","type":"text"},
              {"key":"don_vi_chu_tri","label":"Đơn vị chủ trì","type":"orgtree","pickMode":"unit"},
              {"key":"lanh_dao_phe_duyet","label":"Lãnh đạo phê duyệt (chọn theo cây đơn vị)","type":"unitstaff"},
              {"key":"sec_ph","label":"Đơn vị / cá nhân phối hợp (nếu có)","type":"section"},
              {"key":"co_phoi_hop","label":"Có phối hợp","type":"boolean"},
              {"key":"don_vi_phoi_hop","label":"Đơn vị phối hợp","type":"orgtree","pickMode":"unit"},
              {"key":"noi_dung_phoi_hop","label":"Nội dung đề nghị phối hợp","type":"textarea"},
              {"key":"han_phoi_hop","label":"Hạn xử lý phối hợp","type":"date"},
              {"key":"ca_nhan_phoi_hop","label":"Cá nhân phối hợp (chọn theo cây đơn vị)","type":"unitstaff"}
            ]}""",
            // BƯỚC 2 — Phê duyệt nhiệm vụ (Người duyệt)
            """
            {"fields":[
              {"key":"y_kien_duyet_nv","label":"Ý kiến phê duyệt nhiệm vụ","type":"textarea","required":true}
            ]}""",
            // BƯỚC 3a — Phân công phối hợp
            """
            {"fields":[
              {"key":"cb_thuc_hien","label":"Cán bộ thực hiện (chọn theo cây đơn vị)","type":"unitstaff","required":true},
              {"key":"chuc_vu_cb","label":"Chức vụ","type":"text"},
              {"key":"noi_dung_phan_cong","label":"Nội dung phân công","type":"textarea"},
              {"key":"thoi_han_phan_cong","label":"Thời hạn","type":"date"}
            ]}""",
            // BƯỚC 3b — Tham gia ý kiến
            """
            {"fields":[
              {"key":"noi_dung_y_kien","label":"Nội dung ý kiến tham gia","type":"richtext","required":true},
              {"key":"tai_lieu_kem","label":"Tài liệu kèm theo","type":"text"}
            ]}""",
            // BƯỚC 4 — Xây dựng dự thảo & tổng hợp
            """
            {"fields":[
              {"key":"sec_dt","label":"Nội dung dự thảo","type":"section"},
              {"key":"noi_dung_du_thao","label":"Nội dung dự thảo (công văn tham gia ý kiến)","type":"richtext","required":true},
              {"key":"sec_th","label":"Tổng hợp & tiếp thu ý kiến","type":"section"},
              {"key":"tong_hop_y_kien","label":"Bảng tổng hợp – tiếp thu ý kiến","type":"table","columns":[
                {"key":"nguoi","label":"Người / đơn vị góp ý"},{"key":"noi_dung","label":"Nội dung ý kiến"},
                {"key":"muc_tiep_thu","label":"Mức tiếp thu"},{"key":"ly_do","label":"Lý do / giải trình"}]}
            ]}""",
            // BƯỚC 5 — Phê duyệt dự thảo
            """
            {"fields":[
              {"key":"y_kien_duyet_dt","label":"Ý kiến phê duyệt dự thảo","type":"textarea","required":true}
            ]}""",
            // BƯỚC 6 — Hoàn thiện thông tin văn bản
            """
            {"fields":[
              {"key":"sec_vb","label":"Thông tin văn bản","type":"section"},
              {"key":"do_mat","label":"Độ mật","type":"dropdown","optionSource":"STATIC","options":"Thường, Mật, Tối mật, Tuyệt mật","required":true},
              {"key":"do_khan","label":"Độ khẩn","type":"dropdown","optionSource":"STATIC","options":"Bình thường, Khẩn, Thượng khẩn, Hỏa tốc","required":true},
              {"key":"the_loai_vb","label":"Thể loại văn bản","type":"dropdown","optionSource":"STATIC","options":"Công văn, Báo cáo, Tờ trình, Phiếu trình","required":true},
              {"key":"tao_phieu_trinh","label":"Tạo phiếu trình ký","type":"boolean"},
              {"key":"trich_yeu_noi_dung","label":"Trích yếu nội dung văn bản","type":"textarea","required":true},
              {"key":"nguoi_xu_ly_tiep","label":"Người xử lý tiếp theo (chọn theo cây đơn vị)","type":"unitstaff"}
            ]}""",
            // BƯỚC 7 — Ký, ban hành
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
    private final OrgUnitRepository orgUnitRepo;
    private final AuditPort auditPort;

    @Value("${bpm.seed.qt0101.onboot:false}")
    private boolean seedOnBoot;
    @Value("${bpm.seed.qt0101.reset:false}")
    private boolean resetFirst;
    /** Cờ chạy 1 lần: xoá HẾT hồ sơ QT01.01 lúc boot (GIỮ quy trình + biểu mẫu) — dùng để làm sạch rồi tạo lại. */
    @Value("${bpm.wipe.qt0101.instances.onboot:false}")
    private boolean wipeInstancesOnBoot;

    public Qt0101DemoSeeder(ProcessService processService, FormService formService, WorkflowService workflowService,
                            TaskService taskService, RuntimeService runtimeService, HistoryService historyService,
                            ProcessDefinitionRepository processRepo, WorkflowInstanceRepository instanceRepo,
                            EmployeeRepository employeeRepo, UserAccountRepository userRepo,
                            OrgUnitRepository orgUnitRepo, AuditPort auditPort) {
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
        this.orgUnitRepo = orgUnitRepo;
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

    @EventListener(ApplicationReadyEvent.class)
    void maybeWipeInstancesOnBoot() {
        if (!wipeInstancesOnBoot) {
            return;
        }
        try {
            int n = resetInstancesOnly("system");
            log.info("[Qt0101DemoSeeder] onboot wipe-instances: đã xoá {} hồ sơ QT01.01 (giữ quy trình + biểu mẫu).", n);
        } catch (Exception e) {
            log.warn("[Qt0101DemoSeeder] onboot wipe-instances lỗi: {}", e.toString());
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

    /**
     * Xoá HẾT hồ sơ của QT01.01 (WorkflowInstance + tiến trình runtime + history Flowable) để làm sạch
     * "Việc của tôi" / màn theo dõi, NHƯNG GIỮ NGUYÊN cấu hình quy trình + biểu mẫu (khác {@link #reset}).
     * Sau khi gọi, người dùng tự bấm "Tạo yêu cầu" để tạo hồ sơ mới. Trả về số hồ sơ đã xoá.
     */
    public int resetInstancesOnly(String actor) {
        int[] removed = {0};
        processRepo.findAll().stream().filter(p -> GUARD_KEY.equals(p.getProcessKey())).findFirst().ifPresent(p -> {
            for (WorkflowInstance wi : instanceRepo.findByProcessId(p.getId())) {
                try {
                    if ("RUNNING".equals(wi.getStatus())) {
                        runtimeService.deleteProcessInstance(wi.getFlowableInstanceId(), "wipe QT01.01 instances");
                    }
                } catch (Exception ignore) { /* */ }
                try {
                    historyService.deleteHistoricProcessInstance(wi.getFlowableInstanceId());
                } catch (Exception ignore) { /* */ }
                instanceRepo.delete(wi);
                removed[0]++;
            }
        });
        auditPort.record("QT0101_INSTANCES_WIPED", "System", null, actor,
                "process=" + GUARD_KEY + ", removed=" + removed[0]);
        log.info("[Qt0101DemoSeeder] Đã xoá {} hồ sơ QT01.01 (giữ nguyên quy trình + biểu mẫu).", removed[0]);
        return removed[0];
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
        int n = active.size();
        String[] roleUser = {
                active.get(0).getUserAccountId(), active.get(1 % n).getUserAccountId(),
                active.get(2 % n).getUserAccountId(), active.get(3 % n).getUserAccountId()
        };
        String creatorUsername = username(active.get(0));
        // Đơn vị mẫu cho các trường orgtree (ưu tiên đơn vị của người tạo).
        String sampleUnitId = active.get(0).getOrgUnitId();
        if (sampleUnitId == null || sampleUnitId.isBlank()) {
            sampleUnitId = orgUnitRepo.findAll().stream().findFirst().map(u -> u.getId()).orElse("");
        }

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
        String bpmn = bpmnQt0101(taskIds, STEP_NAMES);
        String meta = buildStepsMeta(taskIds, userIds, formIds);
        ProcessDefinition p = processService.create(GUARD_KEY,
                "QT01.01 – Tạo và xử lý nhiệm vụ (Tham gia ý kiến, góp ý)", "system");
        processService.saveDesign(p.getId(), bpmn, meta, "system");
        processService.publish(p.getId(), "system");

        AtomicInteger created = new AtomicInteger(0);
        try {
            seedInstances(p, creatorUsername, roleUser, sampleUnitId, created);
        } catch (Exception e) {
            log.warn("[Qt0101DemoSeeder] seed hồ sơ lỗi: {}", e.toString());
        }
        int inst = created.get();
        auditPort.record("QT0101_SEEDED", "System", null, actor, "process=" + GUARD_KEY + ", instances=" + inst);
        log.info("[Qt0101DemoSeeder] Cấu hình QT01.01: 1 quy trình 8 bước (có rẽ nhánh phối hợp), {} biểu mẫu, {} hồ sơ.",
                STEP_NAMES.length, inst);
        return new SeedResult(true, STEP_NAMES.length, inst,
                "Đã cấu hình QT01.01 (8 bước, rẽ nhánh phối hợp có điều kiện, picker người/đơn vị, bảng nhiều dòng, trạng thái nghiệp vụ) và "
                        + inst + " hồ sơ mẫu.");
    }

    private Map<String, Object> stepData(int step, int seq, String[] users, String unitId, boolean coPhoiHop) {
        LocalDate today = LocalDate.now();
        String[] chuDe = {"Quy chế chi tiêu nội bộ", "Kế hoạch chuyển đổi số", "Đề án sắp xếp tổ chức bộ máy",
                "Dự thảo Quy định quản lý tài sản", "Quy trình tuyển dụng"};
        String cd = chuDe[seq % chuDe.length];
        Map<String, Object> m = new LinkedHashMap<>();
        switch (step) {
            case 0 -> {
                m.put("ten_nhiem_vu", "Tham gia ý kiến dự thảo " + cd);
                m.put("van_ban_yeu_cau", "[{\"so_ky_hieu\":\"" + (100 + seq) + "/CV-ĐHTN\",\"trich_yeu\":\"V/v tham gia ý kiến dự thảo "
                        + cd + "\",\"nguon\":\"Hệ ĐHTN\"}]");
                m.put("ngay_giao", today.toString());
                m.put("han_xu_ly", today.plusDays(7).toString());
                m.put("van_ban_can_cu", "Công văn " + (100 + seq) + "/CV-ĐHTN");
                m.put("loai_nhiem_vu", "QT01.01: Tham gia ý kiến, góp ý");
                m.put("don_vi_chu_tri", unitId);
                m.put("lanh_dao_phe_duyet", users[1]);
                m.put("co_phoi_hop", coPhoiHop);
                if (coPhoiHop) {
                    m.put("don_vi_phoi_hop", unitId);
                    m.put("noi_dung_phoi_hop", "Đề nghị góp ý Mục 2 và Mục 3 của dự thảo.");
                    m.put("han_phoi_hop", today.plusDays(3).toString());
                    m.put("ca_nhan_phoi_hop", users[2]);
                }
            }
            case 1 -> m.put("y_kien_duyet_nv", "Đồng ý chủ trương, giao thực hiện theo kế hoạch.");
            case 2 -> {
                m.put("cb_thuc_hien", users[2]);
                m.put("chuc_vu_cb", "Chuyên viên");
                m.put("noi_dung_phan_cong", "Nghiên cứu, góp ý Mục 2 và Mục 3 dự thảo.");
                m.put("thoi_han_phan_cong", today.plusDays(3).toString());
            }
            case 3 -> {
                m.put("noi_dung_y_kien", "Nhất trí nội dung; đề nghị bổ sung khoản 3 Điều 5 về trách nhiệm phối hợp.");
                m.put("tai_lieu_kem", "Ban_gop_y_chi_tiet.docx");
            }
            case 4 -> {
                m.put("noi_dung_du_thao", "CÔNG VĂN\nV/v tham gia ý kiến dự thảo " + cd + "\nKính gửi: Cơ quan chủ trì soạn thảo. ...");
                m.put("tong_hop_y_kien", "[{\"nguoi\":\"Phòng Pháp chế\",\"noi_dung\":\"Nhất trí; bổ sung khoản 3 Điều 5\",\"muc_tiep_thu\":\"Tiếp thu\",\"ly_do\":\"\"}]");
            }
            case 5 -> m.put("y_kien_duyet_dt", "Đồng ý nội dung dự thảo, chuyển hoàn thiện để trình ký.");
            case 6 -> {
                m.put("do_mat", "Thường");
                m.put("do_khan", "Bình thường");
                m.put("the_loai_vb", "Công văn");
                m.put("tao_phieu_trinh", true);
                m.put("trich_yeu_noi_dung", "Tham gia ý kiến dự thảo " + cd);
                m.put("nguoi_xu_ly_tiep", users[0]);
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

    private void seedInstances(ProcessDefinition p, String submitter, String[] users, String unitId, AtomicInteger created) {
        // depth = số bước đã hoàn thành (theo thứ tự Task_01..Task_08). Các hồ sơ này CÓ phối hợp.
        // Phủ ĐỦ cả 8 bước (mỗi bước ≥1 hồ sơ đang dừng) + nhiều hồ sơ đi TRỌN quy trình (hoàn thành).
        int[] depths = {0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 8, 8, 2, 4, 6};
        for (int seq = 0; seq < depths.length; seq++) {
            WorkflowInstance wi = workflowService.start(p.getId(), stepData(0, seq, users, unitId, true), submitter);
            created.incrementAndGet();
            for (int k = 0; k < depths[seq]; k++) {
                completeActive(wi, STEP_ACTION[Math.min(k, STEP_ACTION.length - 1)], stepData(k, seq, users, unitId, true));
            }
        }
        // 1 hồ sơ KHÔNG phối hợp → qua gateway sẽ BỎ QUA phân công/tham gia, sang thẳng bước dự thảo.
        WorkflowInstance wiNo = workflowService.start(p.getId(), stepData(0, 5, users, unitId, false), submitter);
        created.incrementAndGet();
        completeActive(wiNo, STEP_ACTION[0], stepData(0, 5, users, unitId, false)); // Task_01: Trình duyệt
        completeActive(wiNo, STEP_ACTION[1], stepData(1, 5, users, unitId, false)); // Task_02: Phê duyệt
        String landed = taskService.createTaskQuery().processInstanceId(wiNo.getFlowableInstanceId())
                .list().stream().map(Task::getName).findFirst().orElse("(không có việc)");
        log.info("[Qt0101DemoSeeder] Hồ sơ KHÔNG phối hợp sau duyệt → dừng ở bước: {}", landed);
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

    /** stepsMeta: mỗi userTask (assignee/SLA/actions/formId + statusLabel) + điều kiện rẽ nhánh trên Flow_cond. */
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
                    .append("\"statusLabel\":\"").append(STEP_STATUS[i]).append("\",")
                    .append("\"formId\":\"").append(formIds[i]).append("\",")
                    .append("\"actions\":").append(acts)
                    .append('}');
        }
        // Điều kiện rẽ nhánh phối hợp: chỉ đi Flow_cond (→ Phân công phối hợp) khi co_phoi_hop có giá trị.
        sb.append(",\"Flow_cond\":{\"condition\":{\"field\":\"co_phoi_hop\",\"op\":\"truthy\"}}");
        return sb.append('}').toString();
    }

    /**
     * BPMN QT01.01 có rẽ nhánh phối hợp:
     * Start → T01 → T02 → G1 ⟨co_phoi_hop⟩ → T03 → T04 → T05 → T06 → T07 → T08 → End,
     * nhánh mặc định của G1 bỏ qua T03/T04, đi thẳng T05.
     * (taskIds[0..7] = T01..T08 = Tạo/Duyệt NV/Phân công/Tham gia/Dự thảo/Duyệt DT/Hoàn thiện/Ký.)
     */
    private static String bpmnQt0101(String[] t, String[] names) {
        StringBuilder proc = new StringBuilder();
        proc.append("<bpmn:startEvent id=\"StartEvent_1\" name=\"Bắt đầu\"><bpmn:outgoing>Flow_0</bpmn:outgoing></bpmn:startEvent>");
        // userTask incoming/outgoing (T05 có 2 incoming: Flow_skip + Flow_4)
        proc.append(userTask(t[0], names[0], "Flow_0", "Flow_1"));
        proc.append(userTask(t[1], names[1], "Flow_1", "Flow_2"));
        proc.append("<bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Có phối hợp?\" default=\"Flow_skip\">")
                .append("<bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_cond</bpmn:outgoing>")
                .append("<bpmn:outgoing>Flow_skip</bpmn:outgoing></bpmn:exclusiveGateway>");
        proc.append(userTask(t[2], names[2], "Flow_cond", "Flow_3"));
        proc.append(userTask(t[3], names[3], "Flow_3", "Flow_4"));
        // T05 hai incoming
        proc.append("<bpmn:userTask id=\"").append(t[4]).append("\" name=\"").append(xml(names[4]))
                .append("\"><bpmn:incoming>Flow_skip</bpmn:incoming><bpmn:incoming>Flow_4</bpmn:incoming>")
                .append("<bpmn:outgoing>Flow_5</bpmn:outgoing></bpmn:userTask>");
        proc.append(userTask(t[5], names[5], "Flow_5", "Flow_6"));
        proc.append(userTask(t[6], names[6], "Flow_6", "Flow_7"));
        proc.append(userTask(t[7], names[7], "Flow_7", "Flow_8"));
        proc.append("<bpmn:endEvent id=\"EndEvent_1\" name=\"Hoàn thành\"><bpmn:incoming>Flow_8</bpmn:incoming></bpmn:endEvent>");
        // sequence flows
        proc.append(flow("Flow_0", "StartEvent_1", t[0]));
        proc.append(flow("Flow_1", t[0], t[1]));
        proc.append(flow("Flow_2", t[1], "Gateway_1"));
        // Nhánh phối hợp: ĐIỀU KIỆN gắn thẳng vào BPMN — chỉ đi khi co_phoi_hop = true; ngược lại theo default Flow_skip.
        proc.append("<bpmn:sequenceFlow id=\"Flow_cond\" sourceRef=\"Gateway_1\" targetRef=\"").append(t[2]).append("\">")
                .append("<bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">${co_phoi_hop == true}</bpmn:conditionExpression>")
                .append("</bpmn:sequenceFlow>");
        proc.append(flow("Flow_skip", "Gateway_1", t[4]));
        proc.append(flow("Flow_3", t[2], t[3]));
        proc.append(flow("Flow_4", t[3], t[4]));
        proc.append(flow("Flow_5", t[4], t[5]));
        proc.append(flow("Flow_6", t[5], t[6]));
        proc.append(flow("Flow_7", t[6], t[7]));
        proc.append(flow("Flow_8", t[7], "EndEvent_1"));

        // ---- DI (đơn giản, một hàng ngang; cạnh nhánh có thể chồng — chỉ để hiển thị) ----
        StringBuilder di = new StringBuilder();
        int y = 220, w = 130, h = 80;
        int[] x = new int[8];
        int sx = 120;
        int gx = 0;
        di.append(shape("StartEvent_1", sx, y - 18, 36, 36));
        int cur = sx + 70;
        // T01, T02
        x[0] = cur; di.append(shape(t[0], x[0], y - h / 2, w, h)); cur += w + 40;
        x[1] = cur; di.append(shape(t[1], x[1], y - h / 2, w, h)); cur += w + 40;
        gx = cur; di.append("<bpmndi:BPMNShape id=\"Gateway_1_di\" bpmnElement=\"Gateway_1\"><dc:Bounds x=\"")
                .append(gx).append("\" y=\"").append(y - 25).append("\" width=\"50\" height=\"50\" /></bpmndi:BPMNShape>");
        cur = gx + 90;
        x[2] = cur; di.append(shape(t[2], x[2], y + 40, w, h)); // nhánh phối hợp xuống dưới
        cur += w + 40;
        x[3] = cur; di.append(shape(t[3], x[3], y + 40, w, h)); cur += w + 40;
        x[4] = cur; di.append(shape(t[4], x[4], y - h / 2, w, h)); cur += w + 40;
        x[5] = cur; di.append(shape(t[5], x[5], y - h / 2, w, h)); cur += w + 40;
        x[6] = cur; di.append(shape(t[6], x[6], y - h / 2, w, h)); cur += w + 40;
        x[7] = cur; di.append(shape(t[7], x[7], y - h / 2, w, h)); cur += w + 40;
        int ex = cur;
        di.append(shape("EndEvent_1", ex, y - 18, 36, 36));
        di.append(edge("Flow_0", sx + 36, y, x[0], y));
        di.append(edge("Flow_1", x[0] + w, y, x[1], y));
        di.append(edge("Flow_2", x[1] + w, y, gx, y));
        di.append(edge("Flow_cond", gx + 25, y + 25, x[2], y + 80));
        di.append(edge("Flow_skip", gx + 50, y, x[4], y));
        di.append(edge("Flow_3", x[2] + w, y + 80, x[3], y + 80));
        di.append(edge("Flow_4", x[3] + w, y + 80, x[4], y));
        di.append(edge("Flow_5", x[4] + w, y, x[5], y));
        di.append(edge("Flow_6", x[5] + w, y, x[6], y));
        di.append(edge("Flow_7", x[6] + w, y, x[7], y));
        di.append(edge("Flow_8", x[7] + w, y, ex, y));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " id=\"Definitions_qt0101\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "<bpmn:process id=\"Process_qt0101\" name=\"QT01.01 – Tạo và xử lý nhiệm vụ\" isExecutable=\"true\">"
                + proc + "</bpmn:process>"
                + "<bpmndi:BPMNDiagram id=\"BPMNDiagram_1\"><bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_qt0101\">"
                + di + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>";
    }

    private static String userTask(String id, String name, String in, String out) {
        return "<bpmn:userTask id=\"" + id + "\" name=\"" + xml(name) + "\"><bpmn:incoming>" + in
                + "</bpmn:incoming><bpmn:outgoing>" + out + "</bpmn:outgoing></bpmn:userTask>";
    }

    private static String flow(String id, String src, String tgt) {
        return "<bpmn:sequenceFlow id=\"" + id + "\" sourceRef=\"" + src + "\" targetRef=\"" + tgt + "\" />";
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
