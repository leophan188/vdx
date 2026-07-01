package com.bpm.api.dto;

import com.bpm.application.LeaveService;
import com.bpm.domain.leave.LeaveEntry;

import java.util.List;

/** DTO cho công cụ Đăng ký NGHỉ. */
public final class LeaveDto {

    private LeaveDto() {
    }

    /** Tạo/sửa đăng ký. fromDate/toDate "YYYY-MM-DD"; type "ANNUAL"|"UNPAID". */
    public record CreateRequest(String fromDate, String toDate, String type, String reason) {
    }

    /** Một đăng ký nghỉ trả về FE. */
    public record EntryView(String id, String fromDate, String toDate, String type, String typeLabel,
                            double days, String reason, String userName) {
        public static EntryView of(LeaveEntry e) {
            return new EntryView(e.getId(),
                    e.getFromDate() == null ? null : e.getFromDate().toString(),
                    e.getToDate() == null ? null : e.getToDate().toString(),
                    e.getType(), LeaveEntry.typeLabel(e.getType()),
                    e.getDays(), e.getReason(), e.getUserName());
        }
    }

    public record EmpRow(String userId, String userName, String orgUnitId, String orgUnitName,
                         double annualDays, double unpaidDays, double totalDays, int entryCount) {
        public static EmpRow of(LeaveService.EmployeeSummary s) {
            return new EmpRow(s.userId(), s.userName(), s.orgUnitId(), s.orgUnitName(),
                    s.annualDays(), s.unpaidDays(), s.totalDays(), s.entryCount());
        }
    }

    public record Totals(double annualDays, double unpaidDays, double totalDays, int people) {
        public static Totals of(LeaveService.Totals t) {
            return new Totals(t.annualDays(), t.unpaidDays(), t.totalDays(), t.people());
        }
    }

    public record SummaryView(String from, String to, List<EmpRow> byEmployee, Totals totals) {
        public static SummaryView of(LeaveService.RangeSummary s) {
            return new SummaryView(s.from().toString(), s.to().toString(),
                    s.byEmployee().stream().map(EmpRow::of).toList(),
                    Totals.of(s.totals()));
        }
    }
}
