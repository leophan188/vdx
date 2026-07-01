package com.bpm.api.dto;

import com.bpm.application.OtService;
import com.bpm.domain.ot.OtEntry;

import java.util.List;

/** DTO cho công cụ Đăng ký OT (Epic 3). */
public final class OtDto {

    private OtDto() {
    }

    /** Tạo/sửa đăng ký. start/end "HH:mm" (tuỳ chọn) hoặc hours; workDate "YYYY-MM-DD". */
    public record EntryRequest(String workDate, String startTime, String endTime, Double hours,
                               String reason, String note) {
    }

    /** Một đăng ký OT trả về FE. closed = kỳ đã chốt ⇒ FE khoá sửa/xoá. */
    public record EntryView(String id, String userId, String userName, String orgUnitId,
                            String workDate, String startTime, String endTime, double hours,
                            String reason, String note, String periodKey, boolean closed,
                            String createdAt) {
        public static EntryView of(OtEntry e, boolean closed) {
            return new EntryView(e.getId(), e.getUserId(), e.getUserName(), e.getOrgUnitId(),
                    e.getWorkDate() == null ? null : e.getWorkDate().toString(),
                    e.getStartTime() == null ? null : e.getStartTime().toString(),
                    e.getEndTime() == null ? null : e.getEndTime().toString(),
                    e.getHours(), e.getReason(), e.getNote(), e.getPeriodKey(), closed,
                    e.getCreatedAt().toString());
        }
    }

    public record EmployeeRow(String userId, String userName, String orgUnitId, String orgUnitName,
                              double totalHours, int entryCount) {
        public static EmployeeRow of(OtService.EmployeeSummary s) {
            return new EmployeeRow(s.userId(), s.userName(), s.orgUnitId(), s.orgUnitName(),
                    s.totalHours(), s.entryCount());
        }
    }

    public record DeptRow(String orgUnitId, String orgUnitName, double totalHours, int entryCount) {
        public static DeptRow of(OtService.DeptSummary s) {
            return new DeptRow(s.orgUnitId(), s.orgUnitName(), s.totalHours(), s.entryCount());
        }
    }

    public record SummaryView(String periodKey, boolean closed, double totalHours, int entryCount,
                              List<EmployeeRow> byEmployee, List<DeptRow> byDept) {
        public static SummaryView of(OtService.PeriodSummary s) {
            return new SummaryView(s.periodKey(), s.closed(), s.totalHours(), s.entryCount(),
                    s.byEmployee().stream().map(EmployeeRow::of).toList(),
                    s.byDept().stream().map(DeptRow::of).toList());
        }
    }

    // ===================== Tổng hợp theo KHOẢNG NGÀY =====================

    public record RangeEmpRow(String userId, String userName, String orgUnitId, String orgUnitName,
                              double totalHours, int entryCount) {
        public static RangeEmpRow of(OtService.RangeEmployeeSummary s) {
            return new RangeEmpRow(s.userId(), s.userName(), s.orgUnitId(), s.orgUnitName(),
                    s.totalHours(), s.entryCount());
        }
    }

    public record RangeTotals(double totalHours, int people) {
        public static RangeTotals of(OtService.RangeTotals t) {
            return new RangeTotals(t.totalHours(), t.people());
        }
    }

    public record RangeSummaryView(String from, String to, List<RangeEmpRow> byEmployee, RangeTotals totals) {
        public static RangeSummaryView of(OtService.RangeSummary s) {
            return new RangeSummaryView(s.from().toString(), s.to().toString(),
                    s.byEmployee().stream().map(RangeEmpRow::of).toList(),
                    RangeTotals.of(s.totals()));
        }
    }
}
