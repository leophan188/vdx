package com.bpm.api;

import com.bpm.api.dto.ProcessDto;
import com.bpm.application.ProcessService;
import com.bpm.domain.process.ProcessDefinition;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị định nghĩa quy trình (Story 2.1) — chỉ ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/processes")
public class ProcessController {

    private final ProcessService service;

    public ProcessController(ProcessService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<ProcessDto.Summary> create(@Valid @RequestBody ProcessDto.CreateRequest req, Authentication auth) {
        ProcessDefinition p = service.create(req.processKey(), req.name(), req.copyFromId(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProcessDto.Summary.from(p));
    }

    @GetMapping
    public List<ProcessDto.Summary> list() {
        return service.list().stream().map(ProcessDto.Summary::from).toList();
    }

    @GetMapping("/{id}")
    public ProcessDto.Detail get(@PathVariable String id) {
        return ProcessDto.Detail.from(service.get(id));
    }

    @PatchMapping("/{id}")
    public ProcessDto.Summary rename(@PathVariable String id, @Valid @RequestBody ProcessDto.RenameRequest req, Authentication auth) {
        return ProcessDto.Summary.from(service.rename(id, req.name(), actor(auth)));
    }

    @PutMapping("/{id}/design")
    public ProcessDto.Detail saveDesign(@PathVariable String id, @RequestBody ProcessDto.DesignRequest req, Authentication auth) {
        return ProcessDto.Detail.from(service.saveDesign(id, req.bpmnXml(), req.stepsMetaJson(), actor(auth)));
    }

    @PostMapping("/{id}/publish")
    public ProcessDto.Version publish(@PathVariable String id, Authentication auth) {
        return ProcessDto.Version.from(service.publish(id, actor(auth)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<Void> retire(@PathVariable String id, Authentication auth) {
        service.retire(id, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<ProcessDto.Version> versions(@PathVariable String id) {
        return service.listVersions(id).stream().map(ProcessDto.Version::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, actor(auth));
        return ResponseEntity.noContent().build();
    }
}
