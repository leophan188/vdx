package com.bpm.api;

import com.bpm.api.dto.EmployeeDto;
import com.bpm.application.EmployeeService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Quản lý nhân sự + Import từ file (Epic 1 GĐ2) — ROLE_ADMIN.
 * Thay màn "Đồng bộ nhân sự" (Google Sheet) cũ.
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService service;
    private final com.bpm.infrastructure.ProjectMemberRepository projectMemberRepo;
    private final com.bpm.infrastructure.ProjectRepository projectRepo;

    public EmployeeController(EmployeeService service,
                             com.bpm.infrastructure.ProjectMemberRepository projectMemberRepo,
                             com.bpm.infrastructure.ProjectRepository projectRepo) {
        this.service = service;
        this.projectMemberRepo = projectMemberRepo;
        this.projectRepo = projectRepo;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    // ===== CRUD =====

    /** Danh sách + lọc (FR-A03). */
    @GetMapping
    public List<EmployeeDto.EmployeeResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String deptCode,
                                                   @RequestParam(required = false) String level,
                                                   @RequestParam(required = false) String q) {
        // Gom thành viên dự án theo userAccountId → mã dự án + tổng % effort (cho cột "Dự án đang join").
        java.util.Map<String, String> projCodeById = new java.util.HashMap<>();
        projectRepo.findAll().forEach(p -> projCodeById.put(p.getId(), p.getCode()));
        java.util.Map<String, java.util.List<String>> projsByUser = new java.util.HashMap<>();
        java.util.Map<String, Integer> effortByUser = new java.util.HashMap<>();
        projectMemberRepo.findAll().forEach(m -> {
            projsByUser.computeIfAbsent(m.getUserId(), k -> new java.util.ArrayList<>())
                    .add(projCodeById.getOrDefault(m.getProjectId(), "?"));
            effortByUser.merge(m.getUserId(), m.getEffortPct(), Integer::sum);
        });
        return service.list(status, deptCode, level, q).stream()
                .map(e -> {
                    String uid = e.getUserAccountId();
                    return EmployeeDto.EmployeeResponse.of(e, null,
                            uid == null ? java.util.List.of() : projsByUser.getOrDefault(uid, java.util.List.of()),
                            uid == null ? 0 : effortByUser.getOrDefault(uid, 0));
                }).toList();
    }

    /** Tạo mới nhân sự THỦ CÔNG (thuê ngoài/mượn) — không qua import. external=true. */
    @PostMapping
    public EmployeeDto.EmployeeResponse create(@RequestBody EmployeeDto.CreateRequest req, Authentication auth) {
        return EmployeeDto.EmployeeResponse.of(service.createManual(req, actor(auth)));
    }

    @GetMapping("/{id}")
    public EmployeeDto.EmployeeResponse get(@PathVariable String id) {
        var e = service.get(id);
        return EmployeeDto.EmployeeResponse.of(e, service.orgInfo(e));
    }

    /** Xoá nhân sự THUÊ NGOÀI (chỉ external) — gỡ khỏi dự án + xoá tài khoản. */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, Authentication auth) {
        service.deleteExternal(id, actor(auth));
    }

    /** Sửa tay thông tin nhân sự (FR-A04). */
    @PutMapping("/{id}")
    public EmployeeDto.EmployeeResponse update(@PathVariable String id,
                                               @RequestBody EmployeeDto.UpdateRequest req,
                                               Authentication auth) {
        return EmployeeDto.EmployeeResponse.of(service.update(id, req, actor(auth)));
    }

    // ===== Import =====

    /** Xem trước thay đổi từ file (đa phần multipart) — read-only (FR-A02). */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeDto.PreviewResponse preview(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "fullSync", defaultValue = "false") boolean fullSync)
            throws IOException {
        return service.preview(file.getBytes(), file.getOriginalFilename(), fullSync);
    }

    /** Áp dụng import: upsert + tạo/khoá tài khoản + nhật ký (FR-A05/A06/A08). fullSync=true mới khoá người vắng mặt. */
    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeDto.ApplyResponse apply(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "fullSync", defaultValue = "false") boolean fullSync,
                                           Authentication auth) throws IOException {
        return service.apply(file.getBytes(), file.getOriginalFilename(), actor(auth), fullSync);
    }

    // ===== Tuỳ chọn: đồng bộ qua LINK Google Sheet (sheet phải chia sẻ công khai / xuất bản CSV) =====
    public record SheetRequest(String url, boolean fullSync) {}

    @PostMapping("/import/sheet/preview")
    public EmployeeDto.PreviewResponse previewSheet(@RequestBody SheetRequest req) {
        return service.previewFromSheet(req.url(), req.fullSync());
    }

    @PostMapping("/import/sheet/apply")
    public EmployeeDto.ApplyResponse applySheet(@RequestBody SheetRequest req, Authentication auth) {
        return service.applyFromSheet(req.url(), actor(auth), req.fullSync());
    }

    // ===== Link đã lưu — chủ động tự đồng bộ (Việc 3) =====
    public record SheetConfigRequest(String url, boolean fullSync, boolean autoSync, String syncTime) {}
    public record SheetConfigResponse(String sheetUrl, boolean fullSync, boolean autoSync, String syncTime,
                                      String lastSyncAt, String lastSyncStatus) {
        static SheetConfigResponse of(com.bpm.domain.hr.HrSheetConfig c) {
            return new SheetConfigResponse(c.getSheetUrl(), c.isFullSync(), c.isAutoSync(), c.getSyncTime(),
                    c.getLastSyncAt() == null ? null : c.getLastSyncAt().toString(), c.getLastSyncStatus());
        }
    }

    @GetMapping("/sheet-config")
    public SheetConfigResponse getSheetConfig() {
        return SheetConfigResponse.of(service.getSheetConfig());
    }

    @PutMapping("/sheet-config")
    public SheetConfigResponse saveSheetConfig(@RequestBody SheetConfigRequest req, Authentication auth) {
        return SheetConfigResponse.of(service.saveSheetConfig(
                req.url(), req.fullSync(), req.autoSync(), req.syncTime(), actor(auth)));
    }

    @PostMapping("/sheet-config/sync-now")
    public EmployeeDto.ApplyResponse syncNow(Authentication auth) {
        return service.syncSaved(actor(auth));
    }

    /** Nhật ký import (FR-A06). */
    @GetMapping("/import/logs")
    public List<EmployeeDto.LogResponse> logs() {
        return service.logs();
    }
}
