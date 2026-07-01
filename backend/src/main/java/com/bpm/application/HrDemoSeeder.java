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
 * Seed dữ liệu DEMO cho các QUY TRÌNH NHÂN SỰ trên dữ liệu THẬT (108 nhân sự đã import).
 * Kích hoạt thủ công bởi ADMIN từ màn "Xoá dữ liệu hệ thống" (POST /api/v1/system/seed-hr-demo).
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>CHỈ THÊM — không xoá/đụng dữ liệu thật (nhân sự / tài khoản / cơ cấu / vai trò).</li>
 *   <li>Idempotent: nếu đã có quy trình theo key (vd "nghi-phep-hr") thì BỎ QUA, không nhân đôi.</li>
 *   <li>Người khởi tạo / người duyệt là NHÂN SỰ THẬT (ưu tiên có tài khoản + đúng vai trò theo
 *       Chức danh: "Giám đốc"→GIAM_DOC duyệt cấp cao, "Trưởng Nhóm"→TRUONG_NHOM duyệt cấp 1,
 *       còn lại là Nhân viên nộp đơn).</li>
 *   <li>Bước được gán trực tiếp tới TÀI KHOẢN nhân sự (assigneeType=USER, assigneeId=userAccountId)
 *       để Việc-của-tôi / Theo-dõi / Dashboard có người thật chịu trách nhiệm.</li>
 *   <li>Hồ sơ rải đủ trạng thái: đang soạn / chờ duyệt cấp 1 / chờ duyệt GĐ / quá hạn / hoàn thành.
 *       "Quá hạn" mô phỏng bằng cách lùi đồng hồ Flowable (như DemoSeeder).</li>
 * </ul>
 */
