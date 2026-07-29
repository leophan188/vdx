package com.bpm.api.dto;

import com.bpm.domain.personal.PersonalTask;

import java.time.format.DateTimeFormatter;

/**
 * DTO cho BACKLOG CÁ NHÂN (/api/v1/me/tasks*).
 * Ngày (deadline) dạng ISO {@code yyyy-MM-dd} (chuỗi, nullable); metadata ISO Instant (chuỗi).
 */
public final class MeTaskDto {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    private MeTaskDto() {
    }

    /** Task RIÊNG của tôi (không thuộc dự án). */
    public record PersonalTaskView(
            String id, String title, double estimateHours, String deadline,
            String status, String note, String createdAt) {

        public static PersonalTaskView of(PersonalTask t) {
            return new PersonalTaskView(
                    t.getId(), t.getTitle(), t.getEstimateHours(),
                    t.getDeadline() == null ? null : t.getDeadline().format(ISO),
                    t.getStatus(), t.getNote(),
                    t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
        }
    }

    /**
     * Tạo task cá nhân hoặc task gắn dự án.
     * {@code deadline} ISO yyyy-MM-dd (nullable). {@code projectId} rỗng/null → task RIÊNG;
     * có giá trị → tạo task trong backlog chung của dự án, giao cho chính tôi.
     */
    public record CreateRequest(
            String title, Double estimateHours, String deadline, String projectId) {
    }

    /** Sửa task cá nhân (chỉ chủ sở hữu). deadline ISO yyyy-MM-dd (nullable). */
    public record UpdateRequest(
            String title, Double estimateHours, String deadline, String status) {
    }

    /** Đổi trạng thái task cá nhân. */
    public record StatusRequest(String status) {
    }

    /**
     * Kết quả tạo task.
     * {@code source}="PERSONAL" → {@code personal} có giá trị.
     * {@code source}="PROJECT" → {@code projectTaskId} + {@code projectId} có giá trị (task vào backlog chung).
     */
    public record CreateResult(
            String source, PersonalTaskView personal, String projectTaskId, String projectId) {

        public static CreateResult personal(PersonalTaskView view) {
            return new CreateResult("PERSONAL", view, null, null);
        }

        public static CreateResult project(String projectTaskId, String projectId) {
            return new CreateResult("PROJECT", null, projectTaskId, projectId);
        }
    }

    /** Dự án tôi là thành viên (đổ vào dropdown khi thêm task gắn dự án). */
    public record ProjectOption(String id, String code, String name) {
    }

    // ===== Log bug/issue CÁ NHÂN (/api/v1/me/bugs) =====

    /**
     * Một BUG/ISSUE LIÊN QUAN tới tôi (xuyên dự án).
     * {@code role}: REPORTER (tôi log), ASSIGNEE (được giao cho tôi), BOTH (cả hai).
     * Ngày dueDate dạng dd/MM/yyyy (chuỗi, nullable).
     */
    public record MyBugView(
            String id, String projectId, String projectCode, String projectName, String code,
            String title, String type, String status, String priority,
            String assigneeUserId, String assigneeName,
            String reporterUserId, String reporterName,
            String testerUserId, String testerName,
            String dueDate, String role) {
    }

    /**
     * Log một BUG/ISSUE cá nhân vào dự án tôi là thành viên.
     * {@code type} = "BUG" | "ISSUE". {@code assigneeUserId} = PIC (nullable).
     * {@code dueDate} ISO yyyy-MM-dd (nullable). reporter tự set = tôi.
     */
    public record LogBugRequest(
            String projectId, String type, String title, String description,
            String priority, String assigneeUserId, Double estimateHours, String startDate, String dueDate,
            String testerUserId, String parentId,
            String severity, String screen, String environment,
            String stepsToReproduce, String expectedResult, String actualResult,
            /** Việc không cần qua kiểm thử (PM/BA). */
            Boolean skipTest,
            /** Giờ tìm & ghi nhận lỗi — bắt buộc khi log BUG/ISSUE. */
            Double testHours, String workDate) {
    }
}
