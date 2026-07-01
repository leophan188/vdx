package com.bpm.infrastructure;

import com.bpm.domain.audit.AuditEvent;
import com.bpm.domain.audit.AuditPort;
import org.springframework.stereotype.Component;

/** Adapter ghi audit append-only vào bảng audit_event (AD-6). */
@Component
public class JpaAuditAdapter implements AuditPort {

    private final AuditEventRepository repository;

    public JpaAuditAdapter(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(String action, String objectType, String objectId, String actor, String detail) {
        repository.save(new AuditEvent(action, objectType, objectId, actor, detail));
    }
}
