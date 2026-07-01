package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.infrastructure.AuditEventRepository;
import com.bpm.infrastructure.CoordinationRoundRepository;
import com.bpm.infrastructure.DelegationRepository;
import com.bpm.infrastructure.DocumentRepository;
import com.bpm.infrastructure.DocumentVersionRepository;
import com.bpm.infrastructure.EmployeeImportLogRepository;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.FormDefinitionRepository;
import com.bpm.infrastructure.FormVersionRepository;
import com.bpm.infrastructure.NotificationRepository;
import com.bpm.infrastructure.OpinionRepository;
import com.bpm.infrastructure.OrgUnitClosureRepository;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.OtEntryRepository;
import com.bpm.infrastructure.OtPeriodRepository;
import com.bpm.infrastructure.PositionAssignmentRepository;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.PositionRoleRepository;
import com.bpm.infrastructure.PostCommentRepository;
import com.bpm.infrastructure.PostLikeRepository;
import com.bpm.infrastructure.PostMediaRepository;
import com.bpm.infrastructure.PostRepository;
import com.bpm.infrastructure.ProcessDefinitionRepository;
import com.bpm.infrastructure.ProcessVersionRepository;
import com.bpm.infrastructure.ReportRunRepository;
import com.bpm.infrastructure.RoleRepository;
import com.bpm.infrastructure.SubstituteRepository;
import com.bpm.infrastructure.TaskAssignmentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import com.bpm.infrastructure.WorkflowInstanceRepository;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Xoá dữ liệu hệ thống theo nhóm (Cấu hình hệ thống → Vùng nguy hiểm). CHỈ ADMIN.
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>TUYỆT ĐỐI KHÔNG xoá tài khoản admin (username="admin").</li>
 *   <li>Mỗi nhóm bọc try/catch riêng — một nhóm lỗi không chặn nhóm khác.</li>
 *   <li>Xoá theo thứ tự an toàn: bảng phụ thuộc (con/closure) trước, bảng gốc sau.</li>
 *   <li>Flowable (deployment/instance) xoá best-effort, không chặn nếu lỗi.</li>
 * </ul>
 * Mỗi nhóm trả về số bản ghi đã xoá (số), hoặc chuỗi "Lỗi: …" nếu nhóm đó thất bại.
 */
@Service
public class SystemDataService {

    private static final Logger log = LoggerFactory.getLogger(SystemDataService.class);

    /** Tài khoản được bảo vệ — không bao giờ bị xoá. */
    public static final String PROTECTED_ADMIN_USERNAME = "admin";

    private final EmployeeRepository employeeRepo;
    private final EmployeeImportLogRepository importLogRepo;
    private final UserAccountRepository userRepo;
    private final RoleRepository roleRepo;
    private final PositionRoleRepository positionRoleRepo;
    private final OrgUnitRepository orgUnitRepo;
    private final OrgUnitClosureRepository orgClosureRepo;
    private final PositionRepository positionRepo;
    private final PositionAssignmentRepository positionAssignmentRepo;
    private final ProcessDefinitionRepository processDefRepo;
    private final ProcessVersionRepository processVersionRepo;
    private final WorkflowInstanceRepository workflowInstanceRepo;
    private final TaskAssignmentRepository taskAssignmentRepo;
    private final CoordinationRoundRepository coordinationRoundRepo;
    private final OpinionRepository opinionRepo;
    private final DelegationRepository delegationRepo;
    private final SubstituteRepository substituteRepo;
    private final FormDefinitionRepository formDefRepo;
    private final FormVersionRepository formVersionRepo;
    private final PostRepository postRepo;
    private final PostCommentRepository postCommentRepo;
    private final PostLikeRepository postLikeRepo;
    private final PostMediaRepository postMediaRepo;
    private final OtEntryRepository otEntryRepo;
    private final OtPeriodRepository otPeriodRepo;
    private final DocumentRepository documentRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final ReportRunRepository reportRunRepo;
    private final NotificationRepository notificationRepo;
    private final AuditEventRepository auditEventRepo;
    private final RepositoryService flowableRepositoryService;
    private final RuntimeService flowableRuntimeService;
    private final AuditPort auditPort;

