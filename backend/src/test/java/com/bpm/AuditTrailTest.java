package com.bpm;

import com.bpm.application.AssignmentService;
import com.bpm.application.AuditQueryService;
import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.assignment.DelegationKind;
import com.bpm.domain.audit.AuditEvent;
import com.bpm.domain.UserAccount;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.infrastructure.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ActiveProfiles("test")
class AuditTrailTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired AssignmentService assignmentService;
    @Autowired AuditQueryService auditQuery;
    @Autowired AuditEventRepository auditRepo;

    private String uid(String n) {
        UserAccount u = userService.createAccount(n, "Secret123", n, "USER", "test");
        return u.getId();
    }

    @Test
    void auditRecord_cannotBeDeleted_appendOnly() {
        AuditEvent saved = auditRepo.save(new AuditEvent("X_TEST", "Thing", "obj-1", "tester", "detail"));

        Throwable thrown = catchThrowable(() -> {
            auditRepo.delete(saved);
            auditRepo.flush();
        });

        // @PreRemove ném UnsupportedOperationException — đi qua chuỗi cause của lỗi flush
        boolean blocked = false;
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof UnsupportedOperationException) { blocked = true; break; }
        }
        assertThat(blocked).as("DELETE audit phải bị chặn (append-only AD-6)").isTrue();
    }

    @Test
    void trailForTask_returnsLifecycleEventsInOrder() {
        String a = uid("au_A");
        String b = uid("au_B");
        OrgUnit unit = orgService.create("Vụ Audit", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), a, "test");

        assignmentService.assignTaskToPosition("au-t1", pos.getId(), "test");
        assignmentService.reassignTask("au-t1", b, DelegationKind.DELEGATE, "đi họp", "A");

        // AC-4: vết theo thứ tự thời gian
        assertThat(auditQuery.trailForTask("au-t1"))
                .extracting(AuditEvent::getAction)
                .containsExactly("TASK_ASSIGNED", "TASK_DELEGATE");
    }

    @Test
    void trailByObject_returnsObjectHistoryInOrder() {
        String holder = uid("au_holder");
        OrgUnit unit = orgService.create("Vụ Obj", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), holder, "test");

        // AC-5: chuỗi audit của một đối tượng (Position): tạo → gán người giữ
        assertThat(auditQuery.trail("Position", pos.getId()))
                .extracting(AuditEvent::getAction)
                .containsExactly("POSITION_CREATED", "POSITION_ASSIGNED");
    }
}
