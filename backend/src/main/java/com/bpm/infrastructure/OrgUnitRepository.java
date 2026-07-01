package com.bpm.infrastructure;

import com.bpm.domain.org.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, String> {
    List<OrgUnit> findByParentId(String parentId);
    List<OrgUnit> findByParentIdIsNull();
    boolean existsByParentId(String parentId);

    /** Tra đơn vị theo mã nghiệp vụ (liên thông import nhân sự — Epic 1 GĐ2). */
    Optional<OrgUnit> findByCode(String code);
}
