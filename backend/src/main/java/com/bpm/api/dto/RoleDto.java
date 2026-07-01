package com.bpm.api.dto;

import com.bpm.domain.role.Role;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public class RoleDto {

    public record CreateRequest(@NotBlank String code, @NotBlank String name, Set<String> permissions) {
    }

    public record AssignRequest(@NotBlank String positionId, @NotBlank String roleCode) {
    }

    public record UpdateRequest(@NotBlank String name, Set<String> permissions) {
    }

    public record Response(String code, String name, Set<String> permissions) {
        public static Response from(Role r) {
            return new Response(r.getCode(), r.getName(), r.getPermissions());
        }
    }
}