@Service
public class HrDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(HrDemoSeeder.class);

    /** Khoá nhận diện đã-seed (idempotent). */
    private static final String GUARD_KEY = "nghi-phep-hr";

    /** Role code suy ra từ Chức danh (xem EmployeeService.toRoleCode). */
    private static final String ROLE_GIAM_DOC = "GIAM_DOC";
    private static final String ROLE_TRUONG_NHOM = "TRUONG_NHOM";

    private final ProcessService processService;
    private final FormService formService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final ProcessEngine processEngine;
    private final ProcessDefinitionRepository processRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final RoleService roleService;
    private final AuditPort auditPort;

    public HrDemoSeeder(ProcessService processService, FormService formService, WorkflowService workflowService,
                        TaskService taskService, ProcessEngine processEngine, ProcessDefinitionRepository processRepo,
                        EmployeeRepository employeeRepo, UserAccountRepository userRepo, RoleService roleService,
                        AuditPort auditPort) {
        this.processService = processService;
        this.formService = formService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.processEngine = processEngine;
        this.processRepo = processRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.roleService = roleService;
        this.auditPort = auditPort;
    }

    /** Tóm tắt kết quả seed (trả về cho FE). */
    public record SeedResult(boolean seeded, int processes, int forms, int instances, String message) {
    }

    /**
     * Tạo 4 quy trình nhân sự + biểu mẫu + nhiều hồ sơ đủ trạng thái. Idempotent.
     * @param actor người bấm nút (admin) — dùng để ghi audit.
     */
    public SeedResult seed(String actor) {
        if (processRepo.existsByProcessKey(GUARD_KEY)) {
            log.info("[HrDemoSeeder] Đã có quy trình nhân sự demo (key={}), bỏ qua.", GUARD_KEY);
            auditPort.record("HR_DEMO_SEED_SKIPPED", "System", null, actor, "Đã tồn tại — không seed lại.");
            return new SeedResult(false, 0, 0, 0,
                    "Đã có dữ liệu quy trình nhân sự demo từ trước — bỏ qua (không nhân đôi).");
        }

        // ===== Chọn NHÂN SỰ THẬT theo vai trò (chỉ người đang làm việc + có tài khoản) =====
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();

        List<Employee> directors = byRole(active, ROLE_GIAM_DOC);
        List<Employee> leaders = byRole(active, ROLE_TRUONG_NHOM);
        // Người nộp đơn: ai không phải GĐ (nhân viên/chuyên viên/trưởng nhóm đều có thể nộp).
        List<Employee> requesters = active.stream()
                .filter(e -> !directors.contains(e))
                .toList();

        if (active.isEmpty()) {
            return new SeedResult(false, 0, 0, 0,
                    "Chưa có nhân sự (đang làm việc + có tài khoản) để gán người thực hiện. "
                            + "Hãy import nhân sự trước.");
        }
        // Fallback an toàn nếu thiếu vai trò nào đó: dùng người bất kỳ trong nhóm còn lại.
        Picker director = new Picker(directors.isEmpty() ? active : directors);
        Picker leader = new Picker(leaders.isEmpty() ? active : leaders);
        Picker requester = new Picker(requesters.isEmpty() ? active : requesters);

        log.info("[HrDemoSeeder] Nhân sự thật: {} active (GĐ={}, TrNhóm={}, người nộp={})",
                active.size(), directors.size(), leaders.size(), requesters.size());

        // ===================== BIỂU MẪU =====================
        String fNghiPhep = form("don-nghi-phep-hr", "Đơn xin nghỉ phép (Nhân sự)", """
                {"fields": [
                  {"key": "loai_nghi", "label": "Loại nghỉ", "type": "dropdown", "optionSource": "STATIC", "options": "Nghỉ phép năm, Nghỉ ốm, Nghỉ việc riêng, Nghỉ thai sản, Nghỉ không lương", "required": true},
                  {"key": "tu_ngay", "label": "Từ ngày", "type": "datetime", "required": true},
                  {"key": "den_ngay", "label": "Đến ngày", "type": "datetime", "required": true},
                  {"key": "so_ngay", "label": "Số ngày nghỉ", "type": "number", "required": true, "validation": {"min": 1}},
                  {"key": "ly_do", "label": "Lý do", "type": "text", "required": true},
                  {"key": "nguoi_ban_giao", "label": "Người bàn giao công việc", "type": "text"}
                ]}""");
        String fKhenThuong = form("de-xuat-khen-thuong-hr", "Phiếu đề xuất khen thưởng", """
                {"fields": [
                  {"key": "nhan_su", "label": "Nhân sự được khen thưởng", "type": "text", "required": true},
                  {"key": "bo_phan", "label": "Bộ phận", "type": "text"},
                  {"key": "hinh_thuc", "label": "Hình thức khen thưởng", "type": "dropdown", "optionSource": "STATIC", "options": "Giấy khen, Tiền thưởng, Bằng khen, Tăng lương, Thăng chức", "required": true},
                  {"key": "muc_thuong", "label": "Mức thưởng (VNĐ)", "type": "number"},
                  {"key": "thanh_tich", "label": "Thành tích / lý do đề xuất", "type": "richtext", "required": true},
                  {"key": "thoi_diem", "label": "Thời điểm áp dụng", "type": "datetime"}
                ]}""");
        String fTuyenDung = form("de-nghi-tuyen-dung-hr", "Phiếu đề nghị tuyển dụng", """
                {"fields": [
                  {"key": "vi_tri", "label": "Vị trí cần tuyển", "type": "text", "required": true},
                  {"key": "so_luong", "label": "Số lượng", "type": "number", "required": true, "validation": {"min": 1}},
                  {"key": "loai_hinh", "label": "Loại hình", "type": "dropdown", "optionSource": "STATIC", "options": "Toàn thời gian, Bán thời gian, Thực tập, Thời vụ", "required": true},
                  {"key": "mo_ta", "label": "Mô tả công việc / yêu cầu", "type": "richtext", "required": true},
                  {"key": "muc_luong", "label": "Mức lương dự kiến (VNĐ)", "type": "number"},
                  {"key": "thoi_gian_can", "label": "Thời gian cần người", "type": "datetime"},
                  {"key": "ly_do", "label": "Lý do tuyển dụng", "type": "text"}
                ]}""");
        String fDanhGia = form("danh-gia-nhan-vien-hr", "Phiếu đánh giá nhân viên định kỳ", """
                {"fields": [
                  {"key": "nhan_su", "label": "Nhân sự được đánh giá", "type": "text", "required": true},
                  {"key": "ky_danh_gia", "label": "Kỳ đánh giá", "type": "dropdown", "optionSource": "STATIC", "options": "Quý I, Quý II, Quý III, Quý IV, Cả năm", "required": true},
                  {"key": "muc_tieu", "label": "Mức độ hoàn thành mục tiêu (%)", "type": "number", "validation": {"min": 0, "max": 100}},
                  {"key": "uu_diem", "label": "Ưu điểm", "type": "richtext"},
                  {"key": "can_cai_thien", "label": "Điểm cần cải thiện", "type": "richtext"},
                  {"key": "xep_loai", "label": "Đề xuất xếp loại", "type": "dropdown", "optionSource": "STATIC", "options": "Xuất sắc, Tốt, Khá, Đạt, Cần cố gắng", "required": true}
                ]}""");

        // ===================== QUY TRÌNH =====================
        // 1) Nghỉ phép: NV nộp → Trưởng nhóm duyệt → HCNS (Giám đốc) ghi nhận.
        String metaNghi = """
                {
                  "Task_Don": {"assigneeType":"USER","assigneeId":"%s","slaHours":4,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"loai_nghi":"EDIT","tu_ngay":"EDIT","den_ngay":"EDIT","so_ngay":"EDIT","ly_do":"EDIT","nguoi_ban_giao":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"USER","assigneeId":"%s","slaHours":16,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"loai_nghi":"READONLY","tu_ngay":"READONLY","den_ngay":"READONLY","so_ngay":"READONLY","ly_do":"READONLY","nguoi_ban_giao":"READONLY"},
                    "fields":[{"key":"y_kien_ql","label":"Ý kiến quản lý","type":"text"}]},
                  "Task_GhiNhan": {"assigneeType":"USER","assigneeId":"%s","slaHours":24,"actions":["RECORD"],"formId":"%s",
                    "fieldPerms":{"loai_nghi":"READONLY","tu_ngay":"READONLY","den_ngay":"READONLY","so_ngay":"READONLY","ly_do":"READONLY","nguoi_ban_giao":"READONLY"}}
                }""".formatted(pick(requester), fNghiPhep, pick(leader), fNghiPhep, pick(director), fNghiPhep);
        ProcessDefinition pNghi = publish(GUARD_KEY, "Quy trình xin nghỉ phép (Nhân sự)", BPMN_3STEP("nghiphep",
                "Làm đơn nghỉ phép", "Quản lý duyệt", "HCNS ghi nhận"), metaNghi);

        // 2) Khen thưởng: Quản lý đề xuất → Giám đốc phê duyệt → HCNS thực hiện.
        String metaKhen = """
                {
                  "Task_Don": {"assigneeType":"USER","assigneeId":"%s","slaHours":8,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"nhan_su":"EDIT","bo_phan":"EDIT","hinh_thuc":"EDIT","muc_thuong":"EDIT","thanh_tich":"EDIT","thoi_diem":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"USER","assigneeId":"%s","slaHours":24,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"nhan_su":"READONLY","bo_phan":"READONLY","hinh_thuc":"READONLY","muc_thuong":"READONLY","thanh_tich":"READONLY","thoi_diem":"READONLY"},
                    "fields":[{"key":"y_kien_gd","label":"Ý kiến Giám đốc","type":"text"}]},
                  "Task_GhiNhan": {"assigneeType":"USER","assigneeId":"%s","slaHours":24,"actions":["RECORD"],"formId":"%s",
                    "fieldPerms":{"nhan_su":"READONLY","bo_phan":"READONLY","hinh_thuc":"READONLY","muc_thuong":"READONLY","thanh_tich":"READONLY","thoi_diem":"READONLY"}}
                }""".formatted(pick(leader), fKhenThuong, pick(director), fKhenThuong, pick(director), fKhenThuong);
        ProcessDefinition pKhen = publish("khen-thuong-hr", "Quy trình đề xuất khen thưởng", BPMN_3STEP("khenthuong",
                "Đề xuất khen thưởng", "Giám đốc phê duyệt", "HCNS thực hiện"), metaKhen);

        // 3) Đề nghị tuyển dụng: Phòng đề nghị → Giám đốc duyệt → HCNS đăng tuyển.
        String metaTuyen = """
                {
                  "Task_Don": {"assigneeType":"USER","assigneeId":"%s","slaHours":8,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"vi_tri":"EDIT","so_luong":"EDIT","loai_hinh":"EDIT","mo_ta":"EDIT","muc_luong":"EDIT","thoi_gian_can":"EDIT","ly_do":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"USER","assigneeId":"%s","slaHours":24,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"vi_tri":"READONLY","so_luong":"READONLY","loai_hinh":"READONLY","mo_ta":"READONLY","muc_luong":"READONLY","thoi_gian_can":"READONLY","ly_do":"READONLY"},
                    "fields":[{"key":"y_kien_gd","label":"Ý kiến phê duyệt","type":"text"}]},
                  "Task_GhiNhan": {"assigneeType":"USER","assigneeId":"%s","slaHours":48,"actions":["RECORD"],"formId":"%s",
                    "fieldPerms":{"vi_tri":"READONLY","so_luong":"READONLY","loai_hinh":"READONLY","mo_ta":"READONLY","muc_luong":"READONLY","thoi_gian_can":"READONLY","ly_do":"READONLY"}}
                }""".formatted(pick(leader), fTuyenDung, pick(director), fTuyenDung, pick(director), fTuyenDung);
        ProcessDefinition pTuyen = publish("de-nghi-tuyen-dung-hr", "Quy trình đề nghị tuyển dụng", BPMN_3STEP("tuyendung",
                "Lập đề nghị tuyển dụng", "Giám đốc duyệt", "HCNS đăng tuyển"), metaTuyen);

        // 4) Đánh giá nhân viên định kỳ: Quản lý đánh giá → Giám đốc duyệt.
        String metaDanhGia = """
                {
                  "Task_Don": {"assigneeType":"USER","assigneeId":"%s","slaHours":8,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"nhan_su":"EDIT","ky_danh_gia":"EDIT","muc_tieu":"EDIT","uu_diem":"EDIT","can_cai_thien":"EDIT","xep_loai":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"USER","assigneeId":"%s","slaHours":24,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"nhan_su":"READONLY","ky_danh_gia":"READONLY","muc_tieu":"READONLY","uu_diem":"READONLY","can_cai_thien":"READONLY","xep_loai":"READONLY"}}
                }""".formatted(pick(leader), fDanhGia, pick(director), fDanhGia);
        ProcessDefinition pDanhGia = publish("danh-gia-nhan-vien-hr", "Quy trình đánh giá nhân viên định kỳ",
                BPMN_2STEP("danhgia", "Lập phiếu đánh giá", "Giám đốc duyệt"), metaDanhGia);

        // ===================== HỒ SƠ ĐANG CHẠY (đủ trạng thái) =====================
        AtomicInteger created = new AtomicInteger(0);
        try {
            seedRunningInstances(pNghi, pKhen, pTuyen, pDanhGia, requester, leader, director, created);
        } catch (Exception e) {
            log.warn("[HrDemoSeeder] Seed hồ sơ chạy gặp lỗi (bỏ qua, cấu hình quy trình vẫn đủ): {}", e.toString());
        }

        int processes = 4, forms = 4, instances = created.get();
        auditPort.record("HR_DEMO_SEEDED", "System", null, actor,
                "processes=" + processes + ", forms=" + forms + ", instances=" + instances);
        log.info("[HrDemoSeeder] Seed quy trình nhân sự: {} quy trình, {} biểu mẫu, {} hồ sơ.",
                processes, forms, instances);
        return new SeedResult(true, processes, forms, instances,
                "Đã tạo " + processes + " quy trình nhân sự, " + forms + " biểu mẫu và "
                        + instances + " hồ sơ demo (đủ trạng thái).");
    }

    /**
     * Khởi tạo nhiều hồ sơ rải đủ trạng thái cho cả 4 quy trình.
     * Mỗi quy trình 3 bước (nghỉ phép/khen thưởng/tuyển dụng) hoặc 2 bước (đánh giá):
     * SUBMIT → (chờ duyệt) → APPROVE/RECORD → ... → hoàn thành.
     */
    private void seedRunningInstances(ProcessDefinition pNghi, ProcessDefinition pKhen, ProcessDefinition pTuyen,
                                      ProcessDefinition pDanhGia, Picker requester, Picker leader, Picker director,
                                      AtomicInteger created) {
        // ---------- 1) NGHỈ PHÉP (3 bước: Đơn → Duyệt → Ghi nhận) ----------
        String[] lyDoNghi = {"Về quê giải quyết việc gia đình", "Khám và điều trị sức khỏe định kỳ",
                "Nghỉ chăm con nhỏ ốm", "Du lịch cùng gia đình", "Giải quyết thủ tục cá nhân"};
        String[] loaiNghi = {"Nghỉ phép năm", "Nghỉ ốm", "Nghỉ việc riêng", "Nghỉ phép năm", "Nghỉ không lương"};
        // đang soạn
        start(pNghi, npData(loaiNghi[0], 2, lyDoNghi[0], req(requester)), req(requester), created);
        // chờ duyệt cấp 1 (×2)
        start(pNghi, npData(loaiNghi[1], 1, lyDoNghi[1], req(requester)), req(requester), created, "SUBMIT", npNote());
        start(pNghi, npData(loaiNghi[2], 3, lyDoNghi[2], req(requester)), req(requester), created, "SUBMIT", npNote());
        // chờ HCNS ghi nhận (đã qua duyệt cấp 1)
        start(pNghi, npData(loaiNghi[3], 5, lyDoNghi[3], req(requester)), req(requester), created,
                "SUBMIT", npNote(), "APPROVE", Map.of("y_kien_ql", "Đồng ý, đã sắp xếp người thay thế."));
        // hoàn thành
        start(pNghi, npData(loaiNghi[0], 2, lyDoNghi[4], req(requester)), req(requester), created,
                "SUBMIT", npNote(), "APPROVE", Map.of("y_kien_ql", "Duyệt."), "RECORD", Map.of());
        // QUÁ HẠN ở bước Duyệt (sla 16h, lùi 30h)
        startBackdated(pNghi, 30, npData(loaiNghi[1], 1, "Nghỉ ốm đột xuất", req(requester)), req(requester), created,
                "SUBMIT", npNote());

        // ---------- 2) KHEN THƯỞNG (3 bước: Đề xuất → Duyệt GĐ → Thực hiện) ----------
        // đang soạn
        start(pKhen, ktData(req(requester), "Phòng Kinh doanh", "Tiền thưởng", 5000000,
                "Vượt 150% chỉ tiêu doanh số Quý II/2026."), req(leader), created);
        // chờ Giám đốc duyệt (×2)
        start(pKhen, ktData(req(requester), "Phòng Kỹ thuật", "Giấy khen", 2000000,
                "Khắc phục sự cố hệ thống ngoài giờ, đảm bảo dịch vụ liên tục."), req(leader), created, "SUBMIT", Map.of());
        start(pKhen, ktData(req(requester), "Phòng HCNS", "Bằng khen", 3000000,
                "Sáng kiến cải tiến quy trình tuyển dụng, giảm 30% thời gian."), req(leader), created, "SUBMIT", Map.of());
        // chờ HCNS thực hiện
        start(pKhen, ktData(req(requester), "Phòng Kinh doanh", "Tăng lương", 0,
                "Thành tích xuất sắc liên tục 3 quý."), req(leader), created,
                "SUBMIT", Map.of(), "APPROVE", Map.of("y_kien_gd", "Đồng ý khen thưởng."));
        // hoàn thành
        start(pKhen, ktData(req(requester), "Phòng Kỹ thuật", "Tiền thưởng", 4000000,
                "Hoàn thành dự án trọng điểm trước hạn."), req(leader), created,
                "SUBMIT", Map.of(), "APPROVE", Map.of("y_kien_gd", "Phê duyệt."), "RECORD", Map.of());

        // ---------- 3) ĐỀ NGHỊ TUYỂN DỤNG (3 bước: Đề nghị → Duyệt GĐ → Đăng tuyển) ----------
        // đang soạn
        start(pTuyen, tdData("Lập trình viên Java", 3, "Toàn thời gian",
                "Phát triển hệ thống BPM, kinh nghiệm 2+ năm Spring Boot.", 25000000, "Mở rộng đội phát triển sản phẩm."),
                req(leader), created);
        // chờ Giám đốc duyệt (×2)
        start(pTuyen, tdData("Chuyên viên Tuyển dụng", 1, "Toàn thời gian",
                "Phụ trách tuyển dụng mảng kỹ thuật.", 15000000, "Bổ sung nhân sự HCNS."),
                req(leader), created, "SUBMIT", Map.of());
        start(pTuyen, tdData("Nhân viên Kinh doanh", 5, "Toàn thời gian",
                "Phát triển khách hàng doanh nghiệp khu vực miền Bắc.", 12000000, "Mở rộng thị trường."),
                req(leader), created, "SUBMIT", Map.of());
        // chờ HCNS đăng tuyển
        start(pTuyen, tdData("Thực tập sinh Kỹ thuật", 2, "Thực tập",
                "Hỗ trợ kiểm thử và tài liệu kỹ thuật.", 5000000, "Chương trình thực tập sinh."),
                req(leader), created, "SUBMIT", Map.of(), "APPROVE", Map.of("y_kien_gd", "Duyệt tuyển 2 thực tập sinh."));
        // hoàn thành
        start(pTuyen, tdData("Kế toán tổng hợp", 1, "Toàn thời gian",
                "Phụ trách báo cáo tài chính, kinh nghiệm 3+ năm.", 18000000, "Thay thế nhân sự nghỉ việc."),
                req(leader), created, "SUBMIT", Map.of(), "APPROVE", Map.of("y_kien_gd", "Đồng ý."), "RECORD", Map.of());
        // QUÁ HẠN ở bước Duyệt GĐ (sla 24h, lùi 40h)
        startBackdated(pTuyen, 40, tdData("Quản trị hệ thống", 1, "Toàn thời gian",
                "Vận hành hạ tầng máy chủ, mạng nội bộ.", 20000000, "Tăng cường vận hành."),
                req(leader), created, "SUBMIT", Map.of());

        // ---------- 4) ĐÁNH GIÁ NHÂN VIÊN (2 bước: Lập phiếu → Duyệt) ----------
        // chờ duyệt
        start(pDanhGia, dgData(req(requester), "Quý II", 95, "Chủ động, hoàn thành tốt mọi nhiệm vụ.",
                "Cần nâng cao kỹ năng trình bày.", "Tốt"), req(leader), created, "SUBMIT", Map.of());
        // hoàn thành
        start(pDanhGia, dgData(req(requester), "Quý II", 110, "Vượt chỉ tiêu, hỗ trợ đồng đội tốt.",
                "—", "Xuất sắc"), req(leader), created, "SUBMIT", Map.of(), "APPROVE",
                Map.of("y_kien_gd", "Đồng ý xếp loại Xuất sắc."));
        // QUÁ HẠN ở bước Duyệt (sla 24h, lùi 36h)
        startBackdated(pDanhGia, 36, dgData(req(requester), "Quý I", 80, "Đạt yêu cầu công việc.",
                "Cần cải thiện đúng hạn.", "Khá"), req(leader), created, "SUBMIT", Map.of());
    }

    // ===================== Helpers chọn người =====================

    private List<Employee> byRole(List<Employee> active, String roleCode) {
        List<Employee> out = new ArrayList<>();
        for (Employee e : active) {
            if (roleService.roleCodesForUser(e.getUserAccountId()).contains(roleCode)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Vòng tròn chọn người trong một nhóm (để rải hồ sơ qua nhiều người thật). */
    private static final class Picker {
        private final List<Employee> list;
        private int i = 0;
        Picker(List<Employee> list) { this.list = list; }
        Employee next() { return list.get((i++) % list.size()); }
    }

    /** Trả userAccountId của người kế tiếp trong nhóm (dùng gán bước). */
    private String pick(Picker p) { return p.next().getUserAccountId(); }

    /** Trả USERNAME (mã NV) của người kế tiếp — dùng làm actor khởi tạo hồ sơ (để "Hồ sơ của tôi" đúng). */
    private String req(Picker p) {
        return username(p.next());
    }

    private String username(Employee e) {
        return userRepo.findById(e.getUserAccountId()).map(UserAccount::getUsername).orElse("system");
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

    /** Khởi tạo hồ sơ (actor=username người nộp) rồi hoàn thành lần lượt các bước (action, data). */
    @SuppressWarnings("unchecked")
    private WorkflowInstance start(ProcessDefinition p, Map<String, Object> initial, String submitterUsername,
                                   AtomicInteger created, Object... stepActions) {
        WorkflowInstance wi = workflowService.start(p.getId(), initial, submitterUsername);
        created.incrementAndGet();
        for (int i = 0; i + 1 < stepActions.length; i += 2) {
            completeActive(wi, (String) stepActions[i], (Map<String, Object>) stepActions[i + 1]);
        }
        return wi;
    }

    /** Như start() nhưng lùi đồng hồ engine để việc được tạo "trong quá khứ" (mô phỏng quá hạn). */
    @SuppressWarnings("unchecked")
    private WorkflowInstance startBackdated(ProcessDefinition p, int hoursAgo, Map<String, Object> initial,
                                            String submitterUsername, AtomicInteger created, Object... stepActions) {
        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(hoursAgo))));
        try {
            WorkflowInstance wi = workflowService.start(p.getId(), initial, submitterUsername);
            created.incrementAndGet();
            for (int i = 0; i + 1 < stepActions.length; i += 2) {
                completeActive(wi, (String) stepActions[i], (Map<String, Object>) stepActions[i + 1]);
            }
            return wi;
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

    // ===================== Dữ liệu form =====================

    private static Map<String, Object> npData(String loai, int soNgay, String lyDo, String banGiao) {
        LocalDate from = LocalDate.now().plusDays(7);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loai_nghi", loai);
        m.put("tu_ngay", from + "T08:00");
        m.put("den_ngay", from.plusDays(Math.max(0, soNgay - 1)) + "T17:00");
        m.put("so_ngay", soNgay);
        m.put("ly_do", lyDo);
        m.put("nguoi_ban_giao", banGiao);
        return m;
    }

    private static Map<String, Object> npNote() {
        return Map.of("y_kien_ql", "Đã xem, đồng ý phương án bàn giao.");
    }

    private static Map<String, Object> ktData(String nhanSu, String boPhan, String hinhThuc, long mucThuong, String thanhTich) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nhan_su", nhanSu);
        m.put("bo_phan", boPhan);
        m.put("hinh_thuc", hinhThuc);
        m.put("muc_thuong", mucThuong);
        m.put("thanh_tich", thanhTich);
        m.put("thoi_diem", LocalDate.now().withDayOfMonth(1).plusMonths(1) + "T08:00");
        return m;
    }

    private static Map<String, Object> tdData(String viTri, int soLuong, String loaiHinh, String moTa,
                                              long mucLuong, String lyDo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vi_tri", viTri);
        m.put("so_luong", soLuong);
        m.put("loai_hinh", loaiHinh);
        m.put("mo_ta", moTa);
        m.put("muc_luong", mucLuong);
        m.put("thoi_gian_can", LocalDate.now().plusMonths(1) + "T08:00");
        m.put("ly_do", lyDo);
        return m;
    }

    private static Map<String, Object> dgData(String nhanSu, String ky, int mucTieu, String uuDiem,
                                              String canCaiThien, String xepLoai) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nhan_su", nhanSu);
        m.put("ky_danh_gia", ky);
        m.put("muc_tieu", mucTieu);
        m.put("uu_diem", uuDiem);
        m.put("can_cai_thien", canCaiThien);
        m.put("xep_loai", xepLoai);
        return m;
    }

    // ===================== BPMN (sinh động theo số bước) =====================

    /** Sơ đồ 2 bước: Bắt đầu → Task_Don → Task_Duyet → Kết thúc. */
    private static String BPMN_2STEP(String pid, String n1, String n2) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_%1$s" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="Process_%1$s" name="%2$s" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="Bắt đầu"><bpmn:outgoing>Flow_s</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:userTask id="Task_Don" name="%2$s"><bpmn:incoming>Flow_s</bpmn:incoming><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:userTask>
                    <bpmn:userTask id="Task_Duyet" name="%3$s"><bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>
                    <bpmn:endEvent id="EndEvent_1" name="Hoàn thành"><bpmn:incoming>Flow_2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_s" sourceRef="StartEvent_1" targetRef="Task_Don" />
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="Task_Don" targetRef="Task_Duyet" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Duyet" targetRef="EndEvent_1" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_%1$s">
                    <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Task_Don_di" bpmnElement="Task_Don"><dc:Bounds x="240" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Task_Duyet_di" bpmnElement="Task_Duyet"><dc:Bounds x="400" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="562" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNEdge id="Flow_s_di" bpmnElement="Flow_s"><di:waypoint x="188" y="200" /><di:waypoint x="240" y="200" /></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="350" y="200" /><di:waypoint x="400" y="200" /></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="510" y="200" /><di:waypoint x="562" y="200" /></bpmndi:BPMNEdge>
                  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
                </bpmn:definitions>""".formatted(pid, n1, n2);
    }

    /** Sơ đồ 3 bước: Bắt đầu → Task_Don → Task_Duyet → Task_GhiNhan → Kết thúc. */
    private static String BPMN_3STEP(String pid, String n1, String n2, String n3) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_%1$s" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="Process_%1$s" name="%2$s" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="Bắt đầu"><bpmn:outgoing>Flow_s</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:userTask id="Task_Don" name="%2$s"><bpmn:incoming>Flow_s</bpmn:incoming><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:userTask>
                    <bpmn:userTask id="Task_Duyet" name="%3$s"><bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>
                    <bpmn:userTask id="Task_GhiNhan" name="%4$s"><bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_3</bpmn:outgoing></bpmn:userTask>
                    <bpmn:endEvent id="EndEvent_1" name="Hoàn thành"><bpmn:incoming>Flow_3</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_s" sourceRef="StartEvent_1" targetRef="Task_Don" />
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="Task_Don" targetRef="Task_Duyet" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Duyet" targetRef="Task_GhiNhan" />
                    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_GhiNhan" targetRef="EndEvent_1" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_%1$s">
                    <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Task_Don_di" bpmnElement="Task_Don"><dc:Bounds x="240" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Task_Duyet_di" bpmnElement="Task_Duyet"><dc:Bounds x="400" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Task_GhiNhan_di" bpmnElement="Task_GhiNhan"><dc:Bounds x="560" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="722" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                    <bpmndi:BPMNEdge id="Flow_s_di" bpmnElement="Flow_s"><di:waypoint x="188" y="200" /><di:waypoint x="240" y="200" /></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="350" y="200" /><di:waypoint x="400" y="200" /></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="510" y="200" /><di:waypoint x="560" y="200" /></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="670" y="200" /><di:waypoint x="722" y="200" /></bpmndi:BPMNEdge>
                  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
                </bpmn:definitions>""".formatted(pid, n1, n2, n3);
    }
}