    public SystemDataService(EmployeeRepository employeeRepo,
                             EmployeeImportLogRepository importLogRepo,
                             UserAccountRepository userRepo,
                             RoleRepository roleRepo,
                             PositionRoleRepository positionRoleRepo,
                             OrgUnitRepository orgUnitRepo,
                             OrgUnitClosureRepository orgClosureRepo,
                             PositionRepository positionRepo,
                             PositionAssignmentRepository positionAssignmentRepo,
                             ProcessDefinitionRepository processDefRepo,
                             ProcessVersionRepository processVersionRepo,
                             WorkflowInstanceRepository workflowInstanceRepo,
                             TaskAssignmentRepository taskAssignmentRepo,
                             CoordinationRoundRepository coordinationRoundRepo,
                             OpinionRepository opinionRepo,
                             DelegationRepository delegationRepo,
                             SubstituteRepository substituteRepo,
                             FormDefinitionRepository formDefRepo,
                             FormVersionRepository formVersionRepo,
                             PostRepository postRepo,
                             PostCommentRepository postCommentRepo,
                             PostLikeRepository postLikeRepo,
                             PostMediaRepository postMediaRepo,
                             OtEntryRepository otEntryRepo,
                             OtPeriodRepository otPeriodRepo,
                             DocumentRepository documentRepo,
                             DocumentVersionRepository documentVersionRepo,
                             ReportRunRepository reportRunRepo,
                             NotificationRepository notificationRepo,
                             AuditEventRepository auditEventRepo,
                             RepositoryService flowableRepositoryService,
                             RuntimeService flowableRuntimeService,
                             AuditPort auditPort) {
        this.employeeRepo = employeeRepo;
        this.importLogRepo = importLogRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.positionRoleRepo = positionRoleRepo;
        this.orgUnitRepo = orgUnitRepo;
        this.orgClosureRepo = orgClosureRepo;
        this.positionRepo = positionRepo;
        this.positionAssignmentRepo = positionAssignmentRepo;
        this.processDefRepo = processDefRepo;
        this.processVersionRepo = processVersionRepo;
        this.workflowInstanceRepo = workflowInstanceRepo;
        this.taskAssignmentRepo = taskAssignmentRepo;
        this.coordinationRoundRepo = coordinationRoundRepo;
        this.opinionRepo = opinionRepo;
        this.delegationRepo = delegationRepo;
        this.substituteRepo = substituteRepo;
        this.formDefRepo = formDefRepo;
        this.formVersionRepo = formVersionRepo;
        this.postRepo = postRepo;
        this.postCommentRepo = postCommentRepo;
        this.postLikeRepo = postLikeRepo;
        this.postMediaRepo = postMediaRepo;
        this.otEntryRepo = otEntryRepo;
        this.otPeriodRepo = otPeriodRepo;
        this.documentRepo = documentRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.reportRunRepo = reportRunRepo;
        this.notificationRepo = notificationRepo;
        this.auditEventRepo = auditEventRepo;
        this.flowableRepositoryService = flowableRepositoryService;
        this.flowableRuntimeService = flowableRuntimeService;
        this.auditPort = auditPort;
    }

    /** Mô tả một nhóm dữ liệu để FE render checkbox. */
    public record CategoryInfo(String key, String label, String description, long count) {
    }

    /**
     * Danh mục nhóm dữ liệu + ước lượng số bản ghi hiện có (đếm bảng đại diện của nhóm).
     * Dùng để FE dựng checkbox; count chỉ là gợi ý.
     */
    public List<CategoryInfo> categories() {
        List<CategoryInfo> out = new ArrayList<>();
        out.add(info("employees", "Nhân sự", "Hồ sơ nhân sự + log import", employeeRepo::count));
        out.add(info("accounts", "Tài khoản", "Tài khoản người dùng (GIỮ tài khoản admin)",
                () -> Math.max(0, userRepo.count() - 1)));
        out.add(info("roles", "Vai trò", "Vai trò + gán vai trò cho vị trí", roleRepo::count));
        out.add(info("org", "Cơ cấu tổ chức", "Đơn vị + bảng quan hệ cây (closure)", orgUnitRepo::count));
        out.add(info("positions", "Vị trí", "Vị trí/chức danh + lịch sử bổ nhiệm", positionRepo::count));
        out.add(info("processes", "Quy trình & hồ sơ",
                "Định nghĩa/phiên bản quy trình, hồ sơ đang chạy, phân công, phối hợp, uỷ quyền + dữ liệu Flowable",
                processDefRepo::count));
        out.add(info("forms", "Biểu mẫu", "Định nghĩa + phiên bản biểu mẫu", formDefRepo::count));
        out.add(info("social", "Mạng xã hội", "Bài đăng, bình luận, lượt thích, media", postRepo::count));
        out.add(info("ot", "OT", "Đăng ký OT + kỳ OT", otEntryRepo::count));
        out.add(info("documents", "Tài liệu", "Tài liệu + phiên bản tài liệu", documentRepo::count));
        out.add(info("reports", "Báo cáo", "Lịch sử chạy báo cáo", reportRunRepo::count));
        out.add(info("notifications", "Thông báo", "Thông báo người dùng", notificationRepo::count));
        out.add(info("audit", "Kiểm toán", "Nhật ký kiểm toán (append-only)", auditEventRepo::count));
        return out;
    }

