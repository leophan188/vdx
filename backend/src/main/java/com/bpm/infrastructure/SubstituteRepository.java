package com.bpm.infrastructure;

import com.bpm.domain.position.Substitute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubstituteRepository extends JpaRepository<Substitute, String> {
    Optional<Substitute> findByPositionIdAndActiveTrue(String positionId);
}
