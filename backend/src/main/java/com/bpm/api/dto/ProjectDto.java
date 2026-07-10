package com.bpm.api.dto;

import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectTask;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** DTO module Quản lý dự án (mini-Jira). Ngày nghiệp vụ dạng dd/MM/yyyy (chuỗi); metadata ISO Instant (chuỗi). */
public final class ProjectDto {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ProjectDto() {
    }

    // ===== Project =====

    /**
     * Bản dự án + số liệu tổng hợp (list kèm completionPct/memberCount/taskCount; chi tiết kèm ownerName).
     * {@code budget} = ngân sách (VND, nullable). {@code totalEffortMM} = tổng nỗ lực man-month
     * = Σ(member.manday) / 22 (làm tròn 2 chữ số).
     */
    public record ProjectResponse(
            String id, String code, String name, String description, String status,
            String startDate, String dueDate, String ownerUserId, String ownerName,
            Long budget, Double plannedEffortMm, double completionPct, int memberCount, int taskCount,
            double totalEffortMM,
            String createdAt, String updatedAt) {

        public static ProjectResponse of(Project p, String ownerName,
                                         double completionPct, int memberCount, int taskCount) {
            return of(p, ownerName, completionPct, memberCount, taskCount, 0.0);
        }

        public static ProjectResponse of(Project p, String ownerName,
                                         double completionPct, int memberCount, int taskCount,
                                         double totalEffortMM) {
            return new ProjectResponse(
                    p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getStatus().name(),
                    p.getStartDate() == null ? null : p.getStartDate().format(DMY),
                    p.getDueDate() == null ? null : p.getDueDate().format(DMY),
                    p.getOwnerUserId(), ownerName, p.getBudget(), p.getPlannedEffortMm(),
                    completionPct, memberCount, taskCount, totalEffortMM,
                    p.getCreatedAt() == null ? null : p.getCreatedAt().toString(),
                    p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString());
        }
    }

    /** Tạo/sửa dự án. budget VND nullable; plannedEffortMm = nỗ lực KẾ HOẠCH (man-month, nhập tay). */
    public record ProjectRequest(
            String code, String name, String description, String status,
            String startDate, String dueDate, String ownerUserId, Long budget, Double plannedEffortMm) {
    }

    // ===== Member =====

    public record MemberResponse(
            String id, String projectId, String userId, String name, String empCode,
            String jobPosition, String title, String deptCode,
            String roleInProject, String startDate, String endDate,
            int effortPct, int workdays, int manday, String joinedAt) {

        public static MemberResponse of(ProjectMember m, String name, String empCode,
                                        String jobPosition, String title, String deptCode) {
            return new MemberResponse(m.getId(), m.getProjectId(), m.getUserId(), name, empCode,
                    jobPosition, title, deptCode,
                    m.getRoleInProject().name(),
                    m.getStartDate() == null ? null : m.getStartDate().format(DMY),
                    m.getEndDate() == null ? null : m.getEndDate().format(DMY),
                    m.getEffortPct(), m.workdays(), m.manday(),
                    m.getJoinedAt() == null ? null : m.getJoinedAt().toString());
        }
    }

    /** Thêm thành viên {userId, role, ngày dd/MM/yyyy, effortPct 1–100 → man-day = ngày công × %}. */
    public record AddMemberRequest(String userId, String role, String startDate, String endDate, Integer effortPct) {
    }

    /** Sửa thành viên — KHÔNG đổi người, chỉ vai trò/ngày/%effort. */
    public record UpdateMemberRequest(String role, String startDate, String endDate, Integer effortPct) {
    }

    // ===== Task =====

    /**
     * Công việc đầy đủ field (phẳng — FE tự dựng cây). code = "PRJ-12".
     * {@code progressPct} (0..100): % hoàn thành của riêng task này — task lá DONE=100/khác=0;
     * task cha = rollup theo estimateHours của lá DONE trong cây con (xem ProjectTaskService).
     */
    /** Một mắt xích cha của task (gốc → cha trực tiếp): loại + mã + tiêu đề. */
    public record ParentRef(String type, String code, String title) {
    }

