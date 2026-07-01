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
 * Bản phiên bản ĐÃ BAN HÀNH của một quy trình (Story 2.4, AD-3). BẤT BIẾN — snapshot định nghĩa
 * BPMN + metadata tại thời điểm publish. Instance đang chạy giữ phiên bản đã snapshot; sửa bản nháp
 * sau đó không ảnh hưởng. Chỉ phiên bản PUBLISHED mới nhất khởi tạo nhiệm vụ mới (FR-A06/A07).
 */
@Entity
@Table(name = "process_version")
public class ProcessVersion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "process_id", length = 36, nullable = false, updatable = false)
    private String processId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Lob
    @Column(name = "bpmn_xml", columnDefinition = "TEXT", updatable = false)
    private String bpmnXml;

    @Lob
    @Column(name = "steps_meta_json", columnDefinition = "TEXT", updatable = false)
    private String stepsMetaJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ProcessStatus status; // PUBLISHED | RETIRED

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 100, updatable = false)
    private String publishedBy;

    protected ProcessVersion() {
    }

    public ProcessVersion(String processId, int version, String bpmnXml, String stepsMetaJson, String publishedBy) {
        this.id = UUID.randomUUID().toString();
        this.processId = processId;
        this.version = version;
        this.bpmnXml = bpmnXml;
        this.stepsMetaJson = stepsMetaJson;
        this.status = ProcessStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.publishedBy = publishedBy;
    }

    public String getId() { return id; }
    public String getProcessId() { return processId; }
    public int getVersion() { return version; }
    public String getBpmnXml() { return bpmnXml; }
    public String getStepsMetaJson() { return stepsMetaJson; }
    public ProcessStatus getStatus() { return status; }
    public void setStatus(ProcessStatus status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
}
