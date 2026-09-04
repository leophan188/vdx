package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.erp.ErpConfig;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectStatus;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.erp.OdooProjectClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chọn dự án bên ERP để đưa về PlanX.
 *
 * KHÔNG kéo hết: ERP có 1.827 dự án của cả công ty, đơn vị này chỉ dùng chừng trăm cái. Người dùng
 * xem danh sách trong phạm vi đơn vị mình rồi tick chọn cái nào cần — đồng bộ toàn bộ là cách nhanh
 * nhất biến màn Dự án thành đống dữ liệu không ai dọn nổi.
 */
@Service
public class ErpProjectSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpProjectSyncService.class);

    private final ErpTimesheetService configService;
    private final OdooProjectClient client;
    private final ProjectRepository projectRepo;
    private final AuditPort auditPort;

    public ErpProjectSyncService(ErpTimesheetService configService, OdooProjectClient client,
                                 ProjectRepository projectRepo, AuditPort auditPort) {
        this.configService = configService;
        this.client = client;
        this.projectRepo = projectRepo;
        this.auditPort = auditPort;
    }

    /**
     * Một dự án bên ERP kèm tình trạng bên này.
     *
     * @param linked   đã đồng bộ về PlanX chưa
     * @param localCode mã dự án bên PlanX (nếu đã đồng bộ) — để đối chiếu nhanh
     */
    public record Candidate(long erpId, String name, String code, String state,
                            String startDate, String endDate, String customer, String unit,
                            boolean linked, String localCode) {
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        ErpConfig cfg = configService.config();
        Long unit = cfg.getOrgUnitErpId();
        if (unit == null) {
            throw new IllegalArgumentException("Chưa chọn đơn vị lấy dữ liệu — khai ở phần Kết nối ERP.");
        }
        List<Candidate> out = new ArrayList<>();
        for (OdooProjectClient.ErpProject p : client.fetchProjects(cfg, unit)) {
            Project local = projectRepo.findByErpProjectId(p.erpId()).orElse(null);
            out.add(new Candidate(p.erpId(), p.name(), p.code(), p.state(),
                    p.startDate() == null ? null : p.startDate().toString(),
                    p.endDate() == null ? null : p.endDate().toString(),
                    p.customer(), p.unit(),
                    local != null, local == null ? null : local.getCode()));
        }
        return out;
    }

    /**
     * Đưa các dự án đã chọn về PlanX. Chạy lại thì CẬP NHẬT bản ghi cũ chứ không tạo thêm — khớp theo
     * ID bên ERP, thứ duy nhất không đổi khi khách sửa tên hay mã dự án.
     *
     * Chỉ chạm vào tên, ngày và trạng thái. Mã dự án bên PlanX giữ nguyên sau lần tạo đầu: nó đã đi
     * vào mã công việc (PRJ-123), đổi mã là làm hỏng mọi tham chiếu đang có.
     */
    @Transactional
    public int sync(List<Long> erpIds, String actor) {
        if (erpIds == null || erpIds.isEmpty()) {
            return 0;
        }
        ErpConfig cfg = configService.config();
        Long unit = cfg.getOrgUnitErpId();
        if (unit == null) {
            throw new IllegalArgumentException("Chưa chọn đơn vị lấy dữ liệu — khai ở phần Kết nối ERP.");
        }
        List<OdooProjectClient.ErpProject> all = client.fetchProjects(cfg, unit);
        int done = 0;
        for (OdooProjectClient.ErpProject p : all) {
            if (!erpIds.contains(p.erpId())) {
                continue;
            }
            Project local = projectRepo.findByErpProjectId(p.erpId()).orElse(null);
            boolean isNew = local == null;
            if (isNew) {
                // Chủ sở hữu để trống: ERP không nói ai là PM bên PlanX, gán bừa một tài khoản còn tệ
                // hơn để trống vì người đó sẽ thấy dự án lạ trong danh sách của mình.
                local = new Project(freeCode(p), p.name() == null ? "(không tên)" : p.name(), null);
                local.setErpProjectId(p.erpId());
            }
            local.setName(p.name() == null ? "(không tên)" : p.name());
            if (p.startDate() != null) {
                local.setStartDate(p.startDate());
            }
            if (p.endDate() != null) {
                local.setDueDate(p.endDate());
            }
            local.setStatus(statusOf(p.state(), local.getStatus()));
            projectRepo.save(local);
            done++;
            auditPort.record(isNew ? "ERP_PROJECT_IMPORTED" : "ERP_PROJECT_UPDATED", "Project",
                    local.getId(), actor, "erpId=" + p.erpId() + ", name=" + p.name());
        }
        log.info("[erp] đồng bộ {} dự án từ ERP", done);
        return done;
    }

    /**
     * Mã dự án bên PlanX: ưu tiên mã ERP, trùng thì thêm hậu tố. Không có mã thì sinh từ ID ERP —
     * mã là bắt buộc và đi vào mã công việc, để trống sẽ vỡ ở chỗ khác.
     */
    private String freeCode(OdooProjectClient.ErpProject p) {
        String base = p.code() == null || p.code().isBlank()
                ? "ERP" + p.erpId()
                : p.code().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "ERP" + p.erpId();
        }
        String code = base;
        int i = 2;
        while (projectRepo.existsByCode(code)) {
            code = base + "-" + i++;
        }
        return code;
    }

    /**
     * Trạng thái ERP → trạng thái PlanX. Không nhận ra thì GIỮ NGUYÊN trạng thái đang có: đồng bộ tên
     * và ngày không có lý do gì để kéo một dự án đang chạy về "Lên kế hoạch".
     */
    private static ProjectStatus statusOf(String erpState, ProjectStatus current) {
        String s = erpState == null ? "" : erpState.toLowerCase(Locale.ROOT);
        if (s.contains("running") || s.contains("progress") || s.contains("active")) {
            return ProjectStatus.ACTIVE;
        }
        if (s.contains("hold") || s.contains("pending")) {
            return ProjectStatus.ON_HOLD;
        }
        if (s.contains("close") || s.contains("done") || s.contains("finish")) {
            return ProjectStatus.DONE;
        }
        if (s.contains("cancel")) {
            return ProjectStatus.CANCELLED;
        }
        return current == null ? ProjectStatus.PLANNING : current;
    }
}
