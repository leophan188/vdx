package com.bpm.domain.role;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/** Vai trò + tập quyền (AD-9). Code là khóa tự nhiên (vd ADMIN, ORG_MANAGER). */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "code", length = 50, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "permission", length = 100)
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String code, String name, Set<String> permissions) {
        this.code = code;
        this.name = name;
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }
}
