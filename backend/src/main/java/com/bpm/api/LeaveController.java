package com.bpm.api;

import com.bpm.api.dto.LeaveDto;
import com.bpm.application.LeaveService;
import com.bpm.domain.leave.LeaveEntry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Công cụ Đăng ký NGHỉ. /api/v1/leave.
 * - /entries (CRUD) = mọi user đăng nhập, chỉ trên đăng ký của chính mình (kiểm ở service) — FEAT_LEAVE.
 * - /summary, /export = tổng hợp nghỉ phép (chặn ở SecurityConfig) — FEAT_LEAVE_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/leave")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    // ===================== CRUD của tôi =====================

    @GetMapping("/entries")
    public List<LeaveDto.EntryView> myEntries(Authentication auth) {
        return service.myEntries(actor(auth)).stream().map(LeaveDto.EntryView::of).toList();
    }

    @PostMapping("/entries")
    public LeaveDto.EntryView register(@RequestBody LeaveDto.CreateRequest req, Authentication auth) {
        LeaveEntry e = service.register(actor(auth),
                LocalDate.parse(req.fromDate()), LocalDate.parse(req.toDate()), req.type(), req.reason());
        return LeaveDto.EntryView.of(e);
    }

    @PutMapping("/entries/{id}")
    public LeaveDto.EntryView update(@PathVariable String id, @RequestBody LeaveDto.CreateRequest req,
                                     Authentication auth) {
        LeaveEntry e = service.update(actor(auth), id,
                LocalDate.parse(req.fromDate()), LocalDate.parse(req.toDate()), req.type(), req.reason());
        return LeaveDto.EntryView.of(e);
    }

    @DeleteMapping("/entries/{id}")
    public Map<String, Object> delete(@PathVariable String id, Authentication auth) {
        service.delete(actor(auth), id);
        return Map.of("ok", true);
    }

    // ===================== Tổng hợp + xuất =====================

    @GetMapping("/summary")
    public LeaveDto.SummaryView summary(@RequestParam String from, @RequestParam String to,
                                        @RequestParam(required = false) String orgUnitId) {
        return LeaveDto.SummaryView.of(service.summary(LocalDate.parse(from), LocalDate.parse(to), orgUnitId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String from, @RequestParam String to,
                                         @RequestParam(required = false) String orgUnitId) {
        byte[] xlsx = service.exportXlsx(LocalDate.parse(from), LocalDate.parse(to), orgUnitId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tong-hop-nghi_" + from + "_" + to + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
