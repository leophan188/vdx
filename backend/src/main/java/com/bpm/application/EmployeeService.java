package com.bpm.application;

import com.bpm.api.dto.EmployeeDto;
import com.bpm.domain.AccountStatus;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.hr.EmployeeImportLog;
import com.bpm.domain.hr.HrSheetConfig;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.domain.role.PositionRole;
import com.bpm.domain.role.Role;
import com.bpm.infrastructure.EmployeeImportLogRepository;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.UserAccountRepository;
import com.bpm.infrastructure.hr.EmployeeFileReader;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quản lý nhân sự + Import từ FILE (Epic 1 GĐ2 — FR-A01..A08, NFR-06/09).
 * Thay cách đồng bộ Google-link cũ: admin TẢI LÊN file Excel/CSV nhân sự thật.
 *
 * <p>{@link #preview} đọc file → đối chiếu theo {@code empCode} với Employee hiện có →
 * phân nhóm ADD/UPDATE/LOCK/HANDOVER + liệt kê dòng lỗi (không chặn dòng hợp lệ).
 *
 * <p>{@link #apply} upsert Employee + tạo/cập nhật UserAccount (username = empCode) + mở/khoá theo trạng thái,
 * trừ HANDOVER (đang giữ việc/là người duyệt — FR-A08). Ghi audit + nhật ký mỗi lần import.
 */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /**
     * Mật khẩu khởi tạo cho tài khoản nhân sự khi import (username=mã NV). Mặc định "1111",
     * đổi được qua biến môi trường BPM_HR_DEFAULT_PASSWORD mà không sửa code — admin/nhân sự đổi sau.
     */
    @org.springframework.beans.factory.annotation.Value("${bpm.hr.default-password:1111}")
    private String defaultPassword;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeeRepository employeeRepo;
    private final EmployeeImportLogRepository logRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeFileReader fileReader;
    private final TaskService taskService;
    private final PasswordEncoder passwordEncoder;
    private final AuditPort auditPort;
    // Liên thông GĐ1 (tái dùng dịch vụ sẵn có — không sửa chữ ký cũ).
    private final OrgUnitService orgUnitService;
    private final OrgUnitRepository orgUnitRepo;
    private final PositionService positionService;
    private final PositionRepository positionRepo;
    private final RoleService roleService;

    private final com.bpm.infrastructure.hr.GoogleSheetReader sheetReader;
    private final com.bpm.infrastructure.HrSheetConfigRepository sheetConfigRepo;
    private final com.bpm.infrastructure.ProjectMemberRepository projectMemberRepo;

    public EmployeeService(EmployeeRepository employeeRepo, EmployeeImportLogRepository logRepo,
                           UserAccountRepository userRepo, EmployeeFileReader fileReader,
                           TaskService taskService, PasswordEncoder passwordEncoder, AuditPort auditPort,
                           OrgUnitService orgUnitService, OrgUnitRepository orgUnitRepo,
                           PositionService positionService, PositionRepository positionRepo,
                           RoleService roleService, com.bpm.infrastructure.hr.GoogleSheetReader sheetReader,
                           com.bpm.infrastructure.HrSheetConfigRepository sheetConfigRepo,
                           com.bpm.infrastructure.ProjectMemberRepository projectMemberRepo) {
        this.projectMemberRepo = projectMemberRepo;
        this.sheetConfigRepo = sheetConfigRepo;
        this.employeeRepo = employeeRepo;
        this.logRepo = logRepo;
        this.userRepo = userRepo;
        this.fileReader = fileReader;
        this.sheetReader = sheetReader;
        this.taskService = taskService;
        this.passwordEncoder = passwordEncoder;
        this.auditPort = auditPort;
        this.orgUnitService = orgUnitService;
        this.orgUnitRepo = orgUnitRepo;
        this.positionService = positionService;
        this.positionRepo = positionRepo;
        this.roleService = roleService;
    }

    // ===== Chuẩn hoá & sửa dữ liệu nhân sự (1 lần, ADMIN) =====

    /** Quyền mặc định theo chức danh (role code) để luồng duyệt quy trình chạy đúng RBAC. */
    private static final Map<String, Set<String>> ROLE_PERMS = Map.of(
            "GIAM_DOC", Set.of("APPROVE", "SIGN", "REJECT", "RETURN", "SUBMIT", "RECORD"),
            "TRUONG_PHONG", Set.of("APPROVE", "REJECT", "RETURN", "SUBMIT", "RECORD"),
            "TRUONG_NHOM", Set.of("APPROVE", "REJECT", "RETURN", "SUBMIT", "RECORD"),
            "NHAN_VIEN", Set.of("RECORD", "SUBMIT", "EDIT"),
            "CONG_TAC_VIEN", Set.of("RECORD", "SUBMIT"),
            "THUC_TAP_SINH", Set.of("RECORD", "SUBMIT"));

    /**
     * Chuẩn hoá & sửa dữ liệu nhân sự (chạy 1 lần): (1) giữ số 0 đầu Mã NV (1–3 chữ số → 4 chữ số) + đồng bộ username;
     * (2) giữ số 0 đầu SĐT (9 chữ số → thêm "0"); (3) reset mật khẩu tài khoản nhân sự; (4) gán quyền cho vai trò chức danh.
     */
    @Transactional
    public Map<String, Object> fixHrData(String newPassword) {
        String pw = blank(newPassword) ? defaultPassword : newPassword.trim();
        List<Employee> all = employeeRepo.findAll();
        Set<String> codeSet = new HashSet<>();
        for (Employee e : all) codeSet.add(e.getEmpCode().trim());

        int codesFixed = 0, phonesFixed = 0, pwReset = 0;
        for (Employee e : all) {
            // (1) Mã NV: pad số 0 đầu (tránh trùng).
            String code = e.getEmpCode();
            String pc = padCode(code);
            if (!pc.equals(code) && !codeSet.contains(pc)) {
                e.setEmpCode(pc);
                codeSet.add(pc);
                if (e.getUserAccountId() != null && userRepo.findByUsername(pc).isEmpty()) {
                    userRepo.findById(e.getUserAccountId()).ifPresent(acc -> acc.setUsername(pc));
                }
                codesFixed++;
            }
            // (2) SĐT: pad số 0 đầu.
            String pp = padPhone(e.getPhone());
            if (pp != null && !pp.equals(e.getPhone())) {
                e.setPhone(pp);
                phonesFixed++;
            }
            employeeRepo.save(e);
            // (3) Reset mật khẩu tài khoản nhân sự.
            if (e.getUserAccountId() != null) {
                Optional<UserAccount> acc = userRepo.findById(e.getUserAccountId());
                if (acc.isPresent()) {
                    acc.get().setPasswordHash(passwordEncoder.encode(pw));
                    userRepo.save(acc.get());
                    pwReset++;
                }
            }
        }

        // (4) Gán quyền cho vai trò chức danh.
        int rolesUpdated = 0;
        for (Role r : roleService.listRoles()) {
            Set<String> perms = ROLE_PERMS.getOrDefault(r.getCode(), Set.of("RECORD", "SUBMIT"));
            roleService.updateRole(r.getCode(), r.getName(), perms, "system");
            rolesUpdated++;
        }

        auditPort.record("HR_DATA_FIXED", "Employee", null, "system",
                "codes=" + codesFixed + ", phones=" + phonesFixed + ", pwReset=" + pwReset + ", roles=" + rolesUpdated);
        log.info("[hr-fix] mã={} sđt={} mật-khẩu={} vai-trò={}", codesFixed, phonesFixed, pwReset, rolesUpdated);
        return Map.of("codesFixed", codesFixed, "phonesFixed", phonesFixed,
                "passwordsReset", pwReset, "rolesUpdated", rolesUpdated, "password", pw);
    }

    /** Mã NV 1–3 chữ số → pad về 4 chữ số (25→0025, 191→0191). Mã có dấu chấm/4+ chữ số giữ nguyên. */
    public static String padCode(String c) {
        return (c != null && c.matches("\\d{1,3}")) ? String.format("%04d", Integer.parseInt(c)) : c;
    }
    /** SĐT 9 chữ số → thêm "0" đầu (SĐT VN 10 số). Khác (có dấu cách / đủ 10 số) giữ nguyên. */
    public static String padPhone(String p) {
        return (p != null && p.matches("\\d{9}")) ? "0" + p : p;
    }

    // ===== CRUD quản lý =====

    /** Danh sách + lọc theo trạng thái / mã bộ phận / level / từ khoá (mã, họ tên, vị trí, chức danh). */
    @Transactional(readOnly = true)
    public List<Employee> list(String status, String deptCode, String level, String keyword) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase();
        return employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(e -> blank(status) || eq(e.getStatus(), status))
                .filter(e -> blank(deptCode) || eq(e.getDeptCode(), deptCode))
                .filter(e -> blank(level) || eq(e.getLevel(), level))
                .filter(e -> q.isEmpty()
                        || contains(e.getEmpCode(), q) || contains(e.getFullName(), q)
                        || contains(e.getJobPosition(), q) || contains(e.getTitle(), q))
                .toList();
    }

    @Transactional(readOnly = true)
    public Employee get(String id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự"));
    }

    /**
     * Resolve thông tin liên thông cho màn chi tiết: đường dẫn cây OrgUnit (vd "KKD / PDX / PDX.1"),
     * tiêu đề vị trí, tên các vai trò của vị trí. Trả về OrgInfo (các phần null/rỗng nếu chưa nối).
     */
    @Transactional(readOnly = true)
    public EmployeeDto.OrgInfo orgInfo(Employee e) {
        String orgPath = null;
        if (e.getOrgUnitId() != null) {
            Optional<OrgUnit> leaf = orgUnitRepo.findById(e.getOrgUnitId());
            if (leaf.isPresent()) {
                List<OrgUnit> chain = new ArrayList<>(orgUnitService.ancestors(e.getOrgUnitId()));
                Collections.reverse(chain); // ancestors trả gần→xa; đảo để xa→gần (gốc trước)
                chain.add(leaf.get());
                List<String> labels = new ArrayList<>();
                for (OrgUnit u : chain) {
                    labels.add(u.getCode() != null ? u.getCode() : u.getName());
                }
                orgPath = String.join(" / ", labels);
            }
        }

        String positionTitle = null;
        List<String> roleNames = new ArrayList<>();
        if (e.getPositionId() != null) {
            Optional<Position> p = positionRepo.findById(e.getPositionId());
            if (p.isPresent()) {
                positionTitle = p.get().getTitle();
                for (PositionRole pr : roleService.rolesOfPosition(e.getPositionId())) {
                    roleService.listRoles().stream()
                            .filter(r -> r.getCode().equals(pr.getRoleCode()))
                            .findFirst()
                            .ifPresent(r -> roleNames.add(r.getName()));
                }
            }
        }
        return new EmployeeDto.OrgInfo(orgPath, positionTitle, roleNames);
    }

    /** Sửa tay thông tin nhân sự + audit. empCode KHÔNG đổi. KHÔNG xoá cứng. */
    @Transactional
    public Employee update(String id, EmployeeDto.UpdateRequest req, String actor) {
        Employee e = get(id);
        e.apply(blankToNull(req.status()), require(req.fullName(), "họ tên"), blankToNull(req.jobPosition()),
                blankToNull(req.title()), blankToNull(req.deptCode()), blankToNull(req.unit()),
                parseDate(req.joinDate()), parseDate(req.birthDate()), blankToNull(req.phone()),
                blankToNull(req.contractType()), blankToNull(req.bankAccount()), blankToNull(req.bankName()),
                blankToNull(req.level()), actor);
        Employee saved = employeeRepo.save(e);
        // Đồng bộ TÊN tài khoản liên kết theo tên DSNS (sửa tên nhân sự → tài khoản cập nhật theo, tránh lệch).
        if (saved.getUserAccountId() != null && saved.getFullName() != null) {
            userRepo.findById(saved.getUserAccountId()).ifPresent(acc -> {
                if (!saved.getFullName().equals(acc.getFullName())) {
                    acc.setFullName(saved.getFullName());
                    userRepo.save(acc);
                }
            });
        }
        // Đồng bộ trạng thái tài khoản liên kết theo trạng thái nhân sự (mở/khoá), trừ khi đang giữ việc.
        syncAccountLock(saved, actor);
        auditPort.record("EMPLOYEE_UPDATED", "Employee", saved.getId(), actor, "empCode=" + saved.getEmpCode());
        return saved;
    }

    /**
     * XOÁ nhân sự THUÊ NGOÀI (external) — chỉ áp dụng cho external (nhân sự import/sync KHÔNG được xoá, chỉ khoá).
     * Gỡ khỏi mọi dự án (ProjectMember), xoá tài khoản liên kết rồi xoá hồ sơ. Ghi audit.
     */
    @Transactional
    public void deleteExternal(String id, String actor) {
        Employee e = get(id);
        if (!e.isExternal()) {
            throw new IllegalArgumentException(
                    "Chỉ xoá được nhân sự THUÊ NGOÀI. Nhân sự đồng bộ từ DSNS/file chỉ có thể đổi trạng thái (khoá).");
        }
        String uid = e.getUserAccountId();
        if (uid != null) {
            // Gỡ khỏi các dự án để không còn thành viên "ma".
            projectMemberRepo.findByUserId(uid).forEach(projectMemberRepo::delete);
            userRepo.findById(uid).ifPresent(userRepo::delete);
        }
        employeeRepo.delete(e);
        auditPort.record("EMPLOYEE_DELETED_EXTERNAL", "Employee", id, actor,
                "empCode=" + e.getEmpCode() + " (thuê ngoài, xoá tay)");
        log.info("[hr-manual] xoá nhân sự ngoài {} · {}", e.getEmpCode(), e.getFullName());
    }

    /**
     * Tạo mới nhân sự THỦ CÔNG cho nhân sự thuê ngoài/mượn (KHÔNG qua import).
     * - Validate empCode bắt buộc & chưa tồn tại.
     * - Đặt external=true → import/sync sẽ KHÔNG khoá khi vắng mặt trong file (xem preview()/apply()).
     * - Tạo UserAccount (username=empCode, mật khẩu mặc định defaultPassword) + linkAccount để gán dự án/đăng nhập.
     * - Ghi audit. KHÔNG liên thông org/position/role tự động (nhân sự ngoài tự gán tay nếu cần).
     */
    @Transactional
    public Employee createManual(EmployeeDto.CreateRequest req, String actor) {
        String empCode = require(req.empCode(), "mã nhân sự (ID)");
        String fullName = require(req.fullName(), "họ tên");
        // Chặn trùng mã (so khớp không phân biệt hoa thường, bỏ khoảng trắng thừa).
        String key = empCode.toLowerCase();
        boolean dup = employeeRepo.findAll().stream()
                .anyMatch(e -> e.getEmpCode() != null && e.getEmpCode().trim().toLowerCase().equals(key));
        if (dup) {
            throw new IllegalArgumentException("Mã nhân sự \"" + empCode + "\" đã tồn tại.");
        }

        Employee emp = new Employee(empCode, fullName, actor);
        emp.setExternal(true);
        emp.apply(blankToNull(req.status()), fullName, blankToNull(req.jobPosition()), blankToNull(req.title()),
                blankToNull(req.deptCode()), blankToNull(req.unit()), parseDate(req.joinDate()),
                parseDate(req.birthDate()), blankToNull(req.phone()), blankToNull(req.contractType()),
                blankToNull(req.bankAccount()), blankToNull(req.bankName()), blankToNull(req.level()), actor);

        // Tạo + liên kết tài khoản (username=empCode, mật khẩu mặc định) để đăng nhập / gán vào dự án.
        // NV thuê ngoài QUẢN LÝ TAY: tài khoản luôn ACTIVE khi tạo (KHÔNG auto-khoá theo chuỗi trạng thái)
        // → gán được vào dự án ngay; admin có thể khoá sau ở màn Quản lý tài khoản nếu cần.
        ensureAccount(emp, actor);
        Employee saved = employeeRepo.save(emp);

        auditPort.record("EMPLOYEE_CREATED_MANUAL", "Employee", saved.getId(), actor,
                "empCode=" + saved.getEmpCode() + " (thuê ngoài/mượn, external=true)");
        log.info("[hr-manual] tạo nhân sự ngoài {} · {}", saved.getEmpCode(), saved.getFullName());
        return saved;
    }

    // ===== Import: xem trước =====

    /** Xem trước từ LINK Google Sheet (tải CSV export rồi đối chiếu như tải file). */
    @Transactional(readOnly = true)
    public EmployeeDto.PreviewResponse previewFromSheet(String sheetUrl, boolean fullSync) {
        return preview(sheetReader.fetchCsv(sheetUrl), "google-sheet.csv", fullSync);
    }

    /** Áp dụng từ LINK Google Sheet. */
    @Transactional
    public EmployeeDto.ApplyResponse applyFromSheet(String sheetUrl, String actor, boolean fullSync) {
        return apply(sheetReader.fetchCsv(sheetUrl), "google-sheet.csv", actor, fullSync);
    }

    // ===== Link Google Sheet đã lưu — chủ động tự đồng bộ (Việc 3) =====

    @Transactional(readOnly = true)
    public HrSheetConfig getSheetConfig() {
        return sheetConfigRepo.findById("default").orElseGet(HrSheetConfig::new);
    }

    @Transactional
    public HrSheetConfig saveSheetConfig(String url, boolean fullSync, boolean autoSync, String syncTime, String actor) {
        HrSheetConfig cfg = sheetConfigRepo.findById("default").orElseGet(HrSheetConfig::new);
        cfg.update(url, fullSync, autoSync, syncTime, actor);
        HrSheetConfig saved = sheetConfigRepo.save(cfg);
        auditPort.record("HR_SHEET_CONFIG_SAVED", "HrSheetConfig", "default", actor,
                "autoSync=" + autoSync + ", dailyAt=" + saved.getSyncTime());
        return saved;
    }

    /** Đồng bộ NGAY dùng link đã lưu; cập nhật trạng thái lần đồng bộ vào config. */
    @Transactional
    public EmployeeDto.ApplyResponse syncSaved(String actor) {
        HrSheetConfig cfg = sheetConfigRepo.findById("default")
                .filter(c -> c.getSheetUrl() != null && !c.getSheetUrl().isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Chưa lưu link Google Sheet."));
        try {
            EmployeeDto.ApplyResponse r = apply(sheetReader.fetchCsv(cfg.getSheetUrl()), "google-sheet.csv", actor, cfg.isFullSync());
            cfg.markSynced("OK: +" + r.added() + " ~" + r.updated() + " khoá" + r.locked() + " lỗi" + r.errors());
            sheetConfigRepo.save(cfg);
            return r;
        } catch (RuntimeException e) {
            cfg.markSynced("LỖI: " + e.getMessage());
            sheetConfigRepo.save(cfg);
            throw e;
        }
    }

    /** Đọc file + đối chiếu → bảng xem trước. KHÔNG ghi gì (read-only). */
    @Transactional(readOnly = true)
    public EmployeeDto.PreviewResponse preview(byte[] bytes, String fileName, boolean fullSync) {
        EmployeeFileReader.ParsedFile parsed = fileReader.read(bytes, fileName);

        Map<String, Employee> existing = new LinkedHashMap<>();
        for (Employee e : employeeRepo.findAll()) {
            existing.put(e.getEmpCode().trim().toLowerCase(), e);
        }

        // Đếm sẽ-tạo-mới cho liên thông (tính trên bản sao tập "đã có", KHÔNG ghi DB).
        Set<String> orgCodes = new HashSet<>();
        for (OrgUnit u : orgUnitRepo.findAll()) {
            if (u.getCode() != null) {
                orgCodes.add(u.getCode());
            }
        }
        Set<String> roleCodes = new HashSet<>();
        roleService.listRoles().forEach(r -> roleCodes.add(r.getCode()));
        int newOrgUnits = 0, newRoles = 0, newPositions = 0;

        List<EmployeeDto.PreviewRow> add = new ArrayList<>();
        List<EmployeeDto.PreviewRow> update = new ArrayList<>();
        List<EmployeeDto.PreviewRow> lock = new ArrayList<>();
        List<EmployeeDto.PreviewRow> handover = new ArrayList<>();
        List<EmployeeDto.PreviewRow> errors = new ArrayList<>();

        Set<String> seen = new HashSet<>();
        Set<String> presentCodes = new HashSet<>();
        int totalRead = parsed.rows().size();

        for (EmployeeFileReader.EmployeeRow row : parsed.rows()) {
            String empCode = row.get("empCode");
            String fullName = row.get("fullName");
            String status = row.get("status");

            if (blank(empCode) || blank(fullName)) {
                errors.add(err(empCode, fullName, row, "Dòng " + row.rowNumber() + ": thiếu ID hoặc Họ và tên"));
                continue;
            }
            String key = empCode.trim().toLowerCase();
            if (!seen.add(key)) {
                errors.add(err(empCode, fullName, row, "Dòng " + row.rowNumber() + ": trùng ID trong file (" + empCode + ")"));
                continue;
            }
            // Kiểm tra định dạng ngày (không chặn dòng — chỉ báo lỗi và bỏ qua dòng đó).
            String dateErr = validateDates(row);
            if (dateErr != null) {
                errors.add(err(empCode, fullName, row, "Dòng " + row.rowNumber() + ": " + dateErr));
                continue;
            }

            presentCodes.add(key);
            Employee match = existing.get(key);
            boolean active = isActiveStatus(status);

            // Đếm liên thông sẽ-tạo-mới (mô phỏng, không ghi DB).
            List<String> codes = simulateOrgCodes(row.get("unit"), row.get("deptCode"));
            for (String c : codes) {
                if (orgCodes.add(c)) {
                    newOrgUnits++;
                }
            }
            String chucDanh = blankToNull(row.get("title"));
            if (chucDanh != null) {
                String rc = toRoleCode(chucDanh);
                if (!rc.isEmpty() && roleCodes.add(rc)) {
                    newRoles++;
                }
            }
            // Position chỉ tạo khi có OrgUnit lá; nhân sự cũ đã có positionId thì tái dùng.
            if (!codes.isEmpty() && (match == null || match.getPositionId() == null)) {
                newPositions++;
            }

            if (match == null) {
                if (active) {
                    add.add(previewRow("ADD", row, "Tạo nhân sự + tài khoản mới"));
                } else {
                    // Nhân sự mới nhưng đã nghỉ → vẫn thêm nhưng tài khoản sẽ khoá ngay.
                    add.add(previewRow("ADD", row, "Tạo nhân sự (trạng thái \"" + status + "\" → tài khoản sẽ khoá)"));
                }
            } else if (match.isExternal()) {
                // Nhân sự thuê ngoài/mượn — KHÔNG để import đụng vào (chỉ cập nhật tay). Bỏ qua, không khoá/không sửa.
                presentCodes.remove(key);
                errors.add(err(empCode, fullName, row,
                        "Dòng " + row.rowNumber() + ": nhân sự THUÊ NGOÀI \"" + empCode + "\" — bỏ qua (chỉ cập nhật tay)"));
            } else if (!active) {
                // Có trong file nhưng trạng thái != Đang làm việc → khoá (hoặc bàn giao).
                classifyLock(match, row, lock, handover, "Trạng thái \"" + status + "\" → khoá tài khoản");
            } else {
                update.add(previewRow("UPDATE", row, describeDiff(match, row)));
            }
        }

        // Đồng bộ TOÀN PHẦN (tuỳ chọn): nhân sự cũ vắng mặt khỏi file → khoá (hoặc bàn giao).
        // Mặc định TẮT để upload từng phần không vô tình khoá người không có trong file.
        if (fullSync) {
            for (Employee e : existing.values()) {
                // LOẠI TRỪ nhân sự thuê ngoài/mượn: vắng mặt khỏi file là bình thường (không đồng bộ) → KHÔNG khoá.
                if (e.isExternal()) {
                    continue;
                }
                if (!presentCodes.contains(e.getEmpCode().trim().toLowerCase()) && isAccountActive(e)) {
                    classifyLock(e, null, lock, handover, "Không còn trong file — sẽ khoá (đồng bộ toàn phần)");
                }
            }
        }

        return new EmployeeDto.PreviewResponse(add, update, lock, handover, errors, totalRead,
                newOrgUnits, newPositions, newRoles);
    }

    /** Mô phỏng danh sách code OrgUnit sẽ cần (đơn vị + các cấp bộ phận) — dùng cho đếm preview. */
    private static List<String> simulateOrgCodes(String unit, String deptCode) {
        List<String> codes = new ArrayList<>();
        String unitCode = blankToNull(unit);
        String dept = blankToNull(deptCode);
        if (unitCode != null) {
            codes.add(unitCode);
        }
        if (dept != null) {
            StringBuilder acc = new StringBuilder();
            for (String part : dept.split("\\.")) {
                String seg = part.trim();
                if (seg.isEmpty()) {
                    continue;
                }
                acc.append(acc.length() == 0 ? "" : ".").append(seg);
                codes.add(acc.toString());
            }
        }
        return codes;
    }

    // ===== Import: áp dụng =====

    @Transactional
    public EmployeeDto.ApplyResponse apply(byte[] bytes, String fileName, String actor, boolean fullSync) {
        EmployeeFileReader.ParsedFile parsed = fileReader.read(bytes, fileName);

        Map<String, Employee> existing = new LinkedHashMap<>();
        for (Employee e : employeeRepo.findAll()) {
            existing.put(e.getEmpCode().trim().toLowerCase(), e);
        }

        int added = 0, updated = 0, locked = 0, handover = 0, errors = 0;
        List<EmployeeDto.PreviewRow> handoverDetail = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> presentCodes = new HashSet<>();

        for (EmployeeFileReader.EmployeeRow row : parsed.rows()) {
            String empCode = row.get("empCode");
            String fullName = row.get("fullName");
            if (blank(empCode) || blank(fullName) || !seen.add(empCode.trim().toLowerCase())
                    || validateDates(row) != null) {
                errors++;
                continue;
            }
            String key = empCode.trim().toLowerCase();
            Employee emp = existing.get(key);
            // Nhân sự thuê ngoài/mượn đã có trong DB → KHÔNG để import sửa/khoá (chỉ cập nhật tay). Bỏ qua dòng.
            if (emp != null && emp.isExternal()) {
                continue;
            }
            presentCodes.add(key);
            boolean isNew = emp == null;
            if (isNew) {
                emp = new Employee(empCode.trim(), fullName.trim(), actor);
            }
            emp.apply(blankToNull(row.get("status")), fullName.trim(), blankToNull(row.get("jobPosition")),
                    blankToNull(row.get("title")), blankToNull(row.get("deptCode")), blankToNull(row.get("unit")),
                    parseDate(row.get("joinDate")), parseDate(row.get("birthDate")), blankToNull(row.get("phone")),
                    blankToNull(row.get("contractType")), blankToNull(row.get("bankAccount")),
                    blankToNull(row.get("bankName")), blankToNull(row.get("level")), actor);

            // Tạo/lấy tài khoản đăng nhập (username = empCode).
            UserAccount acc = ensureAccount(emp, actor);
            emp = employeeRepo.save(emp);

            // Liên thông cơ cấu / vị trí / vai trò (Epic 1 GĐ2 — idempotent).
            linkOrganisation(emp, acc, actor);
            emp = employeeRepo.save(emp);

            // Mở/khoá tài khoản theo trạng thái nhân sự, trừ khi đang giữ việc (HANDOVER).
            int outcome = applyAccountLock(emp, acc, actor);
            if (outcome == LOCKED) {
                locked++;
            } else if (outcome == HANDOVER) {
                handover++;
                handoverDetail.add(previewRow("HANDOVER", row, "Đang giữ việc/là người duyệt — KHÔNG khoá (FR-A08)"));
            }

            if (isNew) {
                added++;
                auditPort.record("EMPLOYEE_IMPORTED_ADD", "Employee", emp.getId(), actor,
                        "empCode=" + emp.getEmpCode() + " (import file)");
            } else {
                updated++;
                auditPort.record("EMPLOYEE_IMPORTED_UPDATE", "Employee", emp.getId(), actor,
                        "empCode=" + emp.getEmpCode() + " (import file)");
            }
        }

        // Đồng bộ TOÀN PHẦN (tuỳ chọn): nhân sự cũ vắng mặt khỏi file → khoá (hoặc bàn giao).
        for (Employee e : fullSync ? existing.values() : java.util.List.<Employee>of()) {
            // LOẠI TRỪ nhân sự thuê ngoài/mượn khỏi khoá-khi-vắng-mặt (không đồng bộ từ file).
            if (e.isExternal()) {
                continue;
            }
            if (presentCodes.contains(e.getEmpCode().trim().toLowerCase()) || !isAccountActive(e)) {
                continue;
            }
            UserAccount acc = e.getUserAccountId() == null ? null
                    : userRepo.findById(e.getUserAccountId()).orElse(null);
            if (acc == null) {
                continue;
            }
            if (hasActiveWork(acc.getId())) {
                handover++;
                handoverDetail.add(previewRowOf("HANDOVER", e,
                        "Vắng mặt khỏi file nhưng đang giữ việc — KHÔNG khoá (FR-A08)"));
                auditPort.record("EMPLOYEE_HANDOVER_REQUIRED", "Employee", e.getId(), actor,
                        "empCode=" + e.getEmpCode() + " — vắng mặt nhưng đang giữ việc");
            } else {
                acc.setStatus(AccountStatus.LOCKED);
                userRepo.save(acc);
                locked++;
                auditPort.record("EMPLOYEE_ACCOUNT_LOCKED", "UserAccount", acc.getId(), actor,
                        "empCode=" + e.getEmpCode() + " (vắng mặt khỏi file)");
            }
        }

        logRepo.save(new EmployeeImportLog(actor, fileName, added, updated, locked, handover, errors, "apply"));
        auditPort.record("EMPLOYEE_IMPORT_APPLIED", "EmployeeImportLog", null, actor,
                "file=" + fileName + ", added=" + added + ", updated=" + updated
                        + ", locked=" + locked + ", handover=" + handover + ", errors=" + errors);
        log.info("[hr-import] {} → +{} ~{} khoá{} bàn-giao{} lỗi{}", fileName, added, updated, locked, handover, errors);
        return new EmployeeDto.ApplyResponse(added, updated, locked, handover, errors, handoverDetail);
    }

    // ===== Nhật ký =====

    @Transactional(readOnly = true)
    public List<EmployeeDto.LogResponse> logs() {
        return logRepo.findTop50ByOrderByRunAtDesc().stream().map(EmployeeDto.LogResponse::from).toList();
    }

    // ===== helpers tài khoản =====

    private static final int NONE = 0, LOCKED = 1, HANDOVER = 2;

    /** Tạo tài khoản nếu chưa có (username = empCode), liên kết Employee→UserAccount. */
    private UserAccount ensureAccount(Employee emp, String actor) {
        if (emp.getUserAccountId() != null) {
            Optional<UserAccount> opt = userRepo.findById(emp.getUserAccountId());
            if (opt.isPresent()) {
                UserAccount acc = opt.get();
                acc.setFullName(emp.getFullName());
                return acc;
            }
        }
        // Có thể tài khoản đã tồn tại trước (vd seed) trùng username.
        Optional<UserAccount> byName = userRepo.findByUsername(emp.getEmpCode());
        UserAccount acc = byName.orElseGet(() -> {
            UserAccount a = new UserAccount(emp.getEmpCode(), passwordEncoder.encode(defaultPassword),
                    emp.getFullName(), "USER");
            UserAccount saved = userRepo.save(a);
            auditPort.record("EMPLOYEE_ACCOUNT_CREATED", "UserAccount", saved.getId(), actor,
                    "username=" + emp.getEmpCode() + " (import file)");
            return saved;
        });
        acc.setFullName(emp.getFullName());
        emp.linkAccount(acc.getId(), actor);
        return acc;
    }

    /** Mở/khoá tài khoản theo trạng thái nhân sự; nếu phải khoá mà đang giữ việc → HANDOVER. */
    private int applyAccountLock(Employee emp, UserAccount acc, String actor) {
        if (emp.isActive()) {
            if (acc.getStatus() == AccountStatus.LOCKED) {
                acc.setStatus(AccountStatus.ACTIVE);
                userRepo.save(acc);
                auditPort.record("EMPLOYEE_ACCOUNT_UNLOCKED", "UserAccount", acc.getId(), actor,
                        "empCode=" + emp.getEmpCode() + " (đang làm việc)");
            } else {
                userRepo.save(acc);
            }
            return NONE;
        }
        // Trạng thái != Đang làm việc → khoá, trừ khi đang giữ việc.
        if (hasActiveWork(acc.getId())) {
            userRepo.save(acc);
            auditPort.record("EMPLOYEE_HANDOVER_REQUIRED", "Employee", emp.getId(), actor,
                    "empCode=" + emp.getEmpCode() + " — đang giữ việc, KHÔNG khoá (FR-A08)");
            return HANDOVER;
        }
        if (acc.getStatus() != AccountStatus.LOCKED) {
            acc.setStatus(AccountStatus.LOCKED);
            auditPort.record("EMPLOYEE_ACCOUNT_LOCKED", "UserAccount", acc.getId(), actor,
                    "empCode=" + emp.getEmpCode() + " (trạng thái " + emp.getStatus() + ")");
        }
        userRepo.save(acc);
        return LOCKED;
    }

    /** Dùng cho sửa tay: đồng bộ khoá tài khoản liên kết theo trạng thái nhân sự (không đếm). */
    private void syncAccountLock(Employee emp, String actor) {
        if (emp.getUserAccountId() == null) {
            return;
        }
        userRepo.findById(emp.getUserAccountId())
                .ifPresent(acc -> applyAccountLock(emp, acc, actor));
    }

    /**
     * FR-A08: người này có đang giữ việc/là người duyệt trong Flowable không?
     * Đúng khi là assignee task đang chạy HOẶC candidate-user task đang chờ.
     */
    private boolean hasActiveWork(String userId) {
        try {
            long asAssignee = taskService.createTaskQuery().taskAssignee(userId).count();
            long asCandidate = taskService.createTaskQuery().taskCandidateUser(userId).count();
            return asAssignee + asCandidate > 0;
        } catch (Exception e) {
            // Thận trọng: không truy vấn được → coi như đang giữ việc, không khoá nhầm.
            log.warn("[hr-import] không kiểm tra được việc Flowable cho {} — coi như đang giữ việc", userId, e);
            return true;
        }
    }

    private boolean isAccountActive(Employee e) {
        if (e.getUserAccountId() == null) {
            return false;
        }
        return userRepo.findById(e.getUserAccountId())
                .map(a -> a.getStatus() == AccountStatus.ACTIVE).orElse(false);
    }

    // ===== liên thông cơ cấu / vị trí / vai trò (Epic 1 GĐ2) =====

    /**
     * Nối nhân sự với OrgUnit (cây Đơn vị + Mã bộ phận), Position (1 ghế) và Role (theo Chức danh).
     * Idempotent: re-import KHÔNG nhân đôi. Lỗi liên thông không chặn import (chỉ ghi log).
     */
    private void linkOrganisation(Employee emp, UserAccount acc, String actor) {
        try {
            // 1) OrgUnit theo Đơn vị (gốc) + Mã bộ phận (cây, dấu chấm) → lá.
            String leafOrgUnitId = resolveOrgUnit(emp.getUnit(), emp.getDeptCode(), actor);
            if (leafOrgUnitId != null) {
                emp.linkOrgUnit(leafOrgUnitId, actor);
            }

            // 2) Position theo Vị trí công việc (fallback Chức danh) — chỉ khi có OrgUnit lá.
            if (leafOrgUnitId != null) {
                String title = blankToNull(emp.getJobPosition());
                if (title == null) {
                    title = blankToNull(emp.getTitle());
                }
                if (title == null) {
                    title = "Nhân sự " + emp.getEmpCode();
                }
                String positionId = ensurePosition(emp, leafOrgUnitId, title, actor);
                emp.linkPosition(positionId, actor);

                // Người giữ = tài khoản nhân sự (idempotent — chỉ gán khi đổi người).
                if (!acc.getId().equals(positionService.currentHolder(positionId))) {
                    positionService.assignHolder(positionId, acc.getId(), actor);
                }

                // 3) Role theo Chức danh → gán cho Position (idempotent).
                String roleCode = ensureRole(emp.getTitle(), actor);
                if (roleCode != null && !roleAlreadyOnPosition(positionId, roleCode)) {
                    roleService.assignRoleToPosition(positionId, roleCode, actor);
                }
            }
        } catch (Exception e) {
            log.warn("[hr-import] không liên thông được nhân sự {} với cơ cấu/vị trí/vai trò", emp.getEmpCode(), e);
        }
    }

    /**
     * Bảo đảm cây OrgUnit theo Đơn vị (gốc) + Mã bộ phận (tách theo dấu chấm) tồn tại; trả về id OrgUnit lá.
     * deptCode trống → lá = đơn vị; cả hai trống → null (bỏ qua nối org, không lỗi).
     */
    private String resolveOrgUnit(String unit, String deptCode, String actor) {
        String unitCode = blankToNull(unit);
        String dept = blankToNull(deptCode);
        if (unitCode == null && dept == null) {
            return null;
        }

        String parentId = null;
        String leafId = null;

        // Gốc = Đơn vị.
        if (unitCode != null) {
            OrgUnit root = ensureOrgUnit(unitCode, unitCode, null, actor);
            parentId = root.getId();
            leafId = root.getId();
        }

        // Mã bộ phận theo dấu chấm: "PDX.1" → ["PDX","PDX.1"]; "BOD.VDX" → ["BOD","BOD.VDX"].
        if (dept != null) {
            String[] parts = dept.split("\\.");
            StringBuilder acc = new StringBuilder();
            for (String part : parts) {
                String seg = part.trim();
                if (seg.isEmpty()) {
                    continue;
                }
                acc.append(acc.length() == 0 ? "" : ".").append(seg);
                String code = acc.toString();
                OrgUnit node = ensureOrgUnit(code, code, parentId, actor);
                parentId = node.getId();
                leafId = node.getId();
            }
        }
        return leafId;
    }

    /** Idempotent: trả OrgUnit theo code nếu có; chưa có → tạo qua OrgUnitService.create rồi gán code. */
    private OrgUnit ensureOrgUnit(String code, String name, String parentId, String actor) {
        Optional<OrgUnit> existing = orgUnitRepo.findByCode(code);
        if (existing.isPresent()) {
            return existing.get();
        }
        OrgUnit created = orgUnitService.create(name, parentId, actor);
        created.setCode(code);
        return orgUnitRepo.save(created);
    }

    /**
     * Mỗi nhân sự = 1 Position. Đã có positionId → cập nhật tiêu đề (+ tạo lại nếu lệch OrgUnit); chưa có → tạo mới.
     * Trả về positionId.
     */
    private String ensurePosition(Employee emp, String orgUnitId, String title, String actor) {
        if (emp.getPositionId() != null) {
            Optional<Position> opt = positionRepo.findById(emp.getPositionId());
            if (opt.isPresent()) {
                Position p = opt.get();
                if (!title.equals(p.getTitle())) {
                    positionService.updateTitle(p.getId(), title, actor);
                }
                // Đổi đơn vị: orgUnitId của Position là immutable → tạo ghế mới ở đơn vị đúng.
                if (!orgUnitId.equals(p.getOrgUnitId())) {
                    return positionService.create(title, orgUnitId, actor).getId();
                }
                return p.getId();
            }
        }
        return positionService.create(title, orgUnitId, actor).getId();
    }

    /**
     * Role theo Chức danh: code = khử dấu + UPPER + khoảng trắng → "_" (vd "Giám đốc"→"GIAM_DOC").
     * Chưa có → tạo (permissions rỗng). Trả role code (null nếu chức danh trống).
     */
    private String ensureRole(String chucDanh, String actor) {
        String name = blankToNull(chucDanh);
        if (name == null) {
            return null;
        }
        String code = toRoleCode(name);
        if (code.isEmpty()) {
            return null;
        }
        if (roleService.listRoles().stream().noneMatch(r -> r.getCode().equals(code))) {
            roleService.createRole(code, name, Collections.emptySet(), actor);
        }
        return code;
    }

    private boolean roleAlreadyOnPosition(String positionId, String roleCode) {
        for (PositionRole pr : roleService.rolesOfPosition(positionId)) {
            if (pr.getRoleCode().equals(roleCode)) {
                return true;
            }
        }
        return false;
    }

    /** Khử dấu tiếng Việt + UPPER + thay khoảng trắng (gộp) bằng "_". */
    static String toRoleCode(String chucDanh) {
        String s = chucDanh.trim();
        // Khử dấu (NFD tách dấu rồi loại các ký tự ghép dấu).
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replace('đ', 'd').replace('Đ', 'D');
        s = s.toUpperCase();
        // Mọi ký tự không phải A-Z0-9 → "_", gộp nhiều "_" và bỏ "_" thừa hai đầu.
        s = s.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s;
    }

    // ===== helpers preview =====

    private void classifyLock(Employee match, EmployeeFileReader.EmployeeRow row,
                              List<EmployeeDto.PreviewRow> lock, List<EmployeeDto.PreviewRow> handover, String msg) {
        boolean holdsWork = match.getUserAccountId() != null && hasActiveWork(match.getUserAccountId());
        EmployeeDto.PreviewRow pr = row != null
                ? previewRow(holdsWork ? "HANDOVER" : "LOCK", row,
                        holdsWork ? "Đang giữ việc/là người duyệt — cần bàn giao trước khi khoá" : msg)
                : previewRowOf(holdsWork ? "HANDOVER" : "LOCK", match,
                        holdsWork ? "Đang giữ việc/là người duyệt — cần bàn giao trước khi khoá" : msg);
        if (holdsWork) {
            handover.add(pr);
        } else {
            lock.add(pr);
        }
    }

    private static EmployeeDto.PreviewRow previewRow(String action, EmployeeFileReader.EmployeeRow row, String msg) {
        return new EmployeeDto.PreviewRow(action, row.get("empCode"), row.get("status"), row.get("fullName"),
                row.get("jobPosition"), row.get("title"), row.get("deptCode"), row.get("unit"),
                row.get("joinDate"), row.get("birthDate"), row.get("phone"), row.get("contractType"),
                row.get("bankAccount"), row.get("bankName"), row.get("level"), msg);
    }

    private static EmployeeDto.PreviewRow err(String empCode, String fullName,
                                              EmployeeFileReader.EmployeeRow row, String msg) {
        return new EmployeeDto.PreviewRow("ERROR", empCode, row.get("status"), fullName,
                row.get("jobPosition"), row.get("title"), row.get("deptCode"), row.get("unit"),
                row.get("joinDate"), row.get("birthDate"), row.get("phone"), row.get("contractType"),
                row.get("bankAccount"), row.get("bankName"), row.get("level"), msg);
    }

    /** Dựng PreviewRow ĐỦ cột từ Employee hiện có (nhân sự cũ bị khoá/bàn giao, không có trong file). */
    private static EmployeeDto.PreviewRow previewRowOf(String action, Employee m, String msg) {
        return new EmployeeDto.PreviewRow(action, m.getEmpCode(), m.getStatus(), m.getFullName(),
                m.getJobPosition(), m.getTitle(), m.getDeptCode(), m.getUnit(),
                fmtDate(m.getJoinDate()), fmtDate(m.getBirthDate()), m.getPhone(), m.getContractType(),
                m.getBankAccount(), m.getBankName(), m.getLevel(), msg);
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? null : d.format(DMY);
    }

    private String describeDiff(Employee e, EmployeeFileReader.EmployeeRow row) {
        List<String> diffs = new ArrayList<>();
        if (!safe(e.getFullName()).equals(safe(row.get("fullName")))) diffs.add("họ tên");
        if (!safe(e.getStatus()).equalsIgnoreCase(safe(row.get("status")))) diffs.add("trạng thái");
        if (!safe(e.getJobPosition()).equals(safe(row.get("jobPosition")))) diffs.add("vị trí");
        if (!safe(e.getTitle()).equals(safe(row.get("title")))) diffs.add("chức danh");
        if (!safe(e.getDeptCode()).equals(safe(row.get("deptCode")))) diffs.add("bộ phận");
        if (!safe(e.getLevel()).equals(safe(row.get("level")))) diffs.add("level");
        return diffs.isEmpty() ? "Không đổi" : "Cập nhật: " + String.join(", ", diffs);
    }

    // ===== helpers chung =====

    private static boolean isActiveStatus(String status) {
        return status != null && "đang làm việc".equals(status.trim().toLowerCase());
    }

    /** Kiểm tra mọi cột ngày trong dòng có đúng dd/MM/yyyy (nếu có giá trị). Trả về thông điệp lỗi hoặc null. */
    private static String validateDates(EmployeeFileReader.EmployeeRow row) {
        for (String f : List.of("joinDate", "birthDate")) {
            String v = row.get(f);
            if (!blank(v) && tryParse(v) == null) {
                String label = f.equals("joinDate") ? "Ngày tham gia" : "Ngày sinh";
                return label + " sai định dạng dd/MM/yyyy (\"" + v + "\")";
            }
        }
        return null;
    }

    private static LocalDate parseDate(String v) {
        return blank(v) ? null : tryParse(v);
    }

    private static LocalDate tryParse(String v) {
        try {
            return LocalDate.parse(v.trim(), DMY);
        } catch (Exception e) {
            return null;
        }
    }

    private static String require(String v, String label) {
        if (blank(v)) {
            throw new IllegalArgumentException("Thiếu " + label);
        }
        return v.trim();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s.trim(); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static boolean eq(String a, String b) { return a != null && a.equalsIgnoreCase(b.trim()); }
    private static boolean contains(String v, String q) { return v != null && v.toLowerCase().contains(q); }
}
