package com.bpm.infrastructure;

import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, String> {
    List<ProjectTask> findByProjectIdOrderByOrderIndexAscSeqAsc(String projectId);
    List<ProjectTask> findByProjectIdAndParentId(String projectId, String parentId);
    List<ProjectTask> findByAssigneeUserId(String assigneeUserId);
    long countByProjectId(String projectId);
    long countByParentId(String parentId);
    void deleteByProjectId(String projectId);

    /** BUG/ISSUE LIÊN QUAN 1 người: được GIAO (assignee) HOẶC do người đó LOG (reporter). */
    @Query("select t from ProjectTask t where t.type in :types "
            + "and (t.assigneeUserId = :userId or t.reporterUserId = :userId)")
    List<ProjectTask> findRelatedByTypes(@Param("userId") String userId,
                                         @Param("types") Collection<TaskType> types);
}
