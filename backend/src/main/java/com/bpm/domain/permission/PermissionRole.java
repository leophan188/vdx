package com.bpm.domain.permission;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * VAI TRÒ PHÂN QUYỀN — tách riêng khỏi vai trò chức danh ({@link com.bpm.domain.role.Role}).
 * Mỗi vai trò gồm một tập KEY chức năng ({@link Feature}); tài khoản được gán 1 vai trò
 * (UserAccount.roleCode) → mọi người cùng vai trò có quyền NHƯ NHAU.
 */
@Entity
@Table(name = "permission_role")
public class PermissionRole {

    @Id
    @Column(name = "code", length = 40, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    /** KEY chức năng đang bật (vd "PROJECT", "HR") — quy ra authority FEAT_{key} khi đăng nhập. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "permission_role_feature", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "feature", length = 40)
    private Set<String> features = new LinkedHashSet<>();

    protected PermissionRole() {
    }

    public PermissionRole(String code, String name, String description, Set<String> features) {
        this.code = code;
        this.name = name;
        this.description = description;
        setFeatures(features);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<String> getFeatures() { return features; }
    public void setFeatures(Set<String> features) {
        this.features = features != null ? new LinkedHashSet<>(features) : new LinkedHashSet<>();
    }
}
