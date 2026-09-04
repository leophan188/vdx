package com.bpm.infrastructure;

import com.bpm.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {
    Optional<Project> findByCode(String code);
    boolean existsByCode(String code);
    List<Project> findAllByOrderByCreatedAtDesc();

    /** Dự án đã đồng bộ từ ERP — khớp theo ID bên ERP vì mã và tên đều có thể bị sửa. */
    Optional<Project> findByErpProjectId(Long erpProjectId);

    List<Project> findByErpProjectIdIsNotNull();
}
