package com.bpm.infrastructure;

import com.bpm.domain.hr.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    Optional<Employee> findByEmpCode(String empCode);
    List<Employee> findAllByOrderByEmpCodeAsc();

    /** Tìm nhân sự theo tài khoản đăng nhập liên kết (1 user ↔ tối đa 1 Employee). */
    Optional<Employee> findByUserAccountId(String userAccountId);

    /** Map hàng loạt user → Employee (tránh N+1 khi dựng list member/task). */
    List<Employee> findByUserAccountIdIn(Collection<String> userAccountIds);
}
