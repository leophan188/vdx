package com.bpm.infrastructure;

import com.bpm.domain.workflow.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    Optional<WorkflowInstance> findByFlowableInstanceId(String flowableInstanceId);
    List<WorkflowInstance> findByProcessId(String processId);
}
