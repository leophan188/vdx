package com.bpm.domain.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Gán vai trò cho vị trí (AD-9): một vị trí có thể có nhiều vai trò. */
@Entity
@Table(name = "position_role")
@IdClass(PositionRole.Key.class)
public class PositionRole {

    @Id
    @Column(name = "position_id", length = 36, nullable = false)
    private String positionId;

    @Id
    @Column(name = "role_code", length = 50, nullable = false)
    private String roleCode;

    protected PositionRole() {
    }

    public PositionRole(String positionId, String roleCode) {
        this.positionId = positionId;
        this.roleCode = roleCode;
    }

    public String getPositionId() { return positionId; }
    public String getRoleCode() { return roleCode; }

    public static class Key implements Serializable {
        private String positionId;
        private String roleCode;

        public Key() {
        }

        public Key(String positionId, String roleCode) {
            this.positionId = positionId;
            this.roleCode = roleCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(positionId, key.positionId) && Objects.equals(roleCode, key.roleCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(positionId, roleCode);
        }
    }
}
