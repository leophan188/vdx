package com.bpm;

import com.bpm.application.AssignmentService;
import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.assignment.AssignmentPort;
import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.assignment.TaskAssignment;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditEvent;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.infrastructure.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class UnassignedQueueTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired AssignmentService assignmentService;
    @Autowired AuditEventRepository auditRepo;

    @MockBean AssignmentPort assignmentPort;

    private String uid(String n) {
        UserAccount u = userService.createAccount(n, "Secret123", n, "USER", "test");
        return u.getId();
    }

    @Test
    void emptyPosition_taskEntersQueue_andAlertsSuperior() {
        OrgUnit parent = orgService.create("Cục X", null, "test");
        OrgUnit child = orgService.create("Vụ con", parent.getId(), "test");
        Position pos = positionService.create("Chuyên viên", child.getId(), "test"); // trống

        assignmentService.assignTaskToPosition("q1", pos.getId(), "test");

        // AC-1, AC-5: việc nằm trong hàng đợi của đơn vị con
        assertThat(assignmentService.unassignedQueue(child.getId()))
                .extracting(TaskAssignment::getTaskId).contains("q1");

        // AC-2: cảnh báo cấp trên (đơn vị cha)
        boolean alerted = auditRepo.findAll().stream().anyMatch(e ->
                e.getAction().equals("TASK_UNASSIGNED_ALERT") && parent.getId().equals(e.getObjectId()));
        assertThat(alerted).isTrue();
    }

    @Test
    void claim_movesTaskOutOfQueue_toAssignee_viaPort() {
        OrgUnit unit = orgService.create("Vụ Q2", null, "test");
        Position pos = positionService.create("Chuyên viên", unit.getId(), "test"); // trống
        assignmentService.assignTaskToPosition("q2", pos.getId(), "test");
        String picked = uid("q2_user");

        // AC-3: gán tạm
        TaskAssignment a = assignmentService.claimUnassigned("q2", picked, "admin");
        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(a.getAssigneeUserId()).isEqualTo(picked);
        verify(assignmentPort).mirrorAssignee("q2", picked);
        // rời hàng đợi
        assertThat(assignmentService.unassignedQueue(unit.getId())).isEmpty();
    }

    @Test
    void escalate_movesCandidateToParent_staysUnassigned() {
        OrgUnit parent = orgService.create("Cục Y", null, "test");
        OrgUnit child = orgService.create("Vụ con Y", parent.getId(), "test");
        Position pos = positionService.create("Chuyên viên", child.getId(), "test"); // trống
        assignmentService.assignTaskToPosition("q3", pos.getId(), "test");

        // AC-4: leo thang lên đơn vị cha
        TaskAssignment a = assignmentService.escalateUnassigned("q3", "admin");
        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(a.getCandidateGroupOrgUnitId()).isEqualTo(parent.getId());
        verify(assignmentPort).mirrorCandidateGroup("q3", parent.getId());

        // việc giờ nằm ở hàng đợi cấp trên, rời hàng đợi con
        assertThat(assignmentService.unassignedQueue(parent.getId()))
                .extracting(TaskAssignment::getTaskId).contains("q3");
        assertThat(assignmentService.unassignedQueue(child.getId())).isEmpty();
    }

    @Test
    void escalateAtRoot_isRejected() {
        OrgUnit root = orgService.create("Cục gốc", null, "test");
        Position pos = positionService.create("Chuyên viên", root.getId(), "test"); // trống
        assignmentService.assignTaskToPosition("q4", pos.getId(), "test");

        assertThatThrownBy(() -> assignmentService.escalateUnassigned("q4", "admin"))
                .isInstanceOf(IllegalStateException.class);
    }
}
