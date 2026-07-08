package com.bpm.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt chạy (instance) của quy trình (Story 3.1, AD-1/AD-3). App lưu liên kết tới Flowable instance
 * + SNAPSHOT phiên bản process (stepsMeta) lúc khởi tạo — node giữ định nghĩa đã snapshot dù publish bản mới.
 */
@Entity
@Table(name = "workflow_instance", indexes = {
        @Index(name = "ix_wfi_flowable", columnList = "flowable_instance_id"),
        @Index(name = "ix_wfi_started_by", columnList = "started_by")
})
public class WorkflowInstance {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "process_id", length = 36, nullable = false)
    private String processId;

    @Column(name = "process_version", nullable = false)
    private int processVersion;

    @Column(name = "flowable_instance_id", length = 64, nullable = false)
    private String flowableInstanceId;

    /** Snapshot metadata bước (JSON) của phiên bản lúc khởi tạo — listener đọc để resolve phân công. */
    @Lob
    @Column(name = "steps_meta_json", columnDefinition = "TEXT")
    private String stepsMetaJson;

    /** Snapshot schema biểu mẫu lúc khởi tạo: JSON {formId: schemaJson} — hiển thị dữ liệu cũ đúng phiên bản
     * dù form/quy trình bị đổi số bước hay sửa trường về sau. */
    @Lob
    @Column(name = "forms_json", columnDefinition = "TEXT")
    private String formsJson;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "RUNNING";

    @Column(name = "started_by", length = 100)
    private String startedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    protected WorkflowInstance() {
    }

    public WorkflowInstance(String processId, int processVersion, String flowableInstanceId,
                            String stepsMetaJson, String startedBy) {
        this(processId, processVersion, flowableInstanceId, stepsMetaJson, null, startedBy);
    }

    public WorkflowInstance(String processId, int processVersion, String flowableInstanceId,
                            String stepsMetaJson, String formsJson, String startedBy) {
        this.id = UUID.randomUUID().toString();
        this.processId = processId;
        this.processVersion = processVersion;
        this.flowableInstanceId = flowableInstanceId;
        this.stepsMetaJson = stepsMetaJson;
        this.formsJson = formsJson;
        this.status = "RUNNING";
        this.startedBy = startedBy;
        this.startedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProcessId() { return processId; }
    public int getProcessVersion() { return processVersion; }
    public String getFlowableInstanceId() { return flowableInstanceId; }
    public String getStepsMetaJson() { return stepsMetaJson; }
    public String getFormsJson() { return formsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
}
