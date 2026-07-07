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
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
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
 * Quy trình mẫu: "Mua sắm – Thanh toán" (Purchase-to-Pay), 10 bước tuần tự, gán tới NHÂN SỰ THẬT.
 *
 * <p>MỖI BƯỚC được cấu hình ĐẦY ĐỦ và có BIỂU MẪU RIÊNG (chỉ chứa trường thông tin của đúng bước đó):
 * người thực hiện + SLA + hành động + biểu mẫu bước. Cấu hình là DỮ LIỆU THẬT lưu DB
 * (process_definition.steps_meta_json + form_definition.schema_json), không hard-code ở FE. Nhiều hồ sơ
 * được rải ĐỦ 10 bước + hoàn thành + quá hạn, mỗi bước ghi dữ liệu thật của bước đó khi hoàn thành.
 *
 * <p>Kích hoạt: ADMIN bấm nút (POST /api/v1/system/seed-process-demo) — HOẶC tự chạy khi khởi động nếu
 * đặt cờ {@code bpm.seed.process10.onboot=true} (chỉ dùng khi seed thủ công qua BE local, mặc định TẮT);
 * kèm {@code bpm.seed.process10.reset=true} để XOÁ demo cũ rồi tạo lại (cấu hình mới).
 */
@Service
public class Process10StepDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(Process10StepDemoSeeder.class);

    /** Khoá nhận diện đã-seed (idempotent). */
    private static final String GUARD_KEY = "mua-sam-p2p-10b";
    /** Mỗi bước MỘT biểu mẫu riêng (trường của đúng bước đó): ms-buoc-01 .. ms-buoc-10. */
    private static final String FORM_PREFIX = "ms-buoc-";

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
    /** Hành động CHÍNH của mỗi bước (dùng khi hoàn thành để tiến bước) — mã hợp lệ với designer. */
    private static final String[] STEP_ACTION = {
            "SUBMIT", "APPROVE", "RECORD", "RECORD", "APPROVE", "RECORD", "RECORD", "APPROVE", "RECORD", "RECORD"
    };
    /** Tập hành động (nút) cấu hình cho từng bước — chỉ dùng mã trong bảng hành động của designer. */
    private static final String[][] STEP_ACTIONS = {
            {"SUBMIT"}, {"APPROVE", "REJECT", "RETURN"}, {"RECORD"}, {"RECORD"}, {"APPROVE", "REJECT", "RETURN"},
            {"RECORD"}, {"RECORD"}, {"APPROVE", "REJECT"}, {"RECORD"}, {"RECORD"}
    };
    /** SLA (giờ) từng bước. */
    private static final int[] STEP_SLA = {4, 16, 24, 16, 24, 24, 48, 16, 24, 8};

    /** Một trường thông tin của biểu mẫu tổng hợp, gắn với BƯỚC sở hữu (owner). */
    private record F(String key, String label, String type, int owner, boolean required, String options) {
        F(String key, String label, String type, int owner) {
            this(key, label, type, owner, false, null);
        }
    }

    /** 40 trường của cả 10 bước — thứ tự hiển thị theo bước. */
    private static final F[] FIELDS = {
            // Bước 1 — Lập đề nghị mua sắm
            new F("tieu_de", "Tiêu đề đề nghị", "text", 0, true, null),
            new F("hang_muc", "Hạng mục / hàng hoá", "text", 0, true, null),
            new F("so_luong", "Số lượng", "number", 0, true, null),
            new F("don_gia", "Đơn giá dự kiến (VNĐ)", "number", 0),
            new F("thanh_tien", "Thành tiền dự kiến (VNĐ)", "number", 0),
            new F("ly_do", "Lý do / mục đích mua sắm", "richtext", 0, true, null),
            new F("can_truoc_ngay", "Cần trước ngày", "date", 0),
            // Bước 2 — Trưởng bộ phận duyệt nhu cầu
            new F("muc_do_uu_tien", "Mức độ ưu tiên", "dropdown", 1, true, "Cao, Trung bình, Thấp"),
            new F("y_kien_tbp", "Ý kiến trưởng bộ phận", "textarea", 1),
            // Bước 3 — Khảo giá & lấy báo giá
            new F("ncc_1", "Nhà cung cấp 1", "text", 2),
            new F("gia_1", "Đơn giá NCC 1 (VNĐ)", "number", 2),
            new F("ncc_2", "Nhà cung cấp 2", "text", 2),
            new F("gia_2", "Đơn giá NCC 2 (VNĐ)", "number", 2),
            new F("ncc_3", "Nhà cung cấp 3", "text", 2),
            new F("gia_3", "Đơn giá NCC 3 (VNĐ)", "number", 2),
            new F("ghi_chu_khao_gia", "Ghi chú khảo giá", "richtext", 2),
            // Bước 4 — So sánh & chọn nhà cung cấp
            new F("ncc_chon", "Nhà cung cấp được chọn", "text", 3, true, null),
            new F("gia_chon", "Đơn giá chốt (VNĐ)", "number", 3, true, null),
            new F("ly_do_chon", "Lý do lựa chọn", "richtext", 3),
            // Bước 5 — Giám đốc phê duyệt ngân sách
            new F("nguon_ngan_sach", "Nguồn ngân sách", "dropdown", 4, true,
                    "Ngân sách vận hành, Ngân sách đầu tư, Ngoài ngân sách"),
            new F("han_muc_duyet", "Hạn mức phê duyệt (VNĐ)", "number", 4),
            new F("y_kien_gd", "Ý kiến Giám đốc", "textarea", 4),
            // Bước 6 — Lập & ký hợp đồng/PO
            new F("so_po", "Số hợp đồng / PO", "text", 5, true, null),
            new F("ngay_po", "Ngày ký", "date", 5),
            new F("gia_tri_po", "Giá trị hợp đồng (VNĐ)", "number", 5),
            new F("dieu_khoan_tt", "Điều khoản thanh toán", "textarea", 5),
            // Bước 7 — Nhận hàng & kiểm tra chất lượng
            new F("ngay_nhan", "Ngày nhận hàng", "date", 6),
            new F("so_luong_nhan", "Số lượng thực nhận", "number", 6),
            new F("tinh_trang_hang", "Tình trạng hàng", "dropdown", 6, false, "Đạt, Không đạt, Đạt một phần"),
            new F("ghi_chu_kiem_tra", "Biên bản kiểm tra", "richtext", 6),
            // Bước 8 — Nghiệm thu
            new F("ket_qua_nghiem_thu", "Kết quả nghiệm thu", "dropdown", 7, true, "Đạt, Không đạt"),
            new F("ngay_nghiem_thu", "Ngày nghiệm thu", "date", 7),
            new F("y_kien_nghiem_thu", "Ý kiến nghiệm thu", "richtext", 7),
            // Bước 9 — Kế toán thanh toán
            new F("so_chung_tu", "Số chứng từ thanh toán", "text", 8, true, null),
            new F("ngay_thanh_toan", "Ngày thanh toán", "date", 8),
            new F("so_tien_thanh_toan", "Số tiền thanh toán (VNĐ)", "number", 8),
            new F("hinh_thuc_thanh_toan", "Hình thức thanh toán", "dropdown", 8, false, "Chuyển khoản, Tiền mặt"),
            // Bước 10 — Lưu hồ sơ & đóng hồ sơ
            new F("so_luu_tru", "Số lưu trữ hồ sơ", "text", 9, true, null),
            new F("ngay_dong_ho_so", "Ngày đóng hồ sơ", "date", 9),
            new F("ghi_chu_luu_tru", "Ghi chú lưu trữ", "textarea", 9)
    };

    private final ProcessService processService;
    private final FormService formService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessEngine processEngine;
    private final ProcessDefinitionRepository processRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort auditPort;

    @Value("${bpm.seed.process10.onboot:false}")
    private boolean seedOnBoot;
    @Value("${bpm.seed.process10.reset:false}")
    private boolean resetFirst;

    public Process10StepDemoSeeder(ProcessService processService, FormService formService,
                                   WorkflowService workflowService, TaskService taskService,
                                   RuntimeService runtimeService, HistoryService historyService,
                                   ProcessEngine processEngine, ProcessDefinitionRepository processRepo,
                                   WorkflowInstanceRepository instanceRepo, EmployeeRepository employeeRepo,
                                   UserAccountRepository userRepo, AuditPort auditPort) {
        this.processService = processService;
        this.formService = formService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.processEngine = processEngine;
        this.processRepo = processRepo;
        this.instanceRepo = instanceRepo;
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
            if (resetFirst) {
                reset("system");
            }
            SeedResult r = seed("system");
            log.info("[Process10StepDemoSeeder] onboot: {}", r.message());
        } catch (Exception e) {
            log.warn("[Process10StepDemoSeeder] onboot seed lỗi: {}", e.toString());
        }
    }

    /** Tóm tắt kết quả seed (trả về cho FE). */
    public record SeedResult(boolean seeded, int steps, int instances, String message) {
    }

    /** Xoá demo cũ (hồ sơ + quy trình + biểu mẫu) để tạo lại với cấu hình mới. */
    public void reset(String actor) {
        processRepo.findAll().stream()
                .filter(p -> GUARD_KEY.equals(p.getProcessKey()))
                .findFirst()
                .ifPresent(p -> {
                    for (WorkflowInstance wi : instanceRepo.findByProcessId(p.getId())) {
                        try {
                            if ("RUNNING".equals(wi.getStatus())) {
                                runtimeService.deleteProcessInstance(wi.getFlowableInstanceId(), "reset demo");
                            }
                        } catch (Exception ignore) {
                            /* runtime đã kết thúc */
                        }
                        try {
                            historyService.deleteHistoricProcessInstance(wi.getFlowableInstanceId());
                        } catch (Exception ignore) {
                            /* không có history */
                        }
                        instanceRepo.delete(wi);
                    }
                    processService.delete(p.getId(), actor);
                    log.info("[Process10StepDemoSeeder] Đã xoá demo cũ (key={}).", GUARD_KEY);
                });
        formService.list().stream()
                .filter(f -> f.getFormKey() != null && f.getFormKey().startsWith(FORM_PREFIX))
                .forEach(f -> {
                    try {
                        formService.delete(f.getId(), actor);
                    } catch (Exception ignore) {
                        /* bỏ qua */
                    }
                });
    }

    /**
     * Tạo 1 quy trình 10 bước (cấu hình đầy đủ mọi bước) + nhiều hồ sơ rải đủ 10 bước + hoàn thành + quá hạn.
     * Idempotent theo key.
     */
    public SeedResult seed(String actor) {
        if (processRepo.existsByProcessKey(GUARD_KEY)) {
            log.info("[Process10StepDemoSeeder] Đã có quy trình 10 bước demo (key={}), bỏ qua.", GUARD_KEY);
            auditPort.record("PROCESS10_DEMO_SEED_SKIPPED", "System", null, actor, "Đã tồn tại — không seed lại.");
            return new SeedResult(false, 0, 0,
                    "Đã có dữ liệu quy trình 10 bước demo từ trước — bỏ qua (dùng reset để tạo lại).");
        }

        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        if (active.isEmpty()) {
            return new SeedResult(false, 0, 0,
                    "Chưa có nhân sự (đang làm việc + có tài khoản) để gán người thực hiện. Hãy import nhân sự trước.");
        }
        Picker people = new Picker(active);

        // Mỗi bước MỘT biểu mẫu riêng (chỉ chứa trường thông tin của đúng bước đó) — cấu hình thật, lưu DB.
        String[] formIds = new String[STEP_NAMES.length];
        for (int i = 0; i < STEP_NAMES.length; i++) {
            formIds[i] = form(FORM_PREFIX + String.format("%02d", i + 1),
                    "Bước " + (i + 1) + " — " + STEP_NAMES[i], buildStepFormSchema(i));
        }

        // Quy trình 10 bước: mỗi bước gán 1 người thật + SLA + hành động + BIỂU MẪU RIÊNG của bước.
        String[] taskIds = new String[STEP_NAMES.length];
        String[] userIds = new String[STEP_NAMES.length];
        for (int i = 0; i < STEP_NAMES.length; i++) {
            taskIds[i] = "Task_" + String.format("%02d", i + 1);
            userIds[i] = pick(people);
        }
        String bpmn = bpmnLinear("muasamp2p", "Quy trình Mua sắm – Thanh toán", taskIds, STEP_NAMES);
        String meta = buildStepsMeta(taskIds, userIds, formIds);
        ProcessDefinition p = publish(GUARD_KEY, "Quy trình Mua sắm – Thanh toán (10 bước)", bpmn, meta);

        // Hồ sơ chạy: rải đủ 10 bước (3 hồ sơ/bước) + hoàn thành + quá hạn, kèm dữ liệu thật.
        AtomicInteger created = new AtomicInteger(0);
        try {
            seedRunningInstances(p, people, created);
        } catch (Exception e) {
            log.warn("[Process10StepDemoSeeder] Seed hồ sơ chạy gặp lỗi (bỏ qua, cấu hình quy trình vẫn đủ): {}",
                    e.toString());
        }

        int instances = created.get();
        auditPort.record("PROCESS10_DEMO_SEEDED", "System", null, actor,
                "process=" + GUARD_KEY + ", steps=10, fields=" + FIELDS.length + ", instances=" + instances);
        log.info("[Process10StepDemoSeeder] Seed quy trình 10 bước: 1 quy trình ({} trường), {} hồ sơ.",
                FIELDS.length, instances);
        return new SeedResult(true, 10, instances,
                "Đã tạo 1 quy trình 10 bước (Mua sắm – Thanh toán) cấu hình đầy đủ " + FIELDS.length
                        + " trường thông tin theo bước, và " + instances
                        + " hồ sơ demo rải đủ các bước (gồm hoàn thành & quá hạn).");
    }

    /** Danh mục hàng hoá để rải hồ sơ (tên/đơn giá khác nhau cho sinh động). */
    private static final Object[][] ITEMS = {
            {"Mua 15 laptop cho phòng Kỹ thuật", "Laptop Dell Latitude 5540", 15, 22_000_000L},
            {"Trang bị điện thoại cho đội Kinh doanh", "iPhone 15 cấp cho Sale", 8, 24_000_000L},
            {"Mua bàn ghế văn phòng tầng 5", "Bàn ghế công thái học", 30, 3_500_000L},
            {"Thuê dịch vụ kiểm thử bảo mật hệ thống", "Pentest hệ thống BPM", 1, 80_000_000L},
            {"Mua license phần mềm thiết kế", "Adobe Creative Cloud (năm)", 10, 15_000_000L},
            {"Nâng cấp máy chủ ảo hoá trung tâm dữ liệu", "Server Dell PowerEdge R760", 2, 250_000_000L},
            {"Mua vật tư tiêu hao văn phòng Quý III", "Giấy, mực in, văn phòng phẩm", 1, 12_000_000L},
            {"Mua thiết bị mạng cho chi nhánh Hà Nội", "Switch + Access Point Cisco", 12, 9_000_000L},
            {"Mua máy chiếu 4K cho phòng họp lớn", "Máy chiếu Epson 4K", 3, 18_000_000L},
            {"Mua UPS cho phòng máy chủ", "UPS 10kVA", 2, 55_000_000L},
            {"Gia hạn tên miền & chứng chỉ SSL", "Domain + SSL wildcard", 1, 6_000_000L},
            {"Đặt in ấn brochure marketing 2026", "Brochure + catalogue", 5000, 8_000L},
            {"Mua ghế công thái học cho Ban giám đốc", "Ghế Herman Miller", 5, 28_000_000L},
            {"Thuê hội trường tổ chức sự kiện năm", "Hội trường 300 khách", 1, 45_000_000L},
            {"Thuê xe đưa đón team building", "Xe 45 chỗ x3 ngày", 3, 15_000_000L},
            {"Mua camera an ninh trụ sở chính", "Camera IP + đầu ghi", 24, 4_500_000L},
            {"Mua phần mềm diệt virus bản quyền", "Antivirus doanh nghiệp (năm)", 200, 350_000L},
            {"Mua tủ đựng hồ sơ phòng Kế toán", "Tủ sắt 4 ngăn", 10, 2_800_000L},
    };

    /**
     * Rải hồ sơ: mỗi bước (1..10) có 3 hồ sơ đang dừng + 4 hồ sơ hoàn thành + 4 hồ sơ quá hạn ở giữa quy trình.
     * depth = số bước đã hoàn thành = chỉ số bước đang dừng (0 → bước 1, 9 → bước 10, 10 → hoàn thành).
     */
    private void seedRunningInstances(ProcessDefinition p, Picker people, AtomicInteger created) {
        List<int[]> specs = new ArrayList<>(); // {depth, backdatedHours}
        for (int depth = 0; depth < 10; depth++) {
            specs.add(new int[]{depth, 0});
            specs.add(new int[]{depth, 0});
            specs.add(new int[]{depth, 0});
        }
        specs.add(new int[]{10, 0});
        specs.add(new int[]{10, 0});
        specs.add(new int[]{10, 0});
        specs.add(new int[]{10, 0});
        // Quá hạn ở giữa quy trình (lùi giờ quá SLA của bước đang dừng).
        specs.add(new int[]{2, 60});
        specs.add(new int[]{4, 72});
        specs.add(new int[]{6, 96});
        specs.add(new int[]{8, 48});

        int seq = 0;
        for (int[] s : specs) {
            int depth = s[0];
            int backdated = s[1];
            Object[] item = ITEMS[seq % ITEMS.length];
            Map<String, Object> full = fullData(item, seq);
            String submitter = username(people.next());
            if (backdated > 0) {
                runBackdated(p, backdated, full, submitter, depth, created);
            } else {
                run(p, full, submitter, depth, created);
            }
            seq++;
        }
    }

    // ===================== Khởi tạo & tiến bước (kèm dữ liệu từng bước) =====================

    /** Khởi tạo hồ sơ với dữ liệu bước 1, rồi hoàn thành {@code completes} bước (mỗi bước ghi dữ liệu của bước đó). */
    private WorkflowInstance run(ProcessDefinition p, Map<String, Object> full, String submitter,
                                 int completes, AtomicInteger created) {
        WorkflowInstance wi = workflowService.start(p.getId(), stepData(0, full), submitter);
        created.incrementAndGet();
        for (int k = 0; k < completes; k++) {
            completeActive(wi, STEP_ACTION[Math.min(k, STEP_ACTION.length - 1)], stepData(k, full));
        }
        return wi;
    }

    /** Như {@link #run} nhưng lùi đồng hồ engine để việc được tạo "trong quá khứ" (mô phỏng quá hạn). */
    private WorkflowInstance runBackdated(ProcessDefinition p, int hoursAgo, Map<String, Object> full,
                                          String submitter, int completes, AtomicInteger created) {
        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(hoursAgo))));
        try {
            return run(p, full, submitter, completes, created);
        } finally {
            clock.reset();
        }
    }

    private void completeActive(WorkflowInstance wi, String action, Map<String, Object> data) {
        List<Task> ts = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
        if (!ts.isEmpty()) {
            workflowService.complete(ts.get(0).getId(), action, data, "system");
        }
    }

    /** Lọc dữ liệu đầy đủ lấy đúng các trường của bước {@code stepIndex}. */
    private static Map<String, Object> stepData(int stepIndex, Map<String, Object> full) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (F f : FIELDS) {
            if (f.owner() == stepIndex && full.containsKey(f.key())) {
                m.put(f.key(), full.get(f.key()));
            }
        }
        return m;
    }

    // ===================== Dữ liệu mẫu đầy đủ 40 trường =====================

    private static Map<String, Object> fullData(Object[] item, int seq) {
        String tieuDe = (String) item[0];
        String hangMuc = (String) item[1];
        int soLuong = (int) item[2];
        long donGia = (long) item[3];
        long gia1 = donGia;
        long gia2 = Math.round(donGia * 0.96);
        long gia3 = Math.round(donGia * 1.03);
        long giaChon = gia2;
        long thanhTienChon = giaChon * soLuong;
        LocalDate today = LocalDate.now();
        String[] uuTien = {"Cao", "Trung bình", "Thấp"};
        String[] nguon = {"Ngân sách vận hành", "Ngân sách đầu tư", "Ngoài ngân sách"};

        Map<String, Object> m = new LinkedHashMap<>();
        // Bước 1
        m.put("tieu_de", tieuDe);
        m.put("hang_muc", hangMuc);
        m.put("so_luong", soLuong);
        m.put("don_gia", donGia);
        m.put("thanh_tien", donGia * soLuong);
        m.put("ly_do", "Phục vụ nhu cầu vận hành/đầu tư của đơn vị theo kế hoạch năm 2026.");
        m.put("can_truoc_ngay", today.plusDays(21).toString());
        // Bước 2
        m.put("muc_do_uu_tien", uuTien[seq % uuTien.length]);
        m.put("y_kien_tbp", "Nhu cầu hợp lý, đề nghị Phòng Mua hàng tiến hành khảo giá.");
        // Bước 3
        m.put("ncc_1", "Công ty TNHH Thương mại An Phát");
        m.put("gia_1", gia1);
        m.put("ncc_2", "Công ty CP Giải pháp Minh Long");
        m.put("gia_2", gia2);
        m.put("ncc_3", "DNTN Dịch vụ Hoàng Gia");
        m.put("gia_3", gia3);
        m.put("ghi_chu_khao_gia", "Đã thu thập 03 báo giá từ các nhà cung cấp uy tín, còn hiệu lực.");
        // Bước 4
        m.put("ncc_chon", "Công ty CP Giải pháp Minh Long");
        m.put("gia_chon", giaChon);
        m.put("ly_do_chon", "Giá tốt nhất, đáp ứng yêu cầu kỹ thuật và cam kết tiến độ giao hàng.");
        // Bước 5
        m.put("nguon_ngan_sach", nguon[seq % nguon.length]);
        m.put("han_muc_duyet", thanhTienChon);
        m.put("y_kien_gd", "Đồng ý phê duyệt trong hạn mức ngân sách đã bố trí.");
        // Bước 6
        m.put("so_po", "PO-2026-" + String.format("%04d", 100 + seq));
        m.put("ngay_po", today.toString());
        m.put("gia_tri_po", thanhTienChon);
        m.put("dieu_khoan_tt", "Thanh toán 100% sau nghiệm thu, trong vòng 15 ngày làm việc.");
        // Bước 7
        m.put("ngay_nhan", today.plusDays(7).toString());
        m.put("so_luong_nhan", soLuong);
        m.put("tinh_trang_hang", "Đạt");
        m.put("ghi_chu_kiem_tra", "Hàng đủ số lượng, đúng chủng loại, chất lượng đạt yêu cầu.");
        // Bước 8
        m.put("ket_qua_nghiem_thu", "Đạt");
        m.put("ngay_nghiem_thu", today.plusDays(8).toString());
        m.put("y_kien_nghiem_thu", "Nghiệm thu đạt yêu cầu, đồng ý chuyển kế toán thanh toán.");
        // Bước 9
        m.put("so_chung_tu", "UNC-2026-" + String.format("%04d", 500 + seq));
        m.put("ngay_thanh_toan", today.plusDays(10).toString());
        m.put("so_tien_thanh_toan", thanhTienChon);
        m.put("hinh_thuc_thanh_toan", "Chuyển khoản");
        // Bước 10
        m.put("so_luu_tru", "HS-MS-2026-" + String.format("%03d", seq));
        m.put("ngay_dong_ho_so", today.plusDays(12).toString());
        m.put("ghi_chu_luu_tru", "Hồ sơ đầy đủ chứng từ, đã lưu trữ theo quy định.");
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

    /** Schema biểu mẫu RIÊNG của một bước (JSON) — chỉ các trường thông tin của đúng bước đó. */
    private static String buildStepFormSchema(int step) {
        StringBuilder sb = new StringBuilder("{\"fields\":[");
        boolean first = true;
        for (F f : FIELDS) {
            if (f.owner() != step) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"key\":\"").append(f.key()).append("\",\"label\":\"").append(js(f.label()))
                    .append("\",\"type\":\"").append(f.type()).append('"');
            if (f.required()) {
                sb.append(",\"required\":true");
            }
            if (f.options() != null) {
                sb.append(",\"optionSource\":\"STATIC\",\"options\":\"").append(js(f.options())).append('"');
            }
            sb.append('}');
        }
        return sb.append("]}").toString();
    }

    /** stepsMeta JSON: mỗi bước gán USER + SLA + hành động + biểu mẫu + quyền TỪNG trường (theo bước sở hữu). */
    private String buildStepsMeta(String[] taskIds, String[] userIds, String[] formIds) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < taskIds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(taskIds[i]).append("\":{")
                    .append("\"assigneeType\":\"USER\",")
                    .append("\"assigneeId\":\"").append(userIds[i]).append("\",")
                    .append("\"slaHours\":").append(STEP_SLA[i]).append(',')
                    .append("\"formId\":\"").append(formIds[i]).append("\",")
                    .append("\"actions\":").append(jsonArray(STEP_ACTIONS[i]))
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

    // ===================== BPMN (sinh tuyến tính N bước, kèm DI) =====================

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
        proc.append("<bpmn:sequenceFlow id=\"Flow_0\" sourceRef=\"StartEvent_1\" targetRef=\"").append(taskIds[0]).append("\" />");
        for (int i = 1; i < n; i++) {
            proc.append("<bpmn:sequenceFlow id=\"Flow_").append(i).append("\" sourceRef=\"").append(taskIds[i - 1])
                    .append("\" targetRef=\"").append(taskIds[i]).append("\" />");
        }
        proc.append("<bpmn:sequenceFlow id=\"Flow_").append(n).append("\" sourceRef=\"").append(taskIds[n - 1])
                .append("\" targetRef=\"EndEvent_1\" />");

        int y = 200, taskW = 120, taskH = 80, gap = 40, startX = 152;
        int firstTaskX = startX + 60;
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

    /** Thoát ký tự JSON cho label/options. */
    private static String js(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
