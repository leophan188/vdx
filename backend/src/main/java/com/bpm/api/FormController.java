package com.bpm.api;

import com.bpm.api.dto.FormDto;
import com.bpm.application.FormService;
import com.bpm.domain.form.FormDefinition;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quản trị biểu mẫu động (Story 2.6) — chỉ ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/forms")
public class FormController {

    private final FormService service;

    public FormController(FormService service) {
        this.service = service;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @PostMapping
    public ResponseEntity<FormDto.Summary> create(@Valid @RequestBody FormDto.CreateRequest req, Authentication auth) {
        FormDefinition f = service.create(req.formKey(), req.name(), actor(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(FormDto.Summary.from(f));
    }

    @GetMapping
    public List<FormDto.Summary> list() {
        return service.list().stream().map(FormDto.Summary::from).toList();
    }

    @GetMapping("/{id}")
    public FormDto.Detail get(@PathVariable String id) {
        return FormDto.Detail.from(service.get(id));
    }

    @PatchMapping("/{id}")
    public FormDto.Summary rename(@PathVariable String id, @Valid @RequestBody FormDto.RenameRequest req, Authentication auth) {
        return FormDto.Summary.from(service.rename(id, req.name(), actor(auth)));
    }

    @PutMapping("/{id}/schema")
    public FormDto.Detail saveSchema(@PathVariable String id, @RequestBody FormDto.SchemaRequest req, Authentication auth) {
        return FormDto.Detail.from(service.saveSchema(id, req.schemaJson(), actor(auth)));
    }

    @PostMapping("/{id}/publish")
    public FormDto.Version publish(@PathVariable String id, Authentication auth) {
        return FormDto.Version.from(service.publish(id, actor(auth)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<Void> retire(@PathVariable String id, Authentication auth) {
        service.retire(id, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<FormDto.Version> versions(@PathVariable String id) {
        return service.listVersions(id).stream().map(FormDto.Version::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, actor(auth));
        return ResponseEntity.noContent().build();
    }
}
