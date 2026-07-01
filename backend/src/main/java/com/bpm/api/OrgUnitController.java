package com.bpm.api;

import com.bpm.api.dto.OrgUnitDto;
import com.bpm.application.OrgUnitService;
import com.bpm.domain.org.OrgUnit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị cây cơ cấu tổ chức — chỉ ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/org-units")
public class OrgUnitController {

    private final OrgUnitService service;

    public OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<OrgUnitDto.Response> create(@Valid @RequestBody OrgUnitDto.CreateRequest req, Authentication auth) {
        OrgUnit u = service.create(req.name(), req.parentId(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrgUnitDto.Response.from(u));
    }

    @GetMapping
    public List<OrgUnitDto.Response> all() {
        return service.all().stream().map(OrgUnitDto.Response::from).toList();
    }

    @GetMapping("/roots")
    public List<OrgUnitDto.Response> roots() {
        return service.roots().stream().map(OrgUnitDto.Response::from).toList();
    }

    @GetMapping("/{id}/children")
    public List<OrgUnitDto.Response> children(@PathVariable String id) {
        return service.children(id).stream().map(OrgUnitDto.Response::from).toList();
    }

    @GetMapping("/{id}/ancestors")
    public List<OrgUnitDto.Response> ancestors(@PathVariable String id) {
        return service.ancestors(id).stream().map(OrgUnitDto.Response::from).toList();
    }

    @GetMapping("/{id}/descendants")
    public List<OrgUnitDto.Response> descendants(@PathVariable String id) {
        return service.descendants(id).stream().map(OrgUnitDto.Response::from).toList();
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<Void> rename(@PathVariable String id, @Valid @RequestBody OrgUnitDto.RenameRequest req, Authentication auth) {
        service.rename(id, req.name(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/parent")
    public ResponseEntity<Void> move(@PathVariable String id, @RequestBody OrgUnitDto.MoveRequest req, Authentication auth) {
        service.move(id, req.newParentId(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, actor(auth));
        return ResponseEntity.noContent().build();
    }
}
