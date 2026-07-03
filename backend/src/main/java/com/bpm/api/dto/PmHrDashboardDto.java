package com.bpm.api.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO TỔNG HỢP cho Bảng điều khiển + Báo cáo theo trọng tâm DỰ ÁN + NHÂN SỰ
 * (hệ thống mini-Jira + HR). Phục vụ {@code GET /api/v1/dashboard/pm-hr}.
 *
 * <p>Số tiền là VND (Long), nỗ lực man-month (MM) làm tròn 2 chữ số, % effort là tổng effortPct
 * của nhân sự qua mọi dự án.
 */
public final class PmHrDashboardDto {

    private PmHrDashboardDto() {
    }

    /** Gói tổng: khối dự án + khối nhân sự + phân bổ nỗ lực theo người (cho báo cáo). */
    public record PmHrDashboard(
            ProjectStats project,
            List<ProjectRow> projects,
            HrStats hr,
            List<EffortByPerson> effortByPerson) {
    }

    // ===== Khối DỰ ÁN =====

    /** Số liệu tổng khối dự án (toàn hệ thống). */
    public record ProjectStats(
            int totalProjects,
            Map<String, Integer> byStatus,   // PLANNING/ACTIVE/ON_HOLD/DONE/CANCELLED -> count
            int totalTasks,
            int doneTasks,
            double avgCompletionPct,          // trung bình completionPct các dự án
            int overdueTasks,                 // dueDate < hôm nay & chưa DONE (toàn hệ thống)
            int openBugs,                     // type BUG/ISSUE & chưa DONE
            long totalBudget,                 // Σ budget
            double totalPlannedMM,            // Σ plannedEffortMm
            double totalActualMM) {           // Σ (Σ member.manday/22) mỗi dự án
    }

    /** Một dòng dự án trên bảng. */
    public record ProjectRow(
            String id, String code, String name, String status,
            double completionPct, int memberCount,
            double plannedMm, double actualMm, Long budget,
            int overdue, int bugOpen) {
    }

    // ===== Khối NHÂN SỰ =====

    /** Số liệu tổng khối nhân sự. */
    public record HrStats(
            int totalEmployees,
            int active,
            int inactive,
            int external,
            Map<String, Integer> byDept,      // deptCode -> count (top, đã sắp xếp giảm dần)
            Map<String, Integer> byLevel,     // level -> count
            List<Overloaded> overloaded,      // ΣeffortPct > 100
            int unassigned,                   // nhân sự active KHÔNG join dự án nào
            List<PersonRef> availableSample) {// vài người chưa join (gợi ý)
    }

    /** Nhân sự quá tải (tổng effort > 100%) + các dự án đang tham gia. */
    public record Overloaded(
            String empCode, String name, int totalEffort, List<String> projects) {
    }

    /** Tham chiếu nhân sự gọn. */
    public record PersonRef(String empCode, String name, String deptCode, String jobPosition, String title) {
    }

    /** Phân bổ nỗ lực theo nhân sự (chỉ người tham gia ≥1 dự án) — dùng cho báo cáo. */
    public record EffortByPerson(
            String empCode, String name, String deptCode,
            int projectsCount, int totalEffort, int totalManday, double totalMM) {
    }
}
