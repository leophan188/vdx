package com.bpm.infrastructure;

import com.bpm.domain.assignment.Delegation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DelegationRepository extends JpaRepository<Delegation, String> {
    List<Delegation> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
