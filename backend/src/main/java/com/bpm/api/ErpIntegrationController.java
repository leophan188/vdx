package com.bpm.api;

import com.bpm.application.ErpIntegrationService;
import com.bpm.application.ErpProjectSyncService;
import com.bpm.application.ErpTimesheetService;
import com.bpm.domain.erp.ErpConfig;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Màn cấu hình TÍCH HỢP ERP: một chỗ khai kết nối chung và link của từng luồng dữ liệu.
 * Chỉ quản trị hệ thống được sửa — ở đây có API key vào ERP của công ty.
 */
@RestController
@RequestMapping("/api/v1/erp-integrations")
public class ErpIntegrationController {

    private final ErpIntegrationService service;
    private final ErpTimesheetService timesheetService;
    private final ErpProjectSyncService projectSync;

    public ErpIntegrationController(ErpIntegrationService service, ErpTimesheetService timesheetService,
                                    ErpProjectSyncService projectSync) {
        this.service = service;
        this.timesheetService = timesheetService;
        this.projectSync = projectSync;
    }

    /** Cấu hình kết nối chung — KHÔNG kèm API key, chỉ nói đã đặt hay chưa. */
    public record ConnectionResponse(String baseUrl, String dbName, String username, boolean apiKeySet,
                                     String orgUnitName, Long orgUnitErpId,
                                     String lastCheckAt, String lastCheckStatus, String updatedBy) {
        static ConnectionResponse of(ErpConfig c) {
            return new ConnectionResponse(c.getBaseUrl(), c.getDbName(), c.getUsername(), c.hasApiKey(),
                    c.getOrgUnitName(), c.getOrgUnitErpId(),
                    c.getLastCheckAt() == null ? null : c.getLastCheckAt().toString(),
                    c.getLastCheckStatus(), c.getUpdatedBy());
        }
    }

    public record OrgUnitRequest(String name) {
    }

    public record OrgUnitResponse(List<String> matches) {
    }

    public record SyncRequest(List<Long> erpIds) {
    }

    public record SyncResult(int count, String message) {
    }

    public record ConnectionRequest(String baseUrl, String dbName, String username, String apiKey) {
    }

    public record IntegrationRequest(String linkUrl, String modelName, boolean enabled) {
    }

    public record TestResult(String message) {
    }

    /** Tất cả trong một lần gọi: màn hình cần cả kết nối lẫn danh sách luồng để vẽ. */
    public record OverviewResponse(ConnectionResponse connection,
                                   List<ErpIntegrationService.IntegrationView> integrations) {
    }

    @GetMapping
    public OverviewResponse overview() {
        return new OverviewResponse(ConnectionResponse.of(timesheetService.config()), service.list());
    }

    @PutMapping("/connection")
    public ConnectionResponse saveConnection(@RequestBody ConnectionRequest req, Authentication auth) {
        requireAdmin(auth);
        return ConnectionResponse.of(timesheetService.saveConfig(req.baseUrl(), req.dbName(),
                req.username(), req.apiKey(), ApiAuth.actor(auth)));
    }

    @PostMapping("/connection/test")
    public TestResult testConnection(@RequestBody(required = false) ConnectionRequest req, Authentication auth) {
        requireAdmin(auth);
        ConnectionRequest r = req == null ? new ConnectionRequest(null, null, null, null) : req;
        return new TestResult(timesheetService.testConnection(r.baseUrl(), r.dbName(), r.username(),
                r.apiKey(), ApiAuth.actor(auth)));
    }

    @PutMapping("/{key}")
    public ErpIntegrationService.IntegrationView save(@PathVariable String key,
                                                      @RequestBody IntegrationRequest req,
                                                      Authentication auth) {
        requireAdmin(auth);
        return service.save(key, req.linkUrl(), req.modelName(), req.enabled(), ApiAuth.actor(auth));
    }

    @PostMapping("/{key}/test")
    public ErpIntegrationService.IntegrationView test(@PathVariable String key, Authentication auth) {
        requireAdmin(auth);
        return service.test(key, ApiAuth.actor(auth));
    }

    /** Tra cứu đơn vị trên cây tổ chức ERP theo tên và ghi vào cấu hình. */
    @PostMapping("/org-unit")
    public OrgUnitResponse resolveOrgUnit(@RequestBody OrgUnitRequest req, Authentication auth) {
        requireAdmin(auth);
        return new OrgUnitResponse(service.resolveOrgUnit(req.name(), ApiAuth.actor(auth)));
    }

    /** Danh sách dự án của đơn vị bên ERP, kèm cờ đã đồng bộ hay chưa. */
    @GetMapping("/projects")
    public List<ErpProjectSyncService.Candidate> projects(Authentication auth) {
        requireAdmin(auth);
        return projectSync.candidates();
    }

    /** Đưa các dự án đã tick về PlanX. */
    @PostMapping("/projects/sync")
    public SyncResult syncProjects(@RequestBody SyncRequest req, Authentication auth) {
        requireAdmin(auth);
        int n = projectSync.sync(req.erpIds(), ApiAuth.actor(auth));
        return new SyncResult(n, "Đã đồng bộ " + n + " dự án từ ERP.");
    }

    private static void requireAdmin(Authentication auth) {
        if (!ApiAuth.isAdmin(auth)) {
            throw new AccessDeniedException("Chỉ quản trị hệ thống được sửa cấu hình tích hợp ERP.");
        }
    }

    @SuppressWarnings("unused")
    private static String str(Instant i) {
        return i == null ? null : i.toString();
    }
}
