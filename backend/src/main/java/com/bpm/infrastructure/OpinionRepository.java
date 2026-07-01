package com.bpm.infrastructure;

import com.bpm.domain.collab.Opinion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpinionRepository extends JpaRepository<Opinion, String> {
    List<Opinion> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, String targetId);
    List<Opinion> findByRoundId(String roundId);
}
