package com.bpm.api.dto;

import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.process.ProcessVersion;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class ProcessDto {

    public record CreateRequest(@NotBlank String processKey, @NotBlank String name) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record DesignRequest(String bpmnXml, String stepsMetaJson) {
    }

    /** Bản tóm tắt cho danh sách. */
    public record Summary(String id, String processKey, String name, String status,
                          int publishedVersion, Instant updatedAt) {
        public static Summary from(ProcessDefinition p) {
            return new Summary(p.getId(), p.getProcessKey(), p.getName(), p.getStatus().name(),
                    p.getPublishedVersion(), p.getUpdatedAt());
        }
    }

    /** Bản đầy đủ cho designer (kèm XML + metadata). */
    public record Detail(String id, String processKey, String name, String status,
                         int publishedVersion, String bpmnXml, String stepsMetaJson, Instant updatedAt) {
        public static Detail from(ProcessDefinition p) {
            return new Detail(p.getId(), p.getProcessKey(), p.getName(), p.getStatus().name(),
                    p.getPublishedVersion(), p.getBpmnXml(), p.getStepsMetaJson(), p.getUpdatedAt());
        }
    }

    /** Một phiên bản đã ban hành (lịch sử). */
    public record Version(String id, int version, String status, Instant publishedAt, String publishedBy) {
        public static Version from(ProcessVersion v) {
            return new Version(v.getId(), v.getVersion(), v.getStatus().name(), v.getPublishedAt(), v.getPublishedBy());
        }
    }
}
