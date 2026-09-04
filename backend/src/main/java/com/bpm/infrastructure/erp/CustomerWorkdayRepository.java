package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.CustomerWorkdayEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerWorkdayRepository extends JpaRepository<CustomerWorkdayEntry, String> {

    List<CustomerWorkdayEntry> findByPeriodKey(String periodKey);

    void deleteByPeriodKey(String periodKey);

    @org.springframework.data.jpa.repository.Query(
            "select distinct c.periodKey from CustomerWorkdayEntry c order by c.periodKey desc")
    List<String> findPeriods();
}
