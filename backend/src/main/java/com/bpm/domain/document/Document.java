package com.bpm.domain.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Tài liệu dự thảo soạn bằng OnlyOffice (Story 3.10). Giữ nội dung hiện tại + phiên bản. */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** Liên kết tới phiên chạy quy trình (tùy chọn). */
    @Column(name = "instance_id", length = 36)
    private String instanceId;

    @Column(name = "version", nullable = false)
    private int version;

    @Lob
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "content_type", length = 100)
    private String contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Khóa tài liệu cho OnlyOffice — đổi mỗi lần lưu để buộc làm mới cache. */
    @Column(name = "doc_key", length = 64, nullable = false)
    private String docKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** DRAFT | SIGNED | ARCHIVED (Story 3.16/3.17). */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "DRAFT";

    // Ký ban hành (3.16)
    @Column(name = "signed_number", length = 100)
    private String signedNumber;
    @Column(name = "signed_at")
    private Instant signedAt;
    @Column(name = "signed_by", length = 100)
    private String signedBy;

    // Đóng hồ sơ / lưu trữ (3.17)
    @Column(name = "archive_no", length = 100)
    private String archiveNo;
    @Column(name = "classification", length = 100)
    private String classification;
    @Column(name = "retention_years")
    private Integer retentionYears;
    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Document() {
    }

    public Document(String name, String instanceId, byte[] content, String actor) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.instanceId = instanceId;
        this.version = 1;
        this.content = content;
        this.docKey = newKey();
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /** Lưu nội dung mới → tăng phiên bản + đổi docKey. */
    public void saveNewContent(byte[] newContent, String actor) {
        this.content = newContent;
        this.version += 1;
        this.docKey = newKey();
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Ghi nhận kết quả ký ban hành (Story 3.16). */
    public void sign(String number, String signer) {
        if ("ARCHIVED".equals(status)) {
            throw new IllegalStateException("Hồ sơ đã lưu trữ, không thể ký");
        }
        this.signedNumber = number;
        this.signedBy = signer;
        this.signedAt = Instant.now();
        this.status = "SIGNED";
    }

    /** Đóng hồ sơ + metadata lưu trữ chuẩn (Story 3.17). */
    public void archive(String archiveNo, String classification, Integer retentionYears) {
        if (!"SIGNED".equals(status)) {
            throw new IllegalStateException("Chỉ đóng hồ sơ sau khi đã ký ban hành");
        }
        this.archiveNo = archiveNo;
        this.classification = classification;
        this.retentionYears = retentionYears;
        this.archivedAt = Instant.now();
        this.status = "ARCHIVED";
    }

    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getSignedNumber() { return signedNumber; }
    public Instant getSignedAt() { return signedAt; }
    public String getSignedBy() { return signedBy; }
    public String getArchiveNo() { return archiveNo; }
    public String getClassification() { return classification; }
    public Integer getRetentionYears() { return retentionYears; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInstanceId() { return instanceId; }
    public int getVersion() { return version; }
    public byte[] getContent() { return content; }
    public String getContentType() { return contentType; }
    public String getDocKey() { return docKey; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
