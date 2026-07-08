package com.bpm.api.dto;

import java.util.List;
import java.util.Map;

/** DTO hộp thư việc & chi tiết/hành động (Story 3.2/3.4). */
public class TaskDto {

    /**
     * Một dòng trong hộp thư. dueAt/overdue = hạn SLA (3.7); claimable = việc theo vai trò chờ nhận (3.x).
     * stepIndex/stepTotal = vị trí bước hiện tại trong tổng thể quy trình (vd "Bước 6/10").
     */
    public record InboxItem(String taskId, String stepName, String processName,
                            String instanceId, String createdAt, String formId, Integer slaHours,
                            String dueAt, boolean overdue, boolean claimable,
                            int stepIndex, int stepTotal) {
    }

    /**
     * Chi tiết việc để FE dựng màn xử lý (form + quyền trường + hành động + dữ liệu hiện có).
     * priorSteps = dữ liệu các BƯỚC TRƯỚC đã hoàn thành (để người xử lý xem lại ngữ cảnh).
     */
    public record Detail(String taskId, String stepName, String stepKey, String processName, String instanceId,
                         String formId, String formSchemaJson, int processVersion, Object fieldPerms,
                         List<String> actions, Map<String, Object> formData, List<StepView> priorSteps,
                         String priorEditFieldsJson) {
    }

    /** Yêu cầu hoàn thành việc: hành động đã chọn + dữ liệu form. */
    public record CompleteRequest(String action, Map<String, Object> formData) {
    }

    /**
     * Một dòng theo dõi phiên chạy. title = tiêu đề từ form (4.5); searchText = mọi giá trị field (4.1).
     * currentStatus = trạng thái nghiệp vụ của bước đang mở (statusLabel cấu hình), null nếu không có.
     */
    public record InstanceListItem(String id, String processName, String title, int processVersion, String status,
                                   String startedBy, String startedAt, String currentStep, String currentStatus,
                                   String currentAssignee, boolean currentOverdue, String searchText) {
    }

    /** Một bước trong dòng thời gian (Story 3.3). status: DONE | ACTIVE. */
    public record StepHistory(String stepName, String assignee, String startedAt, String endedAt, String status) {
    }

    /** Dòng thời gian đầy đủ của một phiên chạy. */
    public record InstanceTimeline(String id, String processName, String status, List<StepHistory> steps) {
    }

    /** Một cặp Nhãn–Giá trị dữ liệu đã nhập ở một bước. */
    public record FieldValue(String label, String value) {
    }

    /**
     * Một bước trong TỔNG QUAN quy trình (toàn bộ các bước, kể cả chưa tới).
     * status: DONE | ACTIVE | PENDING. data = dữ liệu đã nhập ở bước (nếu có).
     */
    public record StepView(int index, String stepKey, String stepName, String assignee,
                           String startedAt, String endedAt, String status, List<FieldValue> data) {
    }

    /** Tổng quan một phiên chạy: đủ các bước theo thứ tự + trạng thái + dữ liệu từng bước (để xem theo bước). */
    public record InstanceOverview(String id, String processName, String title, String status,
                                   int total, int currentIndex, int version, List<StepView> steps) {
    }

    /** Yêu cầu hủy phiên chạy (Story 3.6). */
    public record CancelRequest(String reason) {
    }

    /** Yêu cầu gia hạn việc (Story 3.8): số giờ + lý do. */
    public record ExtendRequest(int hours, String reason) {
    }
}
