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
