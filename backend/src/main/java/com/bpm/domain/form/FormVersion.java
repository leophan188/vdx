package com.bpm.domain.form;

import com.bpm.domain.process.ProcessStatus;
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
 * Phiên bản BẤT BIẾN của một biểu mẫu (Story 2.10, AD-3). Snapshot schema lúc ban hành; node đã
 * instantiate giữ phiên bản đã snapshot, dữ liệu đã nhập vẫn hợp lệ; node mới dùng bản mới nhất.
 */
@Entity
@Table(name = "form_version")
public class FormVersion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "form_id", length = 36, nullable = false, updatable = false)
    private String formId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Lob
    @Column(name = "schema_json", columnDefinition = "TEXT", updatable = false)
    private String schemaJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ProcessStatus status; // PUBLISHED | RETIRED

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 100, updatable = false)
    private String publishedBy;

    protected FormVersion() {
    }

    public FormVersion(String formId, int version, String schemaJson, String publishedBy) {
        this.id = UUID.randomUUID().toString();
        this.formId = formId;
        this.version = version;
        this.schemaJson = schemaJson;
        this.status = ProcessStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.publishedBy = publishedBy;
    }

    public String getId() { return id; }
    public String getFormId() { return formId; }
    public int getVersion() { return version; }
    public String getSchemaJson() { return schemaJson; }
    public ProcessStatus getStatus() { return status; }
    public void setStatus(ProcessStatus status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
}