    private CategoryInfo info(String key, String label, String description, LongSupplier counter) {
        long count;
        try {
            count = counter.getAsLong();
        } catch (RuntimeException ex) {
            count = -1; // không đếm được — bỏ qua, vẫn cho chọn
        }
        return new CategoryInfo(key, label, description, count);
    }

    /**
     * Xoá các nhóm dữ liệu được chọn. Trả map key-nhóm → số bản ghi đã xoá (Long) hoặc "Lỗi: …" (String).
     * Nhóm không nằm trong {@code categories} sẽ bị bỏ qua. Ghi audit SYSTEM_DATA_WIPED.
     */
    @Transactional
    public Map<String, Object> wipe(Set<String> categories, String actor) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (categories == null || categories.isEmpty()) {
            return result;
        }

        // Thứ tự xử lý cố định, an toàn theo phụ thuộc nghiệp vụ.
        runIf(categories, result, "processes", this::wipeProcesses);
        runIf(categories, result, "forms", this::wipeForms);
        runIf(categories, result, "social", this::wipeSocial);
        runIf(categories, result, "ot", this::wipeOt);
        runIf(categories, result, "documents", this::wipeDocuments);
        runIf(categories, result, "reports", this::wipeReports);
        runIf(categories, result, "notifications", this::wipeNotifications);
        runIf(categories, result, "employees", this::wipeEmployees);
        runIf(categories, result, "positions", this::wipePositions);
        runIf(categories, result, "roles", this::wipeRoles);
        runIf(categories, result, "org", this::wipeOrg);
        runIf(categories, result, "accounts", this::wipeAccounts);
        // Audit xử lý cuối để vẫn ghi được audit của các nhóm trước (nếu audit cũng được chọn,
        // bản ghi SYSTEM_DATA_WIPED sẽ được ghi SAU khi xoá bảng audit ⇒ vẫn còn lại).
        runIf(categories, result, "audit", this::wipeAudit);

