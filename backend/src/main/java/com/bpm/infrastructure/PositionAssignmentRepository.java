package com.bpm.infrastructure;

import com.bpm.domain.position.PositionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionAssignmentRepository extends JpaRepository<PositionAssignment, String> {
    Optional<PositionAssignment> findByPositionIdAndEndedAtIsNull(String positionId);
}
