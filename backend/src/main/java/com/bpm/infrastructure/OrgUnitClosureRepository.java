package com.bpm.infrastructure;

import com.bpm.domain.org.OrgUnitClosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgUnitClosureRepository
        extends JpaRepository<OrgUnitClosure, OrgUnitClosure.Key> {

    /** Tất cả hàng có hậu duệ = id → các tổ tiên (gồm self depth 0). */
    List<OrgUnitClosure> findByDescendantId(String descendantId);

    /** Tất cả hàng có tổ tiên = id → các hậu duệ (gồm self depth 0). */
    List<OrgUnitClosure> findByAncestorId(String ancestorId);
}
