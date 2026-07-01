package com.bpm;

import com.bpm.application.AssignmentService;
import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.assignment.AssignmentPort;
import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.assignment.TaskAssignment;
import com.bpm.domain.UserAccount;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
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
class AssignmentServiceTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired AssignmentService assignmentService;

    // Verify app mirror sang Flowable qua DUY NHẤT cổng này (AD-14). Engine thật ở Epic 2/3.
    @MockBean AssignmentPort assignmentPort;

    private String uid(String n) {
        UserAccount u = userService.createAccount(n, "Secret123", n, "USER", "test");
        return u.getId();
    }

    @Test
    void assign_snapshotsHolder_andMirrorsAssignee_inOneTx() {
        OrgUnit unit = orgService.create("Vụ A", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        String a = uid("a1_userA");
        positionService.assignHolder(pos.getId(), a, "test");

        TaskAssignment snap = assignmentService.assignTaskToPosition("task-1", pos.getId(), "test");

        // AC-1: snapshot người + vị trí lúc giao
        assertThat(snap.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(snap.getAssigneeUserId()).isEqualTo(a);
        assertThat(snap.getPositionId()).isEqualTo(pos.getId());
        // AC-2: mirror assignee qua port
        verify(assignmentPort).mirrorAssignee("task-1", a);
    }

    @Test
    void changingHolder_doesNotStealRunningTask_butNewTaskResolvesNewHolder() {
        OrgUnit unit = orgService.create("Vụ B", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        String a = uid("a2_userA");
        String b = uid("a2_userB");

        positionService.assignHolder(pos.getId(), a, "test");
        assignmentService.assignTaskToPosition("task-run", pos.getId(), "test");

        // đổi người giữ sang B
        positionService.assignHolder(pos.getId(), b, "test");

        // AC-3: việc đang chạy vẫn ở A (snapshot bất biến)
        assertThat(assignmentService.byTask("task-run")).get()
                .extracting(TaskAssignment::getAssigneeUserId).isEqualTo(a);

        // AC-4: việc mới resolve về B
        TaskAssignment fresh = assignmentService.assignTaskToPosition("task-new", pos.getId(), "test");
        assertThat(fresh.getAssigneeUserId()).isEqualTo(b);
    }

    @Test
    void emptyPosition_yieldsUnassignedWithCandidateGroup() {
        OrgUnit unit = orgService.create("Vụ C", null, "test");
        Position pos = positionService.create("Chuyên viên", unit.getId(), "test"); // chưa gán người

        TaskAssignment snap = assignmentService.assignTaskToPosition("task-empty", pos.getId(), "test");

        // AC-5: không để task mất — UNASSIGNED + candidate-group = đơn vị
        assertThat(snap.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(snap.getAssigneeUserId()).isNull();
        assertThat(snap.getCandidateGroupOrgUnitId()).isEqualTo(unit.getId());
        verify(assignmentPort).mirrorCandidateGroup("task-empty", unit.getId());
    }

    @Test
    void doubleAssign_sameTask_rejected() {
        OrgUnit unit = orgService.create("Vụ D", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), uid("a4_user"), "test");
        assignmentService.assignTaskToPosition("task-dup", pos.getId(), "test");

        assertThatThrownBy(() -> assignmentService.assignTaskToPosition("task-dup", pos.getId(), "test"))
                .isInstanceOf(IllegalStateException.class);
    }
}
