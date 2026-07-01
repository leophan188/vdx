package com.bpm.infrastructure;

import com.bpm.application.FormService;
import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.ProcessService;
import com.bpm.application.RoleService;
import com.bpm.application.UserAccountService;
import com.bpm.application.WorkflowService;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.ot.OtEntry;
import com.bpm.domain.social.Post;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.workflow.WorkflowInstance;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seed dữ liệu DEMO THỰC TẾ (profile 'local', H2 create-drop): một doanh nghiệp ~13 nhân sự / 5 phòng ban,
 * 3 quy trình nghiệp vụ (Trình ký văn bản — có rẽ nhánh, Đề nghị mua sắm — 4 cấp, Nghỉ phép) và ~10 hồ sơ
 * đang chạy ở nhiều trạng thái (đang soạn / chờ duyệt / chờ ký / quá hạn / đã hoàn thành). Idempotent.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "bpm", name = "seed-demo", havingValue = "true") // mặc định TẮT — chỉ seed khi BPM_SEED_DEMO=true
@Order(10) // chạy sau AdminSeeder
public class DemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final String GUARD_KEY = "trinh-ky-van-ban";

    private final OrgUnitService orgService;
    private final UserAccountService userService;
    private final PositionService positionService;
    private final RoleService roleService;
    private final ProcessService processService;
    private final FormService formService;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final ProcessEngine processEngine;
    private final ProcessDefinitionRepository processRepo;
    private final PostRepository postRepo;
    private final OtEntryRepository otEntryRepo;
    private final EmployeeRepository employeeRepo;

    public DemoSeeder(OrgUnitService orgService, UserAccountService userService, PositionService positionService,
                      RoleService roleService, ProcessService processService, FormService formService,
                      WorkflowService workflowService, TaskService taskService, ProcessEngine processEngine,
                      ProcessDefinitionRepository processRepo, PostRepository postRepo, OtEntryRepository otEntryRepo,
                      EmployeeRepository employeeRepo) {
        this.orgService = orgService;
        this.userService = userService;
        this.positionService = positionService;
        this.roleService = roleService;
        this.processService = processService;
        this.formService = formService;
        this.workflowService = workflowService;
        this.taskService = taskService;
        this.processEngine = processEngine;
        this.processRepo = processRepo;
        this.postRepo = postRepo;
        this.otEntryRepo = otEntryRepo;
        this.employeeRepo = employeeRepo;
    }

    @Override
    public void run(String... args) {
        if (processRepo.existsByProcessKey(GUARD_KEY)) {
            return;
        }

        // ===================== 1) CƠ CẤU TỔ CHỨC (2 cấp) =====================
        OrgUnit cty = orgService.create("Công ty CP Giải pháp ONEConnect", null, "system");
        OrgUnit bgd = orgService.create("Ban Giám đốc", cty.getId(), "system");
        OrgUnit hcns = orgService.create("Phòng Hành chính - Nhân sự", cty.getId(), "system");
        OrgUnit tckt = orgService.create("Phòng Tài chính - Kế toán", cty.getId(), "system");
        OrgUnit kd = orgService.create("Phòng Kinh doanh", cty.getId(), "system");
        OrgUnit kt = orgService.create("Phòng Kỹ thuật", cty.getId(), "system");

        // ===================== 2) NHÂN SỰ (tên thật) =====================
        UserAccount uTgd = user("tgd", "Lê Quang Minh");
        UserAccount uPtgd = user("ptgd", "Phạm Thị Hồng");
        UserAccount uTpHcns = user("tp.hcns", "Nguyễn Văn Hùng");
        UserAccount uVanthu = user("vanthu", "Trần Thị Lan");
        UserAccount uCvNs = user("cv.ns", "Đỗ Minh Tuấn");
        UserAccount uKtt = user("ktt", "Vũ Thị Mai");
        UserAccount uKtv = user("ktv", "Hoàng Văn Nam");
        UserAccount uTpKd = user("tp.kd", "Bùi Thanh Sơn");
        UserAccount uNvKd1 = user("nv.kd1", "Ngô Thị Thu");
        UserAccount uNvKd2 = user("nv.kd2", "Đặng Văn Long");
        UserAccount uTpKt = user("tp.kt", "Phan Đức Anh");
        UserAccount uKs1 = user("ks1", "Trịnh Văn Khoa");
        UserAccount uKs2 = user("ks2", "Lý Thị Hà");

        // ===================== 3) VAI TRÒ + tập quyền =====================
        roleService.createRole("GIAM_DOC", "Giám đốc", Set.of("APPROVE", "SIGN", "REJECT"), "system");
        roleService.createRole("TRUONG_PHONG", "Trưởng phòng", Set.of("APPROVE", "RETURN", "REJECT"), "system");
        roleService.createRole("KE_TOAN_TRUONG", "Kế toán trưởng", Set.of("VERIFY", "APPROVE"), "system");
        roleService.createRole("CHUYEN_VIEN", "Chuyên viên", Set.of("RECORD", "EDIT", "SUBMIT"), "system");
        roleService.createRole("NHAN_VIEN", "Nhân viên", Set.of("RECORD", "SUBMIT"), "system");
        roleService.createRole("VAN_THU", "Văn thư", Set.of("RECORD", "ISSUE"), "system");

        // ===================== 4) CHỨC DANH + người giữ + vai trò =====================
        Position pTgd = pos("Tổng Giám đốc", bgd, uTgd, "GIAM_DOC");
        pos("Phó Tổng Giám đốc", bgd, uPtgd, "GIAM_DOC");
        Position pTpHcns = pos("Trưởng phòng HCNS", hcns, uTpHcns, "TRUONG_PHONG");
        pos("Văn thư", hcns, uVanthu, "VAN_THU");
        Position pCvNs = pos("Chuyên viên Nhân sự", hcns, uCvNs, "CHUYEN_VIEN");
        Position pKtt = pos("Kế toán trưởng", tckt, uKtt, "KE_TOAN_TRUONG");
        pos("Kế toán viên", tckt, uKtv, "NHAN_VIEN");
        Position pTpKd = pos("Trưởng phòng Kinh doanh", kd, uTpKd, "TRUONG_PHONG");
        Position pNvKd1 = pos("Nhân viên Kinh doanh 1", kd, uNvKd1, "NHAN_VIEN");
        pos("Nhân viên Kinh doanh 2", kd, uNvKd2, "NHAN_VIEN");
        Position pTpKt = pos("Trưởng phòng Kỹ thuật", kt, uTpKt, "TRUONG_PHONG");
        Position pKs1 = pos("Kỹ sư 1", kt, uKs1, "CHUYEN_VIEN");
        pos("Kỹ sư 2", kt, uKs2, "CHUYEN_VIEN");

        // ===================== 5) BIỂU MẪU =====================
        String fTrinh = form("phieu-trinh-van-ban", "Phiếu trình văn bản đi", """
                {"fields": [
                  {"key": "so_van_ban", "label": "Số văn bản", "type": "text", "required": true, "placeholder": "VD: 01/2026/CV-ONE"},
                  {"key": "loai_van_ban", "label": "Loại văn bản", "type": "dropdown", "optionSource": "STATIC", "options": "Công văn, Tờ trình, Quyết định, Báo cáo, Thông báo", "required": true},
                  {"key": "do_khan", "label": "Độ khẩn", "type": "dropdown", "optionSource": "STATIC", "options": "Thường, Khẩn, Hỏa tốc"},
                  {"key": "trich_yeu", "label": "Trích yếu nội dung", "type": "richtext", "required": true, "validation": {"maxLength": 2000}},
                  {"key": "noi_nhan", "label": "Nơi nhận", "type": "text"},
                  {"key": "ngay_trinh", "label": "Ngày trình", "type": "datetime"},
                  {"key": "tep_dinh_kem", "label": "Tệp đính kèm", "type": "file"}
                ]}""");
        String fMuaSam = form("phieu-de-nghi-mua-sam", "Phiếu đề nghị mua sắm", """
                {"fields": [
                  {"key": "hang_muc", "label": "Hạng mục mua sắm", "type": "text", "required": true},
                  {"key": "so_luong", "label": "Số lượng", "type": "number", "required": true, "validation": {"min": 1}},
                  {"key": "don_gia", "label": "Đơn giá dự kiến (VNĐ)", "type": "number"},
                  {"key": "thanh_tien", "label": "Thành tiền (VNĐ)", "type": "number"},
                  {"key": "ly_do", "label": "Lý do / mục đích", "type": "richtext", "required": true},
                  {"key": "ncc_de_xuat", "label": "Nhà cung cấp đề xuất", "type": "text"}
                ]}""");
        String fNghiPhep = form("don-xin-nghi-phep", "Đơn xin nghỉ phép", """
                {"fields": [
                  {"key": "loai_nghi", "label": "Loại nghỉ", "type": "dropdown", "optionSource": "STATIC", "options": "Nghỉ phép năm, Nghỉ ốm, Nghỉ việc riêng, Nghỉ không lương", "required": true},
                  {"key": "tu_ngay", "label": "Từ ngày", "type": "datetime", "required": true},
                  {"key": "den_ngay", "label": "Đến ngày", "type": "datetime", "required": true},
                  {"key": "so_ngay", "label": "Số ngày", "type": "number", "required": true, "validation": {"min": 1}},
                  {"key": "ly_do", "label": "Lý do", "type": "text"},
                  {"key": "nguoi_ban_giao", "label": "Người bàn giao công việc", "type": "text"}
                ]}""");

        // ===================== 6) QUY TRÌNH =====================
        // 6a) Trình ký văn bản đi — có rẽ nhánh (Đồng ý → Ký; Trả lại → Soạn lại)
        String metaTrinh = """
                {
                  "Task_Soan": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":8,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"so_van_ban":"EDIT","loai_van_ban":"EDIT","do_khan":"EDIT","trich_yeu":"EDIT","noi_nhan":"EDIT","ngay_trinh":"EDIT","tep_dinh_kem":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":16,"actions":["APPROVE","RETURN"],"formId":"%s",
                    "fieldPerms":{"so_van_ban":"READONLY","loai_van_ban":"READONLY","do_khan":"READONLY","trich_yeu":"READONLY","noi_nhan":"READONLY","ngay_trinh":"READONLY","tep_dinh_kem":"READONLY"},
                    "fields":[{"key":"ket_qua","label":"Kết quả duyệt","type":"radio","optionSource":"STATIC","options":"Đồng ý, Trả lại","required":true},{"key":"y_kien","label":"Ý kiến","type":"text"}]},
                  "Task_Ky": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":24,"actions":["SIGN"],"formId":"%s",
                    "fieldPerms":{"so_van_ban":"READONLY","loai_van_ban":"READONLY","do_khan":"READONLY","trich_yeu":"READONLY","noi_nhan":"READONLY","ngay_trinh":"READONLY","tep_dinh_kem":"READONLY"}},
                  "Flow_ok": {"condition":{"field":"ket_qua","op":"eq","value":"Đồng ý"}}
                }""".formatted(pCvNs.getId(), fTrinh, pTpHcns.getId(), fTrinh, pTgd.getId(), fTrinh);
        ProcessDefinition pTrinh = publish("trinh-ky-van-ban", "Quy trình trình ký văn bản đi", BPMN_TRINHKY, metaTrinh);

        // 6b) Đề nghị mua sắm — 4 cấp (Đề nghị → Duyệt TP → Thẩm định KT → Phê duyệt GĐ)
        String metaMua = """
                {
                  "Task_DeNghi": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":8,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"hang_muc":"EDIT","so_luong":"EDIT","don_gia":"EDIT","thanh_tien":"EDIT","ly_do":"EDIT","ncc_de_xuat":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":16,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"hang_muc":"READONLY","so_luong":"READONLY","don_gia":"READONLY","thanh_tien":"READONLY","ly_do":"READONLY","ncc_de_xuat":"READONLY"}},
                  "Task_ThamDinh": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":24,"actions":["VERIFY"],"formId":"%s",
                    "fieldPerms":{"hang_muc":"READONLY","so_luong":"READONLY","don_gia":"READONLY","thanh_tien":"READONLY","ly_do":"READONLY","ncc_de_xuat":"READONLY"},
                    "fields":[{"key":"y_kien_tc","label":"Ý kiến tài chính","type":"text"}]},
                  "Task_PheDuyet": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":24,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"hang_muc":"READONLY","so_luong":"READONLY","don_gia":"READONLY","thanh_tien":"READONLY","ly_do":"READONLY","ncc_de_xuat":"READONLY"}}
                }""".formatted(pNvKd1.getId(), fMuaSam, pTpKd.getId(), fMuaSam, pKtt.getId(), fMuaSam, pTgd.getId(), fMuaSam);
        ProcessDefinition pMua = publish("de-nghi-mua-sam", "Quy trình đề nghị mua sắm", BPMN_MUASAM, metaMua);

        // 6c) Nghỉ phép — 2 cấp (Đơn → Trưởng phòng duyệt)
        String metaNghi = """
                {
                  "Task_Don": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":4,"actions":["SUBMIT"],"formId":"%s",
                    "fieldPerms":{"loai_nghi":"EDIT","tu_ngay":"EDIT","den_ngay":"EDIT","so_ngay":"EDIT","ly_do":"EDIT","nguoi_ban_giao":"EDIT"}},
                  "Task_Duyet": {"assigneeType":"POSITION","assigneeId":"%s","slaHours":8,"actions":["APPROVE","REJECT"],"formId":"%s",
                    "fieldPerms":{"loai_nghi":"READONLY","tu_ngay":"READONLY","den_ngay":"READONLY","so_ngay":"READONLY","ly_do":"READONLY","nguoi_ban_giao":"READONLY"}}
                }""".formatted(pKs1.getId(), fNghiPhep, pTpKt.getId(), fNghiPhep);
        ProcessDefinition pNghi = publish("nghi-phep", "Quy trình xin nghỉ phép", BPMN_NGHIPHEP, metaNghi);

        // ===================== 7) HỒ SƠ ĐANG CHẠY (nhiều trạng thái) =====================
        try {
            seedRunningInstances(pTrinh, pMua, pNghi);
        } catch (Exception e) {
            log.warn("[DemoSeeder] Seed hồ sơ chạy gặp lỗi (bỏ qua, dữ liệu cấu hình vẫn đầy đủ): {}", e.toString());
        }

        // ===================== 8) GĐ2 — Bảng tin (Epic 2) + OT (Epic 3) =====================
        try {
            postRepo.save(new Post(uTgd.getId(), "Lê Quang Minh", "Chào mừng cả nhà đến với ONEConnect! Cùng nhau — One Team, One Goal 💙", "[]", "ALL", null, "Thông báo"));
            postRepo.save(new Post(uTpHcns.getId(), "Phòng Hành chính - Nhân sự", "Chương trình Tri ân Thâm niên 2026 chính thức khởi động. Trân trọng kính mời CBNV tham gia.", "[]", "ALL", null, "Sự kiện"));
            postRepo.save(new Post(uTpKd.getId(), "Trưởng phòng Kinh doanh", "Phòng Kinh doanh chúc mừng đạt 120% chỉ tiêu Quý II. Cảm ơn cả đội! 🎉", "[]", "ORG_UNIT", kd.getId(), "Vinh danh"));

            otEntryRepo.save(new OtEntry(uNvKd1.getId(), uNvKd1.getFullName(), kd.getId(),
                    LocalDate.now().withDayOfMonth(5), LocalTime.of(18, 0), LocalTime.of(20, 30), 0,
                    "Chạy chiến dịch bán hàng cuối kỳ", "OT cuối tuần"));
            otEntryRepo.save(new OtEntry(uKs1.getId(), uKs1.getFullName(), kt.getId(),
                    LocalDate.now().withDayOfMonth(7), null, null, 3, "Khắc phục sự cố hệ thống", null));
        } catch (Exception e) {
            log.warn("[DemoSeeder] Seed GĐ2 (bài viết/OT) gặp lỗi (bỏ qua): {}", e.toString());
        }

        // ===================== 9) GĐ2 — Nhân sự (Epic 1, khớp file thật VDX) =====================
        try {
            seedEmployee("0025", "Đang làm việc", "Lê Hữu Thanh", "Giám đốc Trung tâm Phần mềm", "Giám đốc", "PDX", "KKD",
                    LocalDate.of(2023, 12, 1), LocalDate.of(1990, 1, 22), "0979444692",
                    "Hợp đồng lao động không xác định thời hạn", "19030540336668", "Ngân hàng TMCP Kỹ Thương Việt Nam", "Expert");
            seedEmployee("0191", "Đang làm việc", "Phạm Quang Long", "Lập trình viên", "Nhân Viên", "PDX.2", "KKD",
                    LocalDate.of(2019, 11, 11), LocalDate.of(1990, 12, 9), "0389565310",
                    "Hợp đồng lao động không xác định thời hạn", "19029077888011", "", "Pre-senior");
            seedEmployee("1411", "Đang làm việc", "Hoàng Thành Sơn", "Giám đốc Điều hành", "Giám đốc", "BOD.VDX", "KKD",
                    LocalDate.of(2021, 11, 30), LocalDate.of(1987, 8, 12), "0915626461",
                    "Hợp đồng lao động không xác định thời hạn", "19025123064015", "", "Senior");
            seedEmployee("2402", "Đã nghỉ việc", "Hoàng Lan Anh", "Trưởng nhóm Quản lý quan hệ khách hàng", "Trưởng Nhóm", "SDX", "KKD",
                    LocalDate.of(2022, 11, 14), LocalDate.of(2000, 1, 13), "0385563493",
                    "Hợp đồng lao động xác định thời hạn 2 năm", "19036142902015", "", "Pre-senior");
        } catch (Exception e) {
            log.warn("[DemoSeeder] Seed GĐ2 (nhân sự) gặp lỗi (bỏ qua): {}", e.toString());
        }

        log.info("[DemoSeeder] Seed THỰC TẾ: 13 nhân sự / 5 phòng, 6 vai trò, 13 chức danh, 3 biểu mẫu, "
                + "3 quy trình nghiệp vụ, ~10 hồ sơ đang chạy (soạn/chờ duyệt/chờ ký/quá hạn/hoàn thành).");
    }

    private void seedRunningInstances(ProcessDefinition pTrinh, ProcessDefinition pMua, ProcessDefinition pNghi) {
        Map<String, Object> vb1 = vbData("01/2026/CV-ONE", "Công văn", "Thường", "V/v triển khai kế hoạch công tác Quý III/2026");
        Map<String, Object> vb2 = vbData("02/2026/TTr-ONE", "Tờ trình", "Khẩn", "V/v phê duyệt phương án nhân sự năm 2026");
        Map<String, Object> vb3 = vbData("03/2026/QĐ-ONE", "Quyết định", "Thường", "V/v ban hành quy chế chi tiêu nội bộ");
        Map<String, Object> vb4 = vbData("04/2026/BC-ONE", "Báo cáo", "Hỏa tốc", "Báo cáo kết quả kinh doanh 6 tháng đầu năm");
        Map<String, Object> dongY = Map.of("ket_qua", "Đồng ý", "y_kien", "Nội dung đạt yêu cầu, đồng ý trình ký.");

        // Trình ký: đủ trạng thái
        start(pTrinh, vb1);                                            // → đang Soạn (cv.ns)
        start(pTrinh, vb2, "SUBMIT", vb2);                            // → chờ Duyệt (tp.hcns)
        start(pTrinh, vb3, "SUBMIT", vb3, "APPROVE", dongY);         // → chờ Ký (tgd)
        start(pTrinh, vb4, "SUBMIT", vb4, "APPROVE", dongY, "SIGN", Map.of()); // → Hoàn thành
        startBackdated(pTrinh, 30, vbData("05/2026/CV-ONE", "Công văn", "Khẩn",
                "V/v đôn đốc nộp báo cáo định kỳ"), "SUBMIT", Map.of());        // → chờ Duyệt QUÁ HẠN (sla 16h)

        // Mua sắm: các cấp khác nhau
        Map<String, Object> ms1 = msData("Máy tính xách tay Dell Latitude", 5, 18000000, 90000000, "Công ty TNHH Tin học XYZ");
        Map<String, Object> ms2 = msData("Máy in màu đa năng", 2, 12000000, 24000000, "Công ty Thiết bị Văn phòng ABC");
        Map<String, Object> ms3 = msData("Phần mềm diệt virus bản quyền", 50, 350000, 17500000, "Nhà phân phối Phần mềm Việt");
        start(pMua, ms1, "SUBMIT", ms1);                              // → chờ Duyệt TP (tp.kd)
        start(pMua, ms2, "SUBMIT", ms2, "APPROVE", Map.of());        // → chờ Thẩm định (ktt)
        start(pMua, ms3, "SUBMIT", ms3, "APPROVE", Map.of(), "VERIFY",
                Map.of("y_kien_tc", "Trong định mức ngân sách, đề nghị phê duyệt."));  // → chờ Phê duyệt GĐ (tgd)

        // Nghỉ phép
        Map<String, Object> np1 = npData("Nghỉ phép năm", 3, "Về quê giải quyết việc gia đình", "Lý Thị Hà");
        Map<String, Object> np2 = npData("Nghỉ ốm", 2, "Điều trị bệnh theo chỉ định bác sĩ", "Trịnh Văn Khoa");
        start(pNghi, np1, "SUBMIT", np1);                             // → chờ Duyệt (tp.kt)
        start(pNghi, np2, "SUBMIT", np2, "APPROVE", Map.of());       // → Hoàn thành
    }

    // ===================== Helpers =====================

    private UserAccount user(String username, String fullName) {
        String email = username.replace('.', '_') + "@oneconnect.vn";
        return userService.createAccount(username, "Demo@123", fullName, email, null, "USER", "system");
    }

    private void seedEmployee(String code, String status, String name, String jobPos, String title, String dept, String unit,
                              LocalDate join, LocalDate dob, String phone, String contract, String bankAcc, String bankName, String level) {
        Employee e = new Employee(code, name, "system");
        e.apply(status, name, jobPos, title, dept, unit, join, dob, phone, contract, bankAcc, bankName, level, "system");
        employeeRepo.save(e);
    }

    private Position pos(String title, OrgUnit unit, UserAccount holder, String roleCode) {
        Position p = positionService.create(title, unit.getId(), "system");
        positionService.assignHolder(p.getId(), holder.getId(), "system");
        roleService.assignRoleToPosition(p.getId(), roleCode, "system");
        return p;
    }

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

    /** Khởi tạo + hoàn thành lần lượt các bước theo cặp (action, data). */
    @SuppressWarnings("unchecked")
    private WorkflowInstance start(ProcessDefinition p, Map<String, Object> initial, Object... stepActions) {
        WorkflowInstance wi = workflowService.start(p.getId(), initial, "system");
        for (int i = 0; i + 1 < stepActions.length; i += 2) {
            completeActive(wi, (String) stepActions[i], (Map<String, Object>) stepActions[i + 1]);
        }
        return wi;
    }

    /** Như start() nhưng lùi đồng hồ engine để việc/hồ sơ được tạo "trong quá khứ" (mô phỏng quá hạn). */
    @SuppressWarnings("unchecked")
    private WorkflowInstance startBackdated(ProcessDefinition p, int hoursAgo, Map<String, Object> initial, Object... stepActions) {
        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(hoursAgo))));
        try {
            WorkflowInstance wi = workflowService.start(p.getId(), initial, "system");
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

    private static Map<String, Object> vbData(String so, String loai, String khan, String trichYeu) {
        return Map.of("so_van_ban", so, "loai_van_ban", loai, "do_khan", khan,
                "trich_yeu", trichYeu, "noi_nhan", "Các phòng ban liên quan", "ngay_trinh", "2026-06-26T09:00");
    }

    private static Map<String, Object> msData(String hangMuc, int sl, long donGia, long thanhTien, String ncc) {
        return Map.of("hang_muc", hangMuc, "so_luong", sl, "don_gia", donGia, "thanh_tien", thanhTien,
                "ly_do", "Phục vụ nhu cầu công tác của đơn vị.", "ncc_de_xuat", ncc);
    }

    private static Map<String, Object> npData(String loai, int soNgay, String lyDo, String banGiao) {
        return Map.of("loai_nghi", loai, "tu_ngay", "2026-07-01T08:00", "den_ngay", "2026-07-03T17:00",
                "so_ngay", soNgay, "ly_do", lyDo, "nguoi_ban_giao", banGiao);
    }

    // ===================== BPMN =====================

    /** Trình ký: Bắt đầu → Soạn → Duyệt → ◇(Đồng ý → Ký → Kết thúc | Trả lại → Soạn). */
    private static final String BPMN_TRINHKY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_trinhky" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="Process_trinhky" name="Trình ký văn bản đi" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Bắt đầu"><bpmn:outgoing>Flow_s</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="Task_Soan" name="Soạn thảo văn bản"><bpmn:incoming>Flow_s</bpmn:incoming><bpmn:incoming>Flow_back</bpmn:incoming><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:userTask>
                <bpmn:userTask id="Task_Duyet" name="Trưởng phòng duyệt"><bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>
                <bpmn:exclusiveGateway id="Gateway_KQ" name="Kết quả duyệt?" default="Flow_back"><bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_ok</bpmn:outgoing><bpmn:outgoing>Flow_back</bpmn:outgoing></bpmn:exclusiveGateway>
                <bpmn:userTask id="Task_Ky" name="Lãnh đạo ký"><bpmn:incoming>Flow_ok</bpmn:incoming><bpmn:outgoing>Flow_3</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" name="Đã ban hành"><bpmn:incoming>Flow_3</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_s" sourceRef="StartEvent_1" targetRef="Task_Soan" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="Task_Soan" targetRef="Task_Duyet" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Duyet" targetRef="Gateway_KQ" />
                <bpmn:sequenceFlow id="Flow_ok" name="Đồng ý" sourceRef="Gateway_KQ" targetRef="Task_Ky" />
                <bpmn:sequenceFlow id="Flow_back" name="Trả lại" sourceRef="Gateway_KQ" targetRef="Task_Soan" />
                <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_Ky" targetRef="EndEvent_1" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_trinhky">
                <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Soan_di" bpmnElement="Task_Soan"><dc:Bounds x="240" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Duyet_di" bpmnElement="Task_Duyet"><dc:Bounds x="400" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Gateway_KQ_di" bpmnElement="Gateway_KQ" isMarkerVisible="true"><dc:Bounds x="560" y="175" width="50" height="50" /><bpmndi:BPMNLabel><dc:Bounds x="548" y="145" width="74" height="14" /></bpmndi:BPMNLabel></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Ky_di" bpmnElement="Task_Ky"><dc:Bounds x="670" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="842" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNEdge id="Flow_s_di" bpmnElement="Flow_s"><di:waypoint x="188" y="200" /><di:waypoint x="240" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="350" y="200" /><di:waypoint x="400" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="510" y="200" /><di:waypoint x="560" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_ok_di" bpmnElement="Flow_ok"><di:waypoint x="610" y="200" /><di:waypoint x="670" y="200" /><bpmndi:BPMNLabel><dc:Bounds x="624" y="182" width="38" height="14" /></bpmndi:BPMNLabel></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_back_di" bpmnElement="Flow_back"><di:waypoint x="585" y="225" /><di:waypoint x="585" y="300" /><di:waypoint x="295" y="300" /><di:waypoint x="295" y="240" /><bpmndi:BPMNLabel><dc:Bounds x="424" y="282" width="36" height="14" /></bpmndi:BPMNLabel></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="780" y="200" /><di:waypoint x="842" y="200" /></bpmndi:BPMNEdge>
              </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
            </bpmn:definitions>""";

    /** Mua sắm: Bắt đầu → Đề nghị → Duyệt TP → Thẩm định KT → Phê duyệt GĐ → Kết thúc. */
    private static final String BPMN_MUASAM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_muasam" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="Process_muasam" name="Đề nghị mua sắm" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Bắt đầu"><bpmn:outgoing>Flow_s</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="Task_DeNghi" name="Lập đề nghị"><bpmn:incoming>Flow_s</bpmn:incoming><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:userTask>
                <bpmn:userTask id="Task_Duyet" name="Trưởng phòng duyệt"><bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>
                <bpmn:userTask id="Task_ThamDinh" name="Kế toán thẩm định"><bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_3</bpmn:outgoing></bpmn:userTask>
                <bpmn:userTask id="Task_PheDuyet" name="Giám đốc phê duyệt"><bpmn:incoming>Flow_3</bpmn:incoming><bpmn:outgoing>Flow_4</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" name="Đã duyệt"><bpmn:incoming>Flow_4</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_s" sourceRef="StartEvent_1" targetRef="Task_DeNghi" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="Task_DeNghi" targetRef="Task_Duyet" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Duyet" targetRef="Task_ThamDinh" />
                <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ThamDinh" targetRef="Task_PheDuyet" />
                <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_PheDuyet" targetRef="EndEvent_1" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_muasam">
                <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_DeNghi_di" bpmnElement="Task_DeNghi"><dc:Bounds x="240" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Duyet_di" bpmnElement="Task_Duyet"><dc:Bounds x="400" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_ThamDinh_di" bpmnElement="Task_ThamDinh"><dc:Bounds x="560" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_PheDuyet_di" bpmnElement="Task_PheDuyet"><dc:Bounds x="720" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="882" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNEdge id="Flow_s_di" bpmnElement="Flow_s"><di:waypoint x="188" y="200" /><di:waypoint x="240" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="350" y="200" /><di:waypoint x="400" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="510" y="200" /><di:waypoint x="560" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="670" y="200" /><di:waypoint x="720" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4"><di:waypoint x="830" y="200" /><di:waypoint x="882" y="200" /></bpmndi:BPMNEdge>
              </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
            </bpmn:definitions>""";

    /** Nghỉ phép: Bắt đầu → Làm đơn → Duyệt → Kết thúc. */
    private static final String BPMN_NGHIPHEP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_nghiphep" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="Process_nghiphep" name="Xin nghỉ phép" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Bắt đầu"><bpmn:outgoing>Flow_s</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="Task_Don" name="Làm đơn nghỉ phép"><bpmn:incoming>Flow_s</bpmn:incoming><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:userTask>
                <bpmn:userTask id="Task_Duyet" name="Trưởng phòng duyệt"><bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" name="Đã duyệt"><bpmn:incoming>Flow_2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_s" sourceRef="StartEvent_1" targetRef="Task_Don" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="Task_Don" targetRef="Task_Duyet" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Duyet" targetRef="EndEvent_1" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_nghiphep">
                <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Don_di" bpmnElement="Task_Don"><dc:Bounds x="240" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="Task_Duyet_di" bpmnElement="Task_Duyet"><dc:Bounds x="400" y="160" width="110" height="80" /></bpmndi:BPMNShape>
                <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="562" y="182" width="36" height="36" /></bpmndi:BPMNShape>
                <bpmndi:BPMNEdge id="Flow_s_di" bpmnElement="Flow_s"><di:waypoint x="188" y="200" /><di:waypoint x="240" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="350" y="200" /><di:waypoint x="400" y="200" /></bpmndi:BPMNEdge>
                <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="510" y="200" /><di:waypoint x="562" y="200" /></bpmndi:BPMNEdge>
              </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
            </bpmn:definitions>""";
}
