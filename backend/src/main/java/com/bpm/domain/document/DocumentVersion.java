package com.bpm.domain.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Phiên bản dự thảo bất biến (Story 3.10) — mỗi lần OnlyOffice lưu sinh 1 bản. */
@Entity
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "version", nullable = false)
    private int version;

    /**
     * KHÔNG dùng @Lob — Hibernate 6 map @Lob không kèm length thành TINYBLOB (255 byte) trên MariaDB,
     * file .docx vài trăm KB là lỗi ngay khi lưu. Đặt length lớn để MariaDB tạo LONGBLOB, H2 tạo cột lớn
     * (cùng cách đã xử lý ở ReportRun#outputBytes, ProjectDiary#content, Post#body).
     */
    @Column(name = "content", nullable = false, length = 100_000_000, updatable = false)
    private byte[] content;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    @Column(name = "saved_by", length = 100)
    private String savedBy;

    protected DocumentVersion() {
    }

    public DocumentVersion(String documentId, int version, byte[] content, String savedBy) {
        this.id = UUID.randomUUID().toString();
        this.documentId = documentId;
        this.version = version;
        this.content = content;
        this.savedBy = savedBy;
        this.savedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getDocumentId() { return documentId; }
    public int getVersion() { return version; }
    public byte[] getContent() { return content; }
    public Instant getSavedAt() { return savedAt; }
    public String getSavedBy() { return savedBy; }
}
