package com.bpm.api.dto;

import com.bpm.domain.org.OrgUnit;
import jakarta.validation.constraints.NotBlank;

public class OrgUnitDto {

    public record CreateRequest(@NotBlank String name, String parentId) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record MoveRequest(String newParentId) {
    }

    public record Response(String id, String name, String parentId) {
        public static Response from(OrgUnit u) {
            return new Response(u.getId(), u.getName(), u.getParentId());
        }
    }
}
