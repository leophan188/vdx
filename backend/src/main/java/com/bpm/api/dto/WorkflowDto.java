package com.bpm.api.dto;

import com.bpm.domain.workflow.WorkflowInstance;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public class WorkflowDto {

    public record StartRequest(@NotBlank String processId, Map<String, Object> formData) {
    }

    /** Quy trình đã ban hành mà người dùng có thể tạo yêu cầu (màn "Tạo yêu cầu"). */
    public record StartableProcess(String id, String processKey, String name) {
    }

    /**
     * Biểu mẫu bước ĐẦU của một quy trình — LẤY MÀ KHÔNG tạo instance (để nhập nháp; chỉ tạo hồ sơ khi Gửi
     * hoặc khi mở soạn thảo tài liệu). Trùng các trường quan trọng của TaskDto.Detail để FE dùng chung.
     */
    public record StartForm(String processId, String processName, int processVersion,
                            String stepKey, String stepName, String formId, String formSchemaJson,
                            Map<String, String> fieldPerms, java.util.List<String> actions, boolean officeDoc) {
    }

    public record InstanceResponse(String id, String processId, int processVersion,
                                   String flowableInstanceId, String status, Instant startedAt,
                                   String firstTaskId) {
        public static InstanceResponse from(WorkflowInstance w) {
            return from(w, null);
        }

        public static InstanceResponse from(WorkflowInstance w, String firstTaskId) {
            return new InstanceResponse(w.getId(), w.getProcessId(), w.getProcessVersion(),
                    w.getFlowableInstanceId(), w.getStatus(), w.getStartedAt(), firstTaskId);
        }
    }
}
