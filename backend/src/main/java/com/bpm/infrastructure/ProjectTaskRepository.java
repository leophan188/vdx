package com.bpm.infrastructure;

import com.bpm.domain.project.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, String> {
    List<ProjectTask> findByProjectIdOrderByOrderIndexAscSeqAsc(String projectId);
    List<ProjectTask> findByProjectIdAndParentId(String projectId, String parentId);
    List<ProjectTask> findByAssigneeUserId(String assigneeUserId);
    long countByProjectId(String projectId);
    long countByParentId(String parentId);
    void deleteByProjectId(String projectId);
}
