package com.bpm.domain.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata tệp đính kèm của biểu mẫu (trường kiểu "Tải file"). File nhị phân lưu trên ĐĨA server
 * (bpm.attachment.dir), DB chỉ giữ metadata + đường dẫn tương đối. Tham chiếu (id) được nhúng vào
 * formData của trường dạng JSON. Phục vụ tải qua /api/v1/attachments/{id} (chỉ người có phiên).
 */
@Entity
@Table(name = "form_attachment")
public class Attachment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 150, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Đường dẫn tương đối trong bpm.attachment.dir (vd "ab/abcd-....pdf"). */
    @Column(name = "rel_path", length = 300, nullable = false)
    private String relPath;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(String id, String originalName, String contentType,
                      long sizeBytes, String relPath, String uploadedBy) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.relPath = relPath;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getRelPath() { return relPath; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
