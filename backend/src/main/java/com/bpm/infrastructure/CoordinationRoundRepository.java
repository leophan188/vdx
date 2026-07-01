package com.bpm.infrastructure;

import com.bpm.domain.collab.CoordinationRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoordinationRoundRepository extends JpaRepository<CoordinationRound, String> {
    List<CoordinationRound> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);
}
