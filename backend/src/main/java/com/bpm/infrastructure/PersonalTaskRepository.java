package com.bpm.infrastructure;

import com.bpm.domain.personal.PersonalTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalTaskRepository extends JpaRepository<PersonalTask, String> {
    List<PersonalTask> findByUserIdOrderByCreatedAtDesc(String userId);
}
