package com.bpm.api;

import com.bpm.api.dto.PositionDto;
import com.bpm.application.PositionService;
import com.bpm.application.SubstituteService;
import com.bpm.domain.position.Position;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị vị trí/chức danh & người thay thế — chỉ ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final PositionService service;
    private final SubstituteService substituteService;

    public PositionController(PositionService service, SubstituteService substituteService) {
        this.service = service;
        this.substituteService = substituteService;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<PositionDto.Response> create(@Valid @RequestBody PositionDto.CreateRequest req, Authentication auth) {
        Position p = service.create(req.title(), req.orgUnitId(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(PositionDto.Response.from(p));
    }

    @GetMapping
    public List<PositionDto.Response> byOrgUnit(@RequestParam String orgUnitId) {
        return service.byOrgUnit(orgUnitId).stream().map(PositionDto.Response::from).toList();
    }

    @GetMapping("/all")
    public List<PositionDto.Response> all() {
        return service.all().stream().map(PositionDto.Response::from).toList();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PositionDto.Response> update(@PathVariable String id,
                                                       @Valid @RequestBody PositionDto.UpdateRequest req,
                                                       Authentication auth) {
        Position p = service.updateTitle(id, req.title(), actor(auth));
        return ResponseEntity.ok(PositionDto.Response.from(p));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/holder")
    public ResponseEntity<Void> assign(@PathVariable String id, @Valid @RequestBody PositionDto.AssignRequest req, Authentication auth) {
        service.assignHolder(id, req.userId(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/substitute")
    public ResponseEntity<Void> setSubstitute(@PathVariable String id, @Valid @RequestBody PositionDto.AssignRequest req, Authentication auth) {
        substituteService.setSubstitute(id, req.userId(), actor(auth));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/substitute")
    public ResponseEntity<Void> clearSubstitute(@PathVariable String id, Authentication auth) {
        substituteService.clearSubstitute(id, actor(auth));
        return ResponseEntity.noContent().build();
    }
}
