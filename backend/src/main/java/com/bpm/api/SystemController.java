package com.bpm.api;

import com.bpm.application.EmployeeService;
import com.bpm.application.HrDemoSeeder;
import com.bpm.application.HrEvalDemoSeeder;
import com.bpm.application.Process10StepDemoSeeder;
import com.bpm.application.Qt0101DemoSeeder;
import com.bpm.application.ProjectDemoSeeder;
import com.bpm.application.SocialDemoSeeder;
import com.bpm.application.SystemDataService;
import com.bpm.application.SystemDataService.CategoryInfo;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cấu hình hệ thống — Xoá dữ liệu hệ thống (vùng nguy hiểm). CHỈ ADMIN (chặn ở SecurityConfig).
 * GET /categories: danh mục nhóm để dựng checkbox.
 * POST /wipe: xoá các nhóm được chọn, chỉ chạy khi confirm == "XOA".
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /** Chuỗi xác nhận bắt buộc. */
    private static final String CONFIRM_TOKEN = "XOA";

    private final SystemDataService service;
    private final HrDemoSeeder hrDemoSeeder;
    private final Process10StepDemoSeeder process10StepDemoSeeder;
    private final HrEvalDemoSeeder hrEvalDemoSeeder;
    private final Qt0101DemoSeeder qt0101DemoSeeder;
    private final ProjectDemoSeeder projectDemoSeeder;
    private final SocialDemoSeeder socialDemoSeeder;
    private final EmployeeService employeeService;

    public SystemController(SystemDataService service, HrDemoSeeder hrDemoSeeder,
                            Process10StepDemoSeeder process10StepDemoSeeder, HrEvalDemoSeeder hrEvalDemoSeeder,
                            Qt0101DemoSeeder qt0101DemoSeeder,
                            ProjectDemoSeeder projectDemoSeeder, SocialDemoSeeder socialDemoSeeder,
                            EmployeeService employeeService) {
        this.service = service;
        this.hrDemoSeeder = hrDemoSeeder;
        this.process10StepDemoSeeder = process10StepDemoSeeder;
        this.hrEvalDemoSeeder = hrEvalDemoSeeder;
        this.qt0101DemoSeeder = qt0101DemoSeeder;
        this.projectDemoSeeder = projectDemoSeeder;
        this.socialDemoSeeder = socialDemoSeeder;
        this.employeeService = employeeService;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @GetMapping("/categories")
    public List<CategoryInfo> categories() {
        return service.categories();
    }

    public record WipeRequest(@NotNull List<String> categories, String confirm) {
    }

    @PostMapping("/wipe")
    public ResponseEntity<Map<String, Object>> wipe(@RequestBody WipeRequest req, Authentication auth) {
        if (req == null || !CONFIRM_TOKEN.equals(req.confirm())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chuỗi xác nhận không đúng. Gõ \"" + CONFIRM_TOKEN + "\" để xác nhận."));
        }
        if (req.categories() == null || req.categories().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chưa chọn nhóm dữ liệu nào để xoá."));
        }
        Set<String> categories = new LinkedHashSet<>(req.categories());
        Map<String, Object> result = service.wipe(categories, actor(auth));
        return ResponseEntity.ok(result);
    }

    /**
     * Tạo dữ liệu DEMO quy trình nhân sự trên nhân sự thật (CHỈ THÊM, idempotent). CHỈ ADMIN.
     * Trả tóm tắt: số quy trình / biểu mẫu / hồ sơ đã tạo.
     */
    @PostMapping("/seed-hr-demo")
    public ResponseEntity<HrDemoSeeder.SeedResult> seedHrDemo(Authentication auth) {
        return ResponseEntity.ok(hrDemoSeeder.seed(actor(auth)));
    }

    /**
     * Tạo dữ liệu DEMO module Quản lý dự án trên nhân sự thật (CHỈ THÊM, idempotent). CHỈ ADMIN.
     * Trả tóm tắt: số dự án / thành viên / công việc / bug đã tạo.
     */
    @PostMapping("/seed-project-demo")
    public ResponseEntity<ProjectDemoSeeder.SeedResult> seedProjectDemo(Authentication auth) {
        return ResponseEntity.ok(projectDemoSeeder.seed(actor(auth)));
    }

    /**
     * Tạo dữ liệu DEMO 1 QUY TRÌNH 10 BƯỚC chạy thật qua BPM (Mua sắm – Thanh toán) trên nhân sự thật
     * (CHỈ THÊM, idempotent). CHỈ ADMIN. Trả tóm tắt: số bước / hồ sơ đã tạo.
     */
    @PostMapping("/seed-process-demo")
    public ResponseEntity<Process10StepDemoSeeder.SeedResult> seedProcessDemo(Authentication auth) {
        return ResponseEntity.ok(process10StepDemoSeeder.seed(actor(auth)));
    }

    /** Tạo dữ liệu DEMO quy trình "Đánh giá năng lực nhân sự" (trường BẢNG CHẤM ĐIỂM). CHỈ ADMIN. */
    @PostMapping("/seed-eval-demo")
    public ResponseEntity<HrEvalDemoSeeder.SeedResult> seedEvalDemo(Authentication auth) {
        return ResponseEntity.ok(hrEvalDemoSeeder.seed(actor(auth)));
    }

    /** Cấu hình quy trình nghiệp vụ QT01.01 – Tạo và xử lý nhiệm vụ (Tham gia ý kiến, góp ý). CHỈ ADMIN. */
    @PostMapping("/seed-qt0101")
    public ResponseEntity<Qt0101DemoSeeder.SeedResult> seedQt0101(Authentication auth) {
        return ResponseEntity.ok(qt0101DemoSeeder.seed(actor(auth)));
    }

    /**
     * Tạo dữ liệu DEMO bảng tin MẠNG XÃ HỘI trên nhân sự thật (CHỈ THÊM, idempotent). CHỈ ADMIN.
     * Trả tóm tắt: số bài / bình luận / lượt thích đã tạo.
     */
    @PostMapping("/seed-social-demo")
    public ResponseEntity<SocialDemoSeeder.SeedResult> seedSocialDemo(Authentication auth) {
        return ResponseEntity.ok(socialDemoSeeder.seed(actor(auth)));
    }

    public record FixHrRequest(String password) {
    }

    /**
     * Chuẩn hoá & sửa dữ liệu nhân sự (CHỈ ADMIN): giữ số 0 đầu Mã NV + SĐT, reset mật khẩu tài khoản nhân sự,
     * gán quyền cho vai trò chức danh. password rỗng → dùng mật khẩu mặc định "1111".
     */
    @PostMapping("/fix-hr-data")
    public Map<String, Object> fixHrData(@RequestBody(required = false) FixHrRequest req, Authentication auth) {
        return employeeService.fixHrData(req == null ? null : req.password());
    }
}
