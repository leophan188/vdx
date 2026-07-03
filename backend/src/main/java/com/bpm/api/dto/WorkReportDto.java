package com.bpm.api.dto;

import java.util.List;

/**
 * DTO cụm BÁO CÁO CÔNG VIỆC (Dashboard + Report Ngày + Report Tuần).
 *
 * <p>Đơn vị đo = EST GIỜ của TASK LÁ (task không có con). 4 nhóm tính theo est giờ:
 * <ul>
 *   <li>inProgress = IN_PROGRESS + IN_REVIEW</li>
 *   <li>done = DONE</li>
 *   <li>upcoming = TODO + BACKLOG</li>
 *   <li>overdue (CẮT NGANG) = dueDate &lt; mốc-kỳ VÀ status != DONE (tập con của chưa-xong)</li>
 * </ul>
 * % mỗi nhóm = estNhóm / tổngEst; % hoàn thành = doneEst / tổngEst. KỲ = snapshot live tại mốc.
 */
public final class WorkReportDto {

    private WorkReportDto() {
    }

    /** Số liệu một nhóm: tổng est giờ, số task, và % so với tổng est của dòng. */
    public record GroupStat(String key, double estimateHours, int taskCount, double pct) {
    }

    /**
     * Một dòng báo cáo (tổng quan / theo dự án / theo thành viên).
     *
     * @param id       khoá dòng (ALL | projectId | userId | UNASSIGNED)
     * @param code     mã hiển thị (mã dự án / mã NV) — có thể null
     * @param name     tên hiển thị (tên dự án / tên nhân sự / "Tổng quan")
     * @param extra    thông tin phụ: bộ phận (thành viên) hoặc mã dự án (dự án) — có thể null
     */
    public record ReportRow(String id, String code, String name, String extra, double totalEst,
                            GroupStat inProgress, GroupStat done, GroupStat upcoming, GroupStat overdue,
                            double completionPct) {
    }

    /**
     * Báo cáo hoàn chỉnh.
     *
     * @param periodType   DAILY | WEEKLY
     * @param periodLabel  nhãn kỳ ("Ngày dd/MM/yyyy" / "Tuần dd/MM–dd/MM/yyyy")
     * @param snapshotDate mốc snapshot dd/MM/yyyy
     * @param overview     tổng toàn phạm vi
     * @param byProject    gom theo dự án
     * @param byMember     gom theo nhân sự (assignee)
     */
    public record WorkReport(String periodType, String periodLabel, String snapshotDate,
                             ReportRow overview, List<ReportRow> byProject, List<ReportRow> byMember) {
    }
}
