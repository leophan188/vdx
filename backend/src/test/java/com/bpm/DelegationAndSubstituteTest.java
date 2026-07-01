package com.bpm;

import com.bpm.application.AssignmentService;
import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.SubstituteService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.assignment.AssignmentPort;
import com.bpm.domain.assignment.DelegationKind;
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
class DelegationAndSubstituteTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired AssignmentService assignmentService;
    @Autowired SubstituteService substituteService;

    @MockBean AssignmentPort assignmentPort;

    private String uid(String n) {
        UserAccount u = userService.createAccount(n, "Secret123", n, "USER", "test");
        return u.getId();
    }

    private Position posWithHolder(String unitName, String holderId) {
        OrgUnit unit = orgService.create(unitName, null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), holderId, "test");
        return pos;
    }

    @Test
    void delegate_movesAssigneeToTarget_viaPort() {
        String a = uid("d1_A");
        String b = uid("d1_B");
        Position pos = posWithHolder("Vụ 1", a);
        assignmentService.assignTaskToPosition("t1", pos.getId(), "test");

        // AC-1: A uỷ quyền cho B
        TaskAssignment after = assignmentService.reassignTask("t1", b, DelegationKind.DELEGATE, "đi công tác", "A");
        assertThat(after.getAssigneeUserId()).isEqualTo(b);
        assertThat(after.getDelegatedFromUserId()).isEqualTo(a);
        verify(assignmentPort).mirrorAssignee("t1", b);
    }

    @Test
    void forward_chain_thenCycleBackIsRejected() {
        String a = uid("d2_A");
        String b = uid("d2_B");
        Position pos = posWithHolder("Vụ 2", a);
        assignmentService.assignTaskToPosition("t2", pos.getId(), "test");

        assignmentService.reassignTask("t2", b, DelegationKind.FORWARD, null, "A"); // A->B ok

        // AC-2: B chuyển ngược về A (đã từng giữ) → bị chặn (chống lặp)
        assertThatThrownBy(() -> assignmentService.reassignTask("t2", a, DelegationKind.FORWARD, null, "B"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reassignToSelf_isRejected() {
        String a = uid("d3_A");
        Position pos = posWithHolder("Vụ 3", a);
        assignmentService.assignTaskToPosition("t3", pos.getId(), "test");

        // AC-3: uỷ quyền cho chính người đang giữ → bị chặn
        assertThatThrownBy(() -> assignmentService.reassignTask("t3", a, DelegationKind.DELEGATE, null, "A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeSubstitute_routesNewTaskToSubstitute_notHolder() {
        String holder = uid("d4_holder");
        String sub = uid("d4_sub");
        Position pos = posWithHolder("Vụ 4", holder);

        substituteService.setSubstitute(pos.getId(), sub, "admin");

        // AC-4: việc mới resolve về người thay thế
        TaskAssignment snap = assignmentService.assignTaskToPosition("t4", pos.getId(), "test");
        assertThat(snap.getAssigneeUserId()).isEqualTo(sub);
        verify(assignmentPort).mirrorAssignee("t4", sub);

        // gỡ người thay thế → việc mới quay lại người giữ
        substituteService.clearSubstitute(pos.getId(), "admin");
        TaskAssignment snap2 = assignmentService.assignTaskToPosition("t4b", pos.getId(), "test");
        assertThat(snap2.getAssigneeUserId()).isEqualTo(holder);
    }
}
