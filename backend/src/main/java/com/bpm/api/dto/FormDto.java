package com.bpm.api.dto;

import com.bpm.domain.form.FormDefinition;
import com.bpm.domain.form.FormVersion;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class FormDto {

    public record CreateRequest(@NotBlank String formKey, @NotBlank String name, String copyFromId) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record SchemaRequest(String schemaJson) {
    }

    public record Summary(String id, String formKey, String name, String status,
                          int publishedVersion, Instant updatedAt) {
        public static Summary from(FormDefinition f) {
            return new Summary(f.getId(), f.getFormKey(), f.getName(), f.getStatus().name(),
                    f.getPublishedVersion(), f.getUpdatedAt());
        }
    }

    public record Detail(String id, String formKey, String name, String status, int publishedVersion,
                         String schemaJson, Instant updatedAt) {
        public static Detail from(FormDefinition f) {
            return new Detail(f.getId(), f.getFormKey(), f.getName(), f.getStatus().name(),
                    f.getPublishedVersion(), f.getSchemaJson(), f.getUpdatedAt());
        }
    }

    public record Version(String id, int version, String status, Instant publishedAt, String publishedBy) {
        public static Version from(FormVersion v) {
            return new Version(v.getId(), v.getVersion(), v.getStatus().name(), v.getPublishedAt(), v.getPublishedBy());
        }
    }
}
