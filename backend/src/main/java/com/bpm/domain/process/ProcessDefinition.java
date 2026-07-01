package com.bpm.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Định nghĩa quy trình (Story 2.1, AD-1). Lưu định nghĩa BPMN (XML do bpmn-js sinh) + metadata bước
 * (JSON theo elementId). Cấu hình động — không build/deploy lại (NFR-09). Versioning/publish ở Story 2.4.
 */
@Entity
@Table(name = "process_definition")
public class ProcessDefinition {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "process_key", length = 100, nullable = false)
    private String processKey;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ProcessStatus status = ProcessStatus.DRAFT;

    /** XML BPMN của diagram (bpmn-js). */
    @Lob
    @Column(name = "bpmn_xml", columnDefinition = "TEXT")
    private String bpmnXml;

    /** Metadata bước: { elementId: { position, slaHours, actions[] } } dạng JSON (AD-4). */
    @Lob
    @Column(name = "steps_meta_json", columnDefinition = "TEXT")
    private String stepsMetaJson;

    @Column(name = "version", nullable = false)
    private int version = 1;

    /** Phiên bản đã ban hành mới nhất (0 = chưa ban hành). UI hiển thị "Phiên bản vX" (UX-DR10). */
    @Column(name = "published_version", nullable = false)
    private int publishedVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessDefinition() {
    }

    public ProcessDefinition(String processKey, String name) {
        this.id = UUID.randomUUID().toString();
        this.processKey = processKey;
        this.name = name;
        this.status = ProcessStatus.DRAFT;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getProcessKey() { return processKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProcessStatus getStatus() { return status; }
    public void setStatus(ProcessStatus status) { this.status = status; }
    public String getBpmnXml() { return bpmnXml; }
    public void setBpmnXml(String bpmnXml) { this.bpmnXml = bpmnXml; }
    public String getStepsMetaJson() { return stepsMetaJson; }
    public void setStepsMetaJson(String stepsMetaJson) { this.stepsMetaJson = stepsMetaJson; }
    public int getVersion() { return version; }
    public int getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(int publishedVersion) { this.publishedVersion = publishedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
