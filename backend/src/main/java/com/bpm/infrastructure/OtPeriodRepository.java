package com.bpm.infrastructure;

import com.bpm.domain.ot.OtPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtPeriodRepository extends JpaRepository<OtPeriod, String> {
    // PK = periodKey; existsById(periodKey) ⇒ kỳ đã chốt.
}
