package com.bpm.infrastructure;

import com.bpm.domain.permission.PermissionRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRoleRepository extends JpaRepository<PermissionRole, String> {
}
