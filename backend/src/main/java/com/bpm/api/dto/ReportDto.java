package com.bpm.api.dto;

import java.util.List;

/** Báo cáo vận hành 4 lát cắt + lọc thời gian (Epic 4 — Story 4.6). */
public class ReportDto {

    /** Lát cắt theo quy trình. */
    public record ProcessRow(String processName, long total, long running, long completed, long cancelled) {
    }

    /** Lát cắt theo trạng thái. */
    public record StatusSummary(long total, long running, long completed, long cancelled) {
    }

    /** Lát cắt theo người: việc đã xử lý (hoàn thành trong kỳ) + việc đang giữ. */
    public record PersonRow(String user, long processed, long open) {
    }

    /** Lát cắt theo thời gian: số hồ sơ khởi tạo theo ngày. */
    public record TimeRow(String date, long started) {
    }

    public record Report(String from, String to,
                         List<ProcessRow> byProcess, StatusSummary byStatus,
                         List<PersonRow> byPerson, List<TimeRow> byTime) {
    }
}
