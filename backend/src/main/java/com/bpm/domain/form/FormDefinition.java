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
 * Định nghĩa biểu mẫu động (Story 2.6, AD-4). Schema các trường lưu JSON; form runtime sinh tự động
 * từ metadata, không cần code (FR-B01, FR-B02). Versioning + snapshot mọi node ở Story 2.10.
 */
@Entity
@Table(name = "form_definition")
public class FormDefinition {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "form_key", length = 100, nullable = false)
    private String formKey;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ProcessStatus status = ProcessStatus.DRAFT;

    /** Schema biểu mẫu: { "fields": [ {key,label,type,required,options,...} ] } (AD-4). */
    @Lob
    @Column(name = "schema_json", columnDefinition = "TEXT")
    private String schemaJson;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "published_version", nullable = false)
    private int publishedVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FormDefinition() {
    }

    public FormDefinition(String formKey, String name) {
        this.id = UUID.randomUUID().toString();
        this.formKey = formKey;
        this.name = name;
        this.status = ProcessStatus.DRAFT;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getFormKey() { return formKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProcessStatus getStatus() { return status; }
    public void setStatus(ProcessStatus status) { this.status = status; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public int getVersion() { return version; }
    public int getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(int publishedVersion) { this.publishedVersion = publishedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
