package com.bpm.api.dto;

import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.assignment.DelegationKind;
import com.bpm.domain.assignment.TaskAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class AssignmentDto {

    public record AssignRequest(@NotBlank String taskId, @NotBlank String positionId) {
    }

    public record ReassignRequest(@NotBlank String toUserId, @NotNull DelegationKind kind, String reason) {
    }

    public record ClaimRequest(@NotBlank String toUserId) {
    }

    public record Response(String id, String taskId, String positionId, String assigneeUserId,
                           String candidateGroupOrgUnitId, String delegatedFromUserId,
                           AssignmentStatus status, Instant assignedAt) {
        public static Response from(TaskAssignment a) {
            return new Response(a.getId(), a.getTaskId(), a.getPositionId(), a.getAssigneeUserId(),
                    a.getCandidateGroupOrgUnitId(), a.getDelegatedFromUserId(), a.getStatus(), a.getAssignedAt());
        }
    }
}