        auditPort.record("SYSTEM_DATA_WIPED", "System", null, actor,
                "categories=" + new ArrayList<>(categories) + ", result=" + result);
        log.warn("SYSTEM_DATA_WIPED by {} categories={} result={}", actor, categories, result);
        return result;
    }

    private void runIf(Set<String> categories, Map<String, Object> result, String key, LongSupplier action) {
        if (!categories.contains(key)) {
            return;
        }
        try {
            long deleted = action.getAsLong();
            result.put(key, deleted);
        } catch (RuntimeException ex) {
            log.error("Lỗi xoá nhóm {}", key, ex);
            result.put(key, "Lỗi: " + ex.getMessage());
        }
    }

    // ===================== Các nhóm (mỗi method tự đếm số bản ghi đã xoá) =====================

    private long wipeProcesses() {
        long n = 0;
        // Bảng phụ thuộc hồ sơ chạy trước.
        n += deleteAll(opinionRepo.count(), opinionRepo::deleteAllInBatch);
        n += deleteAll(coordinationRoundRepo.count(), coordinationRoundRepo::deleteAllInBatch);
        n += deleteAll(delegationRepo.count(), delegationRepo::deleteAllInBatch);
        n += deleteAll(substituteRepo.count(), substituteRepo::deleteAllInBatch);
        n += deleteAll(taskAssignmentRepo.count(), taskAssignmentRepo::deleteAllInBatch);
        n += deleteAll(workflowInstanceRepo.count(), workflowInstanceRepo::deleteAllInBatch);
        n += deleteAll(processVersionRepo.count(), processVersionRepo::deleteAllInBatch);
        n += deleteAll(processDefRepo.count(), processDefRepo::deleteAllInBatch);
        // Best-effort: dọn dữ liệu Flowable (instances đang chạy + deployments).
        wipeFlowableBestEffort();
        return n;
    }

    /** Xoá instances đang chạy + deployments Flowable. Không chặn nếu lỗi. */
    private void wipeFlowableBestEffort() {
        try {
            List<ProcessInstance> instances =
                    flowableRuntimeService.createProcessInstanceQuery().list();
            for (ProcessInstance pi : instances) {
                try {
                    flowableRuntimeService.deleteProcessInstance(pi.getId(), "Xoá dữ liệu hệ thống");
                } catch (RuntimeException ex) {
                    log.warn("Không xoá được Flowable instance {}: {}", pi.getId(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Không truy vấn được Flowable instances: {}", ex.getMessage());
        }
        try {
            List<Deployment> deployments =
                    flowableRepositoryService.createDeploymentQuery().list();
            for (Deployment d : deployments) {
                try {
                    // cascade=true để xoá cả history/definition liên quan.
                    flowableRepositoryService.deleteDeployment(d.getId(), true);
                } catch (RuntimeException ex) {
                    log.warn("Không xoá được Flowable deployment {}: {}", d.getId(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Không truy vấn được Flowable deployments: {}", ex.getMessage());
        }
    }

    private long wipeForms() {
        long n = 0;
        n += deleteAll(formVersionRepo.count(), formVersionRepo::deleteAllInBatch);
        n += deleteAll(formDefRepo.count(), formDefRepo::deleteAllInBatch);
        return n;
    }

    private long wipeSocial() {
        long n = 0;
        n += deleteAll(postLikeRepo.count(), postLikeRepo::deleteAllInBatch);
        n += deleteAll(postCommentRepo.count(), postCommentRepo::deleteAllInBatch);
        n += deleteAll(postMediaRepo.count(), postMediaRepo::deleteAllInBatch);
        n += deleteAll(postRepo.count(), postRepo::deleteAllInBatch);
        return n;
    }

    private long wipeOt() {
        long n = 0;
        n += deleteAll(otEntryRepo.count(), otEntryRepo::deleteAllInBatch);
        n += deleteAll(otPeriodRepo.count(), otPeriodRepo::deleteAllInBatch);
        return n;
    }

    private long wipeDocuments() {
        long n = 0;
        n += deleteAll(documentVersionRepo.count(), documentVersionRepo::deleteAllInBatch);
        n += deleteAll(documentRepo.count(), documentRepo::deleteAllInBatch);
        return n;
    }

    private long wipeReports() {
        return deleteAll(reportRunRepo.count(), reportRunRepo::deleteAllInBatch);
    }

    private long wipeNotifications() {
        return deleteAll(notificationRepo.count(), notificationRepo::deleteAllInBatch);
    }

    private long wipeEmployees() {
        long n = 0;
        n += deleteAll(importLogRepo.count(), importLogRepo::deleteAllInBatch);
        n += deleteAll(employeeRepo.count(), employeeRepo::deleteAllInBatch);
        return n;
    }

    private long wipePositions() {
        long n = 0;
        n += deleteAll(positionAssignmentRepo.count(), positionAssignmentRepo::deleteAllInBatch);
        n += deleteAll(positionRepo.count(), positionRepo::deleteAllInBatch);
        return n;
    }

    private long wipeRoles() {
        long n = 0;
        n += deleteAll(positionRoleRepo.count(), positionRoleRepo::deleteAllInBatch);
        n += deleteAll(roleRepo.count(), roleRepo::deleteAllInBatch);
        return n;
    }

    private long wipeOrg() {
        long n = 0;
        n += deleteAll(orgClosureRepo.count(), orgClosureRepo::deleteAllInBatch);
        n += deleteAll(orgUnitRepo.count(), orgUnitRepo::deleteAllInBatch);
        return n;
    }

    /** Xoá MỌI tài khoản TRỪ username="admin". Tài khoản admin luôn được giữ lại. */
    private long wipeAccounts() {
        List<UserAccount> toDelete = new ArrayList<>();
        for (UserAccount u : userRepo.findAll()) {
            if (!PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername())) {
                toDelete.add(u);
            }
        }
        if (!toDelete.isEmpty()) {
            userRepo.deleteAllInBatch(toDelete);
        }
        return toDelete.size();
    }

    private long wipeAudit() {
        return deleteAll(auditEventRepo.count(), auditEventRepo::deleteAllInBatch);
    }

    /** Đếm trước, rồi xoá toàn bộ bảng; trả số bản ghi đã xoá. */
    private long deleteAll(long count, Runnable deleteOp) {
        deleteOp.run();
        return count;
    }
}
