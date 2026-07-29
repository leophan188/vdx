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
            String testerUserId, String testerName,
            /** Việc không cần qua kiểm thử (PM/BA) — FE ẩn bước kiểm thử cho task này. */
            boolean skipTest) {

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
                    String.valueOf(t.getSeq()),
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
                    t.getTesterUserId(), testerName, t.isSkipTest());
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
                    String.valueOf(t.getSeq()), t.getTitle(), t.getType().name(), t.getStatus().name(),
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
            String testerUserId,
            /** Việc không cần qua kiểm thử (PM/BA) — bỏ trống = cần kiểm thử như bình thường. */
            Boolean skipTest,
            /**
             * Giờ tester bỏ ra để TÌM & ghi nhận lỗi — BẮT BUỘC khi tạo BUG/ISSUE.
             * Ghi ngay trong lệnh tạo (không gọi API thứ hai) để không có cảnh tạo được lỗi
             * nhưng mất giờ vì lệnh sau thất bại.
             */
            Double testHours, String workDate) {
    }

    /**
     * Đổi trạng thái. {@code hours}/{@code workDate} BẮT BUỘC khi bàn giao sang Kiểm thử
     * (giờ dev) và khi chuyển Hoàn thành (giờ test) — nguồn dữ liệu cho timesheet.
     * {@code workDate} dạng yyyy-MM-dd, bỏ trống thì tính vào hôm nay.
     */
    public record StatusRequest(String status, Double hours, String workDate, String note) {
    }

    /** Một lần ghi giờ làm việc trên task. */
    public record WorkLogRequest(Double hours, String workDate, String role, String note) {
    }

    /** Giờ đã ghi — trả về cho chi tiết task và timesheet. */
    public record WorkLogResponse(String id, String taskId, String taskCode, String taskTitle,
                                  String userId, String userName, String role,
                                  String workDate, double hours, String note,
                                  /** Loại task (BUG/TASK/…) và chuỗi cha "Epic › Story › Task" — để timesheet
                                   *  hiện đủ ngữ cảnh như các màn khác, không chỉ mỗi tên việc. */
                                  String taskType, String parentPath, double estimateHours,
                                  /** Hành động sinh ra dòng giờ: LOG_BUG/HANDOVER/VERIFY_DONE/REOPEN/MANUAL. */
                                  String action) {
        public static WorkLogResponse of(com.bpm.domain.project.TaskWorkLog w, String taskCode, String taskTitle) {
            return of(w, taskCode, taskTitle, null, null, 0);
        }
        public static WorkLogResponse of(com.bpm.domain.project.TaskWorkLog w, String taskCode, String taskTitle,
                                         String taskType, String parentPath, double estimateHours) {
            return new WorkLogResponse(w.getId(), w.getTaskId(), taskCode, taskTitle,
                    w.getUserId(), w.getUserName(), w.getRole(),
                    w.getWorkDate() == null ? null : w.getWorkDate().toString(), w.getHours(), w.getNote(),
                    taskType, parentPath, estimateHours, w.getAction());
        }
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
            String uploadedBy, String uploadedAt, String url, String commentId) {

        public static AttachmentResponse of(com.bpm.domain.project.TaskAttachment a, String url) {
            return new AttachmentResponse(a.getId(), a.getTaskId(), a.getFileName(), a.getContentType(),
                    a.getSize(), a.getUploadedBy(),
                    a.getUploadedAt() == null ? null : a.getUploadedAt().toString(), url, a.getCommentId());
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
            String reporterUserId, String reporterName,
            /** Người KIỂM THỬ — để nhóm thống kê "tester duyệt xong" theo đúng vai. */
            String testerUserId, String testerName,
            /**
             * CHỦ HIỆN TẠI — ai đang thực sự giữ việc ở trạng thái này (xem
             * {@code ProjectReportService.ownerUserId}). Thống kê theo nhân sự gom theo
             * field này để việc đã bàn giao sang Kiểm thử không còn bị đếm cho dev.
             */
            String ownerUserId, String ownerName) {
    }

    /** Số liệu tổng quan của kỳ báo cáo. */
    public record ReportOverview(
            double completionPct, int totalTasks, int doneTasks,
            double totalEstimate, double doneEstimate, int overdueCount, int bugCount) {
    }

    /** Tỷ lệ hoàn thành theo nhân sự trong kỳ báo cáo. */
    /**
     * Thống kê theo nhân sự. {@code total/done/doing/todo/overdue/pct} là TOÀN DỰ ÁN;
     * {@code inPeriod/donePeriod} chỉ tính việc CÓ THAY ĐỔI trong kỳ Ngày/Tuần đang xem.
     */
    public record PersonProgress(String userId, String name,
                                 int total, int done, int doing, int todo, int overdue, double pct,
                                 int inPeriod, int donePeriod,
                                 /**
                                  * ĐÓNG GÓP TRONG KỲ theo VAI — mỗi task sinh 2 phần việc (dev + tester),
                                  * tính theo mốc RIÊNG của từng vai:
                                  * {@code devHandover} = task người này làm dev và đã bàn giao sang Kiểm thử trong kỳ;
                                  * {@code testerDone}  = task người này làm tester và đã chuyển Hoàn thành trong kỳ.
                                  * Cộng các dòng sẽ LỚN HƠN tổng task vì một task có 2 người tham gia.
                                  */
                                 int devHandover, int testerDone) {
    }

    /** Báo cáo ngày/tuần: nhãn kỳ + 4 nhóm task + tổng quan. */
    public record PeriodReportResponse(
            String periodLabel,
            List<ReportTaskItem> done, List<ReportTaskItem> inProgress,
            List<ReportTaskItem> upcoming, List<ReportTaskItem> overdue,
            ReportOverview overview,
            List<ReportTaskItem> epicStory,
            List<PersonProgress> byPerson,
            List<ReportTaskItem> bugsLogged,
            /** Việc XỬ LÝ TRONG KỲ (có thay đổi trong kỳ) — nguồn cho popup chi tiết theo nhân sự. */
            List<ReportTaskItem> periodItems,
            /** Task được DEV BÀN GIAO sang Kiểm thử trong kỳ — nguồn popup cột "Dev bàn giao". */
            List<ReportTaskItem> devHandoverItems,
            /** Task được TESTER chuyển Hoàn thành trong kỳ — nguồn popup cột "Tester duyệt". */
            List<ReportTaskItem> testerDoneItems,
            /**
             * CẦN LÀM — đã đến hạn TRONG KỲ nhưng trạng thái vẫn Cần làm/Backlog (chưa ai khởi động).
             * Ngày: hạn đúng hôm nay. Tuần: hạn nằm trong tuần đang xem.
             * Khác "Trễ hạn" (hạn đã QUA) và khác "Sắp làm" (hạn còn ở phía trước).
             */
            List<ReportTaskItem> todo) {
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
            List<String> teamUserIds, List<String> teamNames, List<DiaryPerson> team,
            String clientContacts, String content, String conclusion,
            String location, String startTime, String endTime, List<DiaryAction> nextActions,
            String createdBy, String createdByName, String createdAt, boolean canEdit) {
    }

    /** Tạo/sửa nhật ký. {@code workDate} chấp nhận dd/MM/yyyy hoặc yyyy-MM-dd. */
    public record DiaryRequest(
            String workDate, String category, List<String> teamUserIds,
            String clientContacts, String content, String conclusion,
            String location, String startTime, String endTime, List<DiaryAction> nextActions) {
    }

    /**
     * Một việc cần làm tiếp (next action) của buổi làm việc — in thành bảng trong biên bản họp.
     * {@code owner} là TEXT tự do (có thể là người phía khách hàng, không có trong hệ thống).
     * {@code dueDate} dd/MM/yyyy; {@code status} = NEW | DOING | DONE.
     */
    public record DiaryAction(String content, String owner, String dueDate, String status) {
    }

    /**
     * Một người tham dự trong biên bản họp: họ tên + vai trò.
     * Phía đơn vị thực hiện lấy vai trò TỪ HỆ THỐNG (vai trò trong dự án, fallback chức danh nhân sự);
     * phía khách hàng tách từ text tự do dạng "Nguyễn Văn A (Trưởng phòng)".
     */
    public record DiaryPerson(String name, String role) {
    }
}
