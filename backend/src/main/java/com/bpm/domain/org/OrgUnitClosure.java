package com.bpm.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Bảng closure cho cây org (AD-7): một hàng cho mỗi cặp (tổ tiên, hậu duệ) kể cả self (depth 0).
 * Cho phép truy vấn tổ tiên/hậu duệ O(1) join, không đệ quy.
 */
@Entity
@Table(name = "org_unit_closure")
@IdClass(OrgUnitClosure.Key.class)
public class OrgUnitClosure {

    @Id
    @Column(name = "ancestor_id", length = 36, nullable = false)
    private String ancestorId;

    @Id
    @Column(name = "descendant_id", length = 36, nullable = false)
    private String descendantId;

    @Column(name = "depth", nullable = false)
    private int depth;

    protected OrgUnitClosure() {
    }

    public OrgUnitClosure(String ancestorId, String descendantId, int depth) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
        this.depth = depth;
    }

    public String getAncestorId() { return ancestorId; }
    public String getDescendantId() { return descendantId; }
    public int getDepth() { return depth; }

    /** Khóa phức hợp (ancestor, descendant). */
    public static class Key implements Serializable {
        private String ancestorId;
        private String descendantId;

        public Key() {
        }

        public Key(String ancestorId, String descendantId) {
            this.ancestorId = ancestorId;
            this.descendantId = descendantId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(ancestorId, key.ancestorId)
                    && Objects.equals(descendantId, key.descendantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ancestorId, descendantId);
        }
    }
}
