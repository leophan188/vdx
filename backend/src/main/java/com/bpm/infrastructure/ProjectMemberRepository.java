package com.bpm.infrastructure;

import com.bpm.domain.project.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {
    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(String projectId);
    List<ProjectMember> findByUserId(String userId);
    Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);
    boolean existsByProjectIdAndUserId(String projectId, String userId);
    long countByProjectId(String projectId);
    void deleteByProjectId(String projectId);
}
