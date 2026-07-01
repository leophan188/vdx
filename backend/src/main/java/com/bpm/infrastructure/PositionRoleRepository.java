package com.bpm.infrastructure;

import com.bpm.domain.role.PositionRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRoleRepository extends JpaRepository<PositionRole, PositionRole.Key> {
    List<PositionRole> findByPositionId(String positionId);
    boolean existsByRoleCode(String roleCode);
}
