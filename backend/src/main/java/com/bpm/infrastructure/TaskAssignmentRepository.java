package com.bpm.infrastructure;

import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.assignment.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, String> {
    Optional<TaskAssignment> findByTaskId(String taskId);
    List<TaskAssignment> findByPositionId(String positionId);

    /** Hàng đợi "chưa có người nhận" của một đơn vị (FR-C08). */
    List<TaskAssignment> findByStatusAndCandidateGroupOrgUnitId(AssignmentStatus status, String orgUnitId);
}
