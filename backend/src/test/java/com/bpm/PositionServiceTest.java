package com.bpm;

import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.UserAccount;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.infrastructure.PositionAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PositionServiceTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired PositionAssignmentRepository assignmentRepo;

    private String uid(String name) {
        UserAccount u = userService.createAccount(name, "Secret123", name, "USER", "test");
        return u.getId();
    }

    @Test
    void assignNewHolder_endsPreviousTenure_andKeepsOneCurrent() {
        OrgUnit unit = orgService.create("Vụ A", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        String u1 = uid("p1_user1");
        String u2 = uid("p1_user2");

        positionService.assignHolder(pos.getId(), u1, "test");
        assertThat(positionService.currentHolder(pos.getId())).isEqualTo(u1);

        positionService.assignHolder(pos.getId(), u2, "test");
        assertThat(positionService.currentHolder(pos.getId())).isEqualTo(u2);

        // đúng một nhiệm kỳ hiện hành (endedAt null)
        long current = assignmentRepo.findAll().stream()
                .filter(a -> a.getPositionId().equals(pos.getId()) && a.isCurrent()).count();
        assertThat(current).isEqualTo(1);
        // người cũ có nhiệm kỳ đã kết thúc
        assertThat(assignmentRepo.findAll().stream()
                .anyMatch(a -> a.getUserId().equals(u1) && a.getEndedAt() != null)).isTrue();
    }

    @Test
    void updateTitle_changesName() {
        OrgUnit unit = orgService.create("Vụ U", null, "test");
        Position pos = positionService.create("Vụ phó", unit.getId(), "test");
        positionService.updateTitle(pos.getId(), "Vụ trưởng", "test");
        assertThat(positionService.all().stream().filter(p -> p.getId().equals(pos.getId())).findFirst().orElseThrow().getTitle())
                .isEqualTo("Vụ trưởng");
    }

    @Test
    void delete_emptyPosition_ok_butBlockedWhenHeld() {
        OrgUnit unit = orgService.create("Vụ D", null, "test");
        Position empty = positionService.create("Trống", unit.getId(), "test");
        positionService.delete(empty.getId(), "test"); // không người giữ → xóa được
        assertThat(positionService.all().stream().noneMatch(p -> p.getId().equals(empty.getId()))).isTrue();

        Position held = positionService.create("Có người", unit.getId(), "test");
        positionService.assignHolder(held.getId(), uid("pd_user"), "test");
        assertThatThrownBy(() -> positionService.delete(held.getId(), "test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteOrgUnit_withPosition_isBlocked() {
        OrgUnit unit = orgService.create("Vụ B", null, "test");
        positionService.create("Chuyên viên", unit.getId(), "test");
        assertThatThrownBy(() -> orgService.delete(unit.getId(), "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vị trí");
    }
}
