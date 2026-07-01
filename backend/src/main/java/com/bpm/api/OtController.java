package com.bpm.api;

import com.bpm.api.dto.OtDto;
import com.bpm.application.OtService;
import com.bpm.domain.ot.OtEntry;
import com.bpm.domain.ot.OtPeriod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Công cụ Đăng ký OT (Epic 3). /api/v1/ot.
 * - /entries (CRUD) = mọi user đăng nhập, nhưng chỉ trên đăng ký của chính mình (kiểm ở service).
 * - /summary, /periods/{key}/close, /export = ADMIN (chặn ở SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/ot")
public class OtController {

    private final OtService service;

    public OtController(OtService service) {
        this.service = service;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    private static LocalTime time(String s) {
        return (s == null || s.isBlank()) ? null : LocalTime.parse(s);
    }

    // ===================== CRUD của tôi =====================

    @GetMapping("/entries")
    public List<OtDto.EntryView> myEntries(Authentication auth) {
        return service.myEntries(actor(auth)).stream()
                .map(e -> OtDto.EntryView.of(e, service.isClosed(e.getPeriodKey())))
                .toList();
    }

    @PostMapping("/entries")
    public OtDto.EntryView register(@RequestBody OtDto.EntryRequest req, Authentication auth) {
        OtEntry e = service.register(actor(auth),
                LocalDate.parse(req.workDate()), time(req.startTime()), time(req.endTime()),
                req.hours(), req.reason(), req.note());
        return OtDto.EntryView.of(e, false);
    }

    @PutMapping("/entries/{id}")
    public OtDto.EntryView update(@PathVariable String id, @RequestBody OtDto.EntryRequest req, Authentication auth) {
        OtEntry e = service.update(actor(auth), id,
                LocalDate.parse(req.workDate()), time(req.startTime()), time(req.endTime()),
                req.hours(), req.reason(), req.note());
        return OtDto.EntryView.of(e, service.isClosed(e.getPeriodKey()));
    }

    @DeleteMapping("/entries/{id}")
    public Map<String, Object> delete(@PathVariable String id, Authentication auth) {
        service.delete(actor(auth), id);
        return Map.of("ok", true);
    }

    // ===================== Tổng hợp + chốt kỳ + xuất (ADMIN) =====================

    /** Danh sách kỳ có dữ liệu (mới nhất trước) để FE chọn kỳ. */
    @GetMapping("/periods")
    public List<String> periods() {
        return service.periodsWithData();
    }

    @GetMapping("/summary")
    public OtDto.SummaryView summary(@RequestParam String period,
                                     @RequestParam(required = false) String orgUnitId) {
        return OtDto.SummaryView.of(service.summary(period, orgUnitId));
    }

    @PostMapping("/periods/{key}/close")
    public Map<String, Object> close(@PathVariable String key, Authentication auth) {
        OtPeriod p = service.closePeriod(key, actor(auth));
        return Map.of("periodKey", p.getPeriodKey(), "closed", p.isClosed(),
                "closedAt", p.getClosedAt().toString(), "closedBy", p.getClosedBy());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String period) {
        byte[] xlsx = service.exportXlsx(period);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao-cao-ot-" + period + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    // ===================== Tổng hợp theo KHOẢNG NGÀY (FEAT_OT_MANAGE) =====================

    @GetMapping("/summary-range")
    public OtDto.RangeSummaryView summaryRange(@RequestParam String from, @RequestParam String to,
                                               @RequestParam(required = false) String orgUnitId) {
        return OtDto.RangeSummaryView.of(service.summaryRange(LocalDate.parse(from), LocalDate.parse(to), orgUnitId));
    }

    @GetMapping("/export-range")
    public ResponseEntity<byte[]> exportRange(@RequestParam String from, @RequestParam String to,
                                              @RequestParam(required = false) String orgUnitId) {
        byte[] xlsx = service.exportRangeXlsx(LocalDate.parse(from), LocalDate.parse(to), orgUnitId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tong-hop-ot_" + from + "_" + to + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
