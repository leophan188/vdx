package com.bpm.api.dto;

import com.bpm.domain.position.Position;
import jakarta.validation.constraints.NotBlank;

public class PositionDto {

    public record CreateRequest(@NotBlank String title, @NotBlank String orgUnitId) {
    }

    public record AssignRequest(@NotBlank String userId) {
    }

    public record UpdateRequest(@NotBlank String title) {
    }

    public record Response(String id, String title, String orgUnitId, String currentHolderUserId) {
        public static Response from(Position p) {
            return new Response(p.getId(), p.getTitle(), p.getOrgUnitId(), p.getCurrentHolderUserId());
        }
    }
}