    public record TaskResponse(
            String id, String projectId, String parentId, int seq, String code,
            String title, String description, String type, String status, String priority,
            String assigneeUserId, String assigneeName,
            String assigneeCode, String assigneePosition, String assigneeTitle, String assigneeDept,
            double estimateHours, double spentHours,
            String startDate, String dueDate, int orderIndex, String screen,
            String severity, String stepsToReproduce, String expectedResult,
            String actualResult, String environment,
            boolean leaf, double progressPct, List<ParentRef> parentChain,
            String createdAt, String updatedAt, String createdBy,
            String reporterUserId, String reporterName,
            String testerUserId, String testerName) {

        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName, boolean leaf) {
            return of(t, projectCode, assigneeName, null, null, null, null,
                    leaf, leaf && t.getStatus() == com.bpm.domain.project.TaskStatus.DONE ? 100.0 : 0.0, List.of());
        }

        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName,
                                      boolean leaf, double progressPct) {
            return of(t, projectCode, assigneeName, null, null, null, null, leaf, progressPct, List.of());
        }

        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName,
                                      String assigneeCode, String assigneePosition, String assigneeTitle, String assigneeDept,
                                      boolean leaf, double progressPct) {
            return of(t, projectCode, assigneeName, assigneeCode, assigneePosition, assigneeTitle, assigneeDept,
                    leaf, progressPct, List.of());
        }

        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName,
                                      String assigneeCode, String assigneePosition, String assigneeTitle, String assigneeDept,
                                      boolean leaf, double progressPct, List<ParentRef> parentChain) {
            return of(t, projectCode, assigneeName, assigneeCode, assigneePosition, assigneeTitle, assigneeDept,
                    leaf, progressPct, parentChain, null);
        }

        /** Bản đầy đủ: kèm reporterName (người LOG). reporterUserId/testerUserId lấy trực tiếp từ entity; testerName=null. */
        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName,
                                      String assigneeCode, String assigneePosition, String assigneeTitle, String assigneeDept,
                                      boolean leaf, double progressPct, List<ParentRef> parentChain,
                                      String reporterName) {
            return of(t, projectCode, assigneeName, assigneeCode, assigneePosition, assigneeTitle, assigneeDept,
                    leaf, progressPct, parentChain, reporterName, null);
        }

        /** Bản đầy đủ nhất: kèm reporterName (người LOG) + testerName (người kiểm thử). Các userId lấy từ entity. */
        public static TaskResponse of(ProjectTask t, String projectCode, String assigneeName,
                                      String assigneeCode, String assigneePosition, String assigneeTitle, String assigneeDept,
                                      boolean leaf, double progressPct, List<ParentRef> parentChain,
                                      String reporterName, String testerName) {
            return new TaskResponse(
                    t.getId(), t.getProjectId(), t.getParentId(), t.getSeq(),
                    projectCode + "-" + t.getSeq(),
                    t.getTitle(), t.getDescription(), t.getType().name(), t.getStatus().name(),
                    t.getPriority().name(), t.getAssigneeUserId(), assigneeName,
                    assigneeCode, assigneePosition, assigneeTitle, assigneeDept,
                    t.getEstimateHours(), t.getSpentHours(),
                    t.getStartDate() == null ? null : t.getStartDate().format(DMY),
                    t.getDueDate() == null ? null : t.getDueDate().format(DMY),
                    t.getOrderIndex(), t.getScreen(),
                    t.getSeverity() == null ? null : t.getSeverity().name(),
                    t.getStepsToReproduce(), t.getExpectedResult(), t.getActualResult(), t.getEnvironment(),
                    leaf, progressPct,
                    parentChain == null ? List.of() : parentChain,
                    t.getCreatedAt() == null ? null : t.getCreatedAt().toString(),
                    t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString(),
                    t.getCreatedBy(),
                    t.getReporterUserId(), reporterName,
                    t.getTesterUserId(), testerName);
        }
    }

    // ===== Lịch sử / hoạt động task (kiểu Jira) =====

    public record TaskActivityResponse(
            String id, String taskId, String actorName, String action, String detail, String createdAt) {

        public static TaskActivityResponse of(com.bpm.domain.project.TaskActivity a) {
            return new TaskActivityResponse(a.getId(), a.getTaskId(), a.getActorName(),
                    a.getAction(), a.getDetail(),
                    a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        }
    }

    /**
     * Một dòng nhật ký hoạt động cấp DỰ ÁN (kiểu Jira "Activity stream").
     * {@code taskCode}/{@code taskTitle} có thể null nếu task đã bị xoá. {@code createdAt} = Instant.toString().
     */
    public record ProjectActivityItem(
            String id, String taskId, String taskCode, String taskTitle,
            String actorName, String action, String detail, String createdAt) {
    }

    /** Log work: {hours} (giờ) — cộng dồn vào spentHours của task. */
    public record LogWorkRequest(Double hours) {
    }

    /** Một việc của TÔI (xuyên dự án) — màn cá nhân. */
    public record MyTaskResponse(
            String taskId, String projectId, String projectCode, String projectName, String code,
            String title, String type, String status, String priority,
            double estimateHours, String dueDate, double progressPct,
            String assigneeCode) {
        public static MyTaskResponse of(ProjectTask t, String projectCode, String projectName, double progressPct) {
            return of(t, projectCode, projectName, progressPct, null);
        }

        public static MyTaskResponse of(ProjectTask t, String projectCode, String projectName,
                                        double progressPct, String assigneeCode) {
            return new MyTaskResponse(t.getId(), t.getProjectId(), projectCode, projectName,
                    projectCode + "-" + t.getSeq(), t.getTitle(), t.getType().name(), t.getStatus().name(),
                    t.getPriority().name(), t.getEstimateHours(),
                    t.getDueDate() == null ? null : t.getDueDate().format(DMY), progressPct, assigneeCode);
        }
    }

    /** Tạo/sửa task. parentId nullable; ngày dd/MM/yyyy. Khi tạo, các enum mặc định nếu để trống. */
    public record TaskRequest(
            String parentId, String title, String description, String type, String status, String priority,
            String assigneeUserId, Double estimateHours, String startDate, String dueDate,
            Integer orderIndex, String screen,
            String severity, String stepsToReproduce, String expectedResult,
            String actualResult, String environment,
            String testerUserId) {
    }

    public record StatusRequest(String status) {
    }

    public record AssigneeRequest(String assigneeUserId) {
    }

    /** Một mục reorder: {taskId, parentId(nullable), orderIndex}. */
    public record ReorderItem(String taskId, String parentId, int orderIndex) {
    }

    public record ReorderRequest(List<ReorderItem> items) {
    }

    // ===== Report =====

    public record AssigneeStat(String userId, String name, int total, int done,
                               int backlog, int todo, int doing, int review, int cancel, double estimate) {
    }

    /** Báo cáo dự án: tỷ lệ hoàn thành + phân rã theo trạng thái/loại/người + số liệu est + bug + quá hạn. */
    public record ReportResponse(
            double completionPct,
            int totalTasks, int doneTasks, int leafTasks, int leafDoneTasks,
            double totalEstimate, double doneEstimate, double totalSpent, int bugCount, int overdue,
            Map<String, Integer> byStatus, Map<String, Integer> byType,
            List<AssigneeStat> byAssignee) {
    }

    // ===== People (chọn thành viên / assignee) =====

    public record PersonResponse(String userId, String name, String empCode,
                                 String jobPosition, String title, String deptCode) {
    }

    // ===== Bình luận task (kiểu Jira) =====

    public record CommentResponse(
            String id, String taskId, String authorId, String authorName, String body,
            boolean edited, boolean mine,
            String createdAt, String updatedAt) {

        public static CommentResponse of(com.bpm.domain.project.TaskComment c, boolean mine) {
            return new CommentResponse(c.getId(), c.getTaskId(), c.getAuthorId(), c.getAuthorName(),
                    c.getBody(), c.isEdited(), mine,
                    c.getCreatedAt() == null ? null : c.getCreatedAt().toString(),
                    c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
        }
    }

    /** Thêm/sửa bình luận: {body}. */
    public record CommentRequest(String body) {
    }

    // ===== Ảnh đính kèm task (kiểu Jira) =====

    public record AttachmentResponse(
            String id, String taskId, String fileName, String contentType, long size,
            String uploadedBy, String uploadedAt, String url) {

        public static AttachmentResponse of(com.bpm.domain.project.TaskAttachment a, String url) {
            return new AttachmentResponse(a.getId(), a.getTaskId(), a.getFileName(), a.getContentType(),
                    a.getSize(), a.getUploadedBy(),
                    a.getUploadedAt() == null ? null : a.getUploadedAt().toString(), url);
        }
    }

    // ===== Báo cáo ngày / tuần =====

    /** Một dòng tóm tắt task trong báo cáo ngày/tuần. */
    public record ReportTaskItem(
            String taskId, String code, String title, String type, String status,
            String assigneeName, double estimateHours, String startDate, String dueDate, double progressPct,
            String priority, String severity, String assigneeUserId,
            /** Chuỗi cha "Epic: … › Story: …" (null nếu là gốc) — làm rõ ngữ cảnh công việc. */
            String parentPath,
            /** Người LOG (tester tạo bug/việc) — để thống kê "tester log nhiều bug". */
            String reporterUserId, String reporterName) {
    }

    /** Số liệu tổng quan của kỳ báo cáo. */
    public record ReportOverview(
            double completionPct, int totalTasks, int doneTasks,
            double totalEstimate, double doneEstimate, int overdueCount, int bugCount) {
    }

    /** Tỷ lệ hoàn thành theo nhân sự trong kỳ báo cáo. */
    public record PersonProgress(String userId, String name,
                                 int total, int done, int doing, int todo, int overdue, double pct) {
    }

    /** Báo cáo ngày/tuần: nhãn kỳ + 4 nhóm task + tổng quan. */
    public record PeriodReportResponse(
            String periodLabel,
            List<ReportTaskItem> done, List<ReportTaskItem> inProgress,
            List<ReportTaskItem> upcoming, List<ReportTaskItem> overdue,
            ReportOverview overview,
            List<ReportTaskItem> epicStory,
            List<PersonProgress> byPerson) {
    }

    // ===== Burndown (req: biểu đồ cháy việc) =====

    /** Một mốc trên đường burndown: ngày dd/MM/yyyy + ideal (kế hoạch) + actual (thực tế còn lại) — giờ. */
    public record BurndownPoint(String date, double ideal, double actual) {
    }

    /**
     * Dữ liệu biểu đồ burndown của một dự án.
     * {@code unit} = "day" hoặc "week" (lấy mẫu theo ngày, nếu khoảng > 60 ngày thì theo tuần).
     * {@code totalEstimate} = Σ estimateHours task LÁ; {@code totalSpent} = Σ spentHours task LÁ;
     * {@code teamManday} = Σ manday của các thành viên dự án.
     */
    public record BurndownResponse(
            String startDate, String dueDate,
            double totalEstimate, double totalSpent, int teamManday,
            String unit, List<BurndownPoint> points) {
    }

    // ===== Nhật ký dự án (ghi tay buổi làm việc với khách hàng) =====

    /**
     * Một bản ghi nhật ký dự án. {@code workDate} dạng dd/MM/yyyy; {@code createdAt} là ISO Instant.
     * {@code teamNames} là tên hiển thị đã resolve từ {@code teamUserIds}. {@code canEdit} = creator/admin/PM.
     */
    public record DiaryEntry(
            String id, String workDate, String category,
            List<String> teamUserIds, List<String> teamNames,
            String clientContacts, String content, String conclusion,
            String createdBy, String createdByName, String createdAt, boolean canEdit) {
    }

    /** Tạo/sửa nhật ký. {@code workDate} chấp nhận dd/MM/yyyy hoặc yyyy-MM-dd. */
    public record DiaryRequest(
            String workDate, String category, List<String> teamUserIds,
            String clientContacts, String content, String conclusion) {
    }
}
