package com.bpm.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Đơn vị trong cây cơ cấu tổ chức (AD-7). Cây độ sâu tùy ý qua closure-table. */
@Entity
@Table(name = "org_unit")
public class OrgUnit {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * Mã đơn vị (định danh nghiệp vụ, vd "KKD", "PDX.1") — dùng để liên thông với import nhân sự (Epic 1 GĐ2).
     * Nullable cho dữ liệu cũ tạo tay chỉ có tên. Không unique ở cấp DB để không phá seed demo cũ.
     */
    @Column(name = "code", length = 100)
    private String code;

    /** Cha trực tiếp (null = nút gốc). Quan hệ tổ tiên/hậu duệ đầy đủ ở org_unit_closure. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrgUnit() {
    }

    public OrgUnit(String name, String parentId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.parentId = parentId;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public Instant getCreatedAt() { return createdAt; }
}
