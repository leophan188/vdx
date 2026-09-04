package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.erp.ErpConfig;
import com.bpm.domain.erp.ErpIntegration;
import com.bpm.domain.erp.ErpIntegrationKind;
import com.bpm.infrastructure.erp.ErpIntegrationRepository;
import com.bpm.infrastructure.erp.OdooAttendanceClient;
import com.bpm.infrastructure.erp.OdooOrgClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Khai báo và kiểm tra các luồng dữ liệu lấy từ ERP.
 *
 * Màn này chỉ lo phần KẾT NỐI: link nào, model nào, đọc được hay không. Việc đọc dữ liệu thật của
 * từng luồng nằm ở nghiệp vụ riêng (chấm công đã có ở {@link ErpTimesheetService}) — gom cả hai vào
 * một chỗ thì mỗi lần thêm một luồng lại phải sửa vào giữa phần đang chạy ổn.
 */
@Service
public class ErpIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ErpIntegrationService.class);

    private final ErpIntegrationRepository repo;
    private final ErpTimesheetService timesheetService;
    private final OdooOrgClient orgClient;
    private final OdooAttendanceClient odoo;
    private final AuditPort auditPort;

    public ErpIntegrationService(ErpIntegrationRepository repo, ErpTimesheetService timesheetService,
                                 OdooAttendanceClient odoo, OdooOrgClient orgClient, AuditPort auditPort) {
        this.repo = repo;
        this.timesheetService = timesheetService;
        this.orgClient = orgClient;
        this.odoo = odoo;
        this.auditPort = auditPort;
    }

    /** Một luồng kèm phần mô tả cố định trong code — màn hình cần cả hai để vẽ. */
    public record IntegrationView(String key, String label, String description, String suggestedModel,
                                  String linkUrl, String modelName, boolean enabled,
                                  String lastCheckAt, String lastCheckStatus, Integer lastCount,
                                  String updatedBy) {
    }

    /**
     * Toàn bộ luồng đã khai trong code, kèm phần cấu hình đã lưu (nếu có).
     * Trả về đủ 5 mục kể cả mục chưa ai đụng tới — người dùng phải thấy hệ thống có thể lấy được gì,
     * chứ không phải đoán qua một danh sách rỗng.
     */
    @Transactional(readOnly = true)
    public List<IntegrationView> list() {
        List<IntegrationView> out = new ArrayList<>();
        for (ErpIntegrationKind kind : ErpIntegrationKind.values()) {
            ErpIntegration saved = repo.findById(kind.name()).orElse(null);
            out.add(new IntegrationView(kind.name(), kind.label(), kind.description(), kind.suggestedModel(),
                    saved == null ? null : saved.getLinkUrl(),
                    saved == null ? null : saved.getModelName(),
                    saved != null && saved.isEnabled(),
                    saved == null || saved.getLastCheckAt() == null ? null : saved.getLastCheckAt().toString(),
                    saved == null ? null : saved.getLastCheckStatus(),
                    saved == null ? null : saved.getLastCount(),
                    saved == null ? null : saved.getUpdatedBy()));
        }
        return out;
    }

    @Transactional
    public IntegrationView save(String key, String linkUrl, String modelName, boolean enabled, String actor) {
        ErpIntegrationKind kind = kindOf(key);
        ErpIntegration entity = repo.findById(kind.name()).orElseGet(() -> new ErpIntegration(kind.name()));
        entity.update(linkUrl, modelName, enabled, actor);
        repo.save(entity);
        auditPort.record("ERP_INTEGRATION_UPDATED", "ErpIntegration", kind.name(), actor,
                "model=" + entity.getModelName() + ", enabled=" + entity.isEnabled());
        return find(kind);
    }

    /**
     * Thử đọc model của luồng này. Lưu lại kết quả — cả khi lỗi — để lần sau mở màn còn biết lần kiểm
     * tra gần nhất ra sao thay vì phải bấm lại mới biết.
     */
    @Transactional
    public IntegrationView test(String key, String actor) {
        ErpIntegrationKind kind = kindOf(key);
        ErpIntegration entity = repo.findById(kind.name()).orElseGet(() -> new ErpIntegration(kind.name()));
        String model = entity.getModelName() == null || entity.getModelName().isBlank()
                ? kind.suggestedModel() : entity.getModelName();
        ErpConfig cfg = timesheetService.config();
        try {
            int count = odoo.countRecords(cfg, model);
            entity.markChecked("OK — đọc được " + count + " bản ghi từ " + model, count);
            repo.save(entity);
            auditPort.record("ERP_INTEGRATION_TESTED", "ErpIntegration", kind.name(), actor,
                    "model=" + model + ", count=" + count);
            log.info("[erp] kiểm tra luồng {} ({}) → {} bản ghi", kind.name(), model, count);
        } catch (RuntimeException e) {
            entity.markChecked("LỖI — " + e.getMessage(), null);
            repo.save(entity);
            throw e;
        }
        return find(kind);
    }

    /**
     * Tìm đơn vị trên cây tổ chức ERP theo tên rồi ghi vào cấu hình.
     *
     * Người dùng biết tên đơn vị ("VMO DX") chứ không biết id nội bộ của Odoo, nên nhận tên và tự tra.
     * Trả về danh sách nếu tên khớp nhiều nhánh — chọn bừa nhánh đầu là cách âm thầm lấy nhầm dữ liệu
     * của đơn vị khác.
     */
    @Transactional
    public List<String> resolveOrgUnit(String name, String actor) {
        ErpConfig cfg = timesheetService.config();
        List<OdooOrgClient.Department> found = orgClient.findDepartments(cfg, name);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn vị nào tên chứa \"" + name + "\".");
        }
        // Khớp đúng tên thì chốt luôn, khỏi bắt chọn giữa một danh sách chỉ có một ứng viên thật.
        OdooOrgClient.Department exact = found.stream()
                .filter(d -> d.name().equalsIgnoreCase(name.trim()))
                .findFirst().orElse(found.size() == 1 ? found.get(0) : null);
        if (exact != null) {
            timesheetService.saveOrgUnit(exact.name(), exact.id(), actor);
            auditPort.record("ERP_ORG_UNIT_SET", "ErpConfig", "default", actor,
                    "unit=" + exact.completeName() + " (id=" + exact.id() + ")");
            return List.of(exact.completeName() + " · " + exact.employeeCount() + " nhân sự");
        }
        return found.stream().map(d -> d.completeName() + " · " + d.employeeCount() + " nhân sự").toList();
    }

    private IntegrationView find(ErpIntegrationKind kind) {
        return list().stream().filter(v -> v.key().equals(kind.name())).findFirst().orElseThrow();
    }

    private static ErpIntegrationKind kindOf(String key) {
        try {
            return ErpIntegrationKind.valueOf(key == null ? "" : key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loại tích hợp không hợp lệ: " + key);
        }
    }
}
