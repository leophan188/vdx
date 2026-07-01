package com.bpm.infrastructure;

import com.bpm.domain.AccountStatus;
import com.bpm.domain.UserAccount;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Tài khoản đang được gán một vai trò phân quyền (theo roleCode). */
    List<UserAccount> findByRoleCode(String roleCode);
    long countByRoleCode(String roleCode);

    /** Gợi ý @mention: account đang hoạt động có fullName/username chứa q (không phân biệt hoa thường). */
    @Query("select u from UserAccount u where u.status = :status "
            + "and (lower(u.fullName) like lower(concat('%', :q, '%')) "
            + "or lower(u.username) like lower(concat('%', :q, '%'))) "
            + "order by u.fullName asc")
    List<UserAccount> searchActive(@Param("q") String q, @Param("status") AccountStatus status, Limit limit);
}
