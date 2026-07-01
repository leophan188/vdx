package com.bpm.domain.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata media của bài (Story 2.1, NFR-03). File NHỊ PHÂN lưu trên ĐĨA server (bpm.media.dir),
 * DB chỉ giữ metadata + đường dẫn tương đối. Phục vụ qua /api/v1/media/{id} (có phiên).
 * kind = IMAGE | VIDEO.
 */
@Entity
@Table(name = "social_media")
public class PostMedia {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** null khi vừa upload (chưa gắn bài); set khi bài được tạo. */
    @Column(name = "post_id", length = 36)
    private String postId;

    @Column(name = "kind", length = 16, nullable = false)
    private String kind;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Đường dẫn tương đối trong bpm.media.dir (vd "ab/abcd-....mp4"). */
    @Column(name = "rel_path", length = 300, nullable = false)
    private String relPath;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostMedia() {
    }

    public PostMedia(String id, String kind, String originalName, String contentType,
                     long sizeBytes, String relPath, String uploadedBy) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.kind = kind;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.relPath = relPath;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public void setPostId(String postId) { this.postId = postId; }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getKind() { return kind; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getRelPath() { return relPath; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
