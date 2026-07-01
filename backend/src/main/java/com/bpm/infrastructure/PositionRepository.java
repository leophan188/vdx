package com.bpm.infrastructure;

import com.bpm.domain.position.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, String> {
    List<Position> findByOrgUnitId(String orgUnitId);
    List<Position> findByCurrentHolderUserId(String userId);
    boolean existsByOrgUnitId(String orgUnitId);
}
