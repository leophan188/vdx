package com.bpm.api;

import com.bpm.api.dto.RoleDto;
import com.bpm.application.RoleService;
import com.bpm.domain.role.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị vai trò & gán vai trò cho vị trí — chỉ ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<RoleDto.Response> create(@Valid @RequestBody RoleDto.CreateRequest req, Authentication auth) {
        Role r = service.createRole(req.code(), req.name(), req.permissions(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleDto.Response.from(r));
    }

    @GetMapping
    public List<RoleDto.Response> list() {
        return service.listRoles().stream().map(RoleDto.Response::from).toList();
    }

    @PostMapping("/assign")
    public ResponseEntity<Void> assign(@Valid @RequestBody RoleDto.AssignRequest req, Authentication auth) {
        service.assignRoleToPosition(req.positionId(), req.roleCode(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{code}")
    public ResponseEntity<RoleDto.Response> update(@PathVariable String code,
                                                   @Valid @RequestBody RoleDto.UpdateRequest req, Authentication auth) {
        Role r = service.updateRole(code, req.name(), req.permissions(), actor(auth));
        return ResponseEntity.ok(RoleDto.Response.from(r));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code, Authentication auth) {
        service.deleteRole(code, actor(auth));
        return ResponseEntity.noContent().build();
    }
}
