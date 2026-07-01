package com.bpm.api;

import com.bpm.api.dto.ReportDto;
import com.bpm.application.WorkflowService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Báo cáo vận hành 4 lát cắt + xuất Excel/CSV (Story 4.6/4.8) — ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final WorkflowService workflow;

    public ReportController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    private static Instant parseFrom(String d) {
        return (d == null || d.isBlank()) ? null
                : LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static Instant parseTo(String d) {
        return (d == null || d.isBlank()) ? null
                : LocalDate.parse(d).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    @GetMapping
    public ReportDto.Report report(@RequestParam(required = false) @Nullable String from,
                                   @RequestParam(required = false) @Nullable String to) {
        return workflow.report(parseFrom(from), parseTo(to));
    }

    /** Xuất Excel (CSV UTF-8 BOM — Excel mở trực tiếp). */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) @Nullable String from,
                                            @RequestParam(required = false) @Nullable String to) {
        ReportDto.Report r = workflow.report(parseFrom(from), parseTo(to));
        StringBuilder sb = new StringBuilder();
        sb.append("BÁO CÁO VẬN HÀNH QUY TRÌNH\n");
        sb.append("Từ;").append(from == null ? "Tất cả" : from).append(";Đến;").append(to == null ? "Nay" : to).append("\n\n");

        sb.append("THEO QUY TRÌNH\nQuy trình;Tổng;Đang chạy;Hoàn thành;Đã hủy\n");
        for (ReportDto.ProcessRow p : r.byProcess()) {
            sb.append(csv(p.processName())).append(';').append(p.total()).append(';')
                    .append(p.running()).append(';').append(p.completed()).append(';').append(p.cancelled()).append('\n');
        }
        sb.append("\nTHEO TRẠNG THÁI\nTổng;Đang chạy;Hoàn thành;Đã hủy\n");
        sb.append(r.byStatus().total()).append(';').append(r.byStatus().running()).append(';')
                .append(r.byStatus().completed()).append(';').append(r.byStatus().cancelled()).append("\n\n");

        sb.append("THEO NGƯỜI\nNgười;Đã xử lý;Đang giữ\n");
        for (ReportDto.PersonRow p : r.byPerson()) {
            sb.append(csv(p.user())).append(';').append(p.processed()).append(';').append(p.open()).append('\n');
        }
        sb.append("\nTHEO THỜI GIAN\nNgày;Số hồ sơ khởi tạo\n");
        for (ReportDto.TimeRow t : r.byTime()) {
            sb.append(t.date()).append(';').append(t.started()).append('\n');
        }

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao-cao-van-hanh.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(out);
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        return (s.contains(";") || s.contains("\"") || s.contains("\n"))
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
