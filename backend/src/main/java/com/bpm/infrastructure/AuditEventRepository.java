package com.bpm.infrastructure;

import com.bpm.domain.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    /** Vết kiểm toán của một đối tượng theo thời gian (FR-I01, FR-I02). */
    List<AuditEvent> findByObjectTypeAndObjectIdOrderByCreatedAtAsc(String objectType, String objectId);

    /** 200 sự kiện kiểm toán GẦN NHẤT (toàn hệ thống) — cho màn duyệt nhanh. */
    List<AuditEvent> findTop200ByOrderByCreatedAtDesc();
}
