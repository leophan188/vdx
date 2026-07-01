package com.bpm;

import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.RoleService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.UserAccount;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RoleServiceTest {

    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired RoleService roleService;
    @Autowired UserAccountService userService;

    private String uid(String n) {
        UserAccount u = userService.createAccount(n, "Secret123", n, "USER", "test");
        return u.getId();
    }

    @Test
    void holderInheritsPermissions_andChangeOfHolderTransfersThem() {
        // vai trò ORG_MANAGER với quyền ORG_ADMIN
        roleService.createRole("ORG_MANAGER_T", "Quản lý tổ chức", Set.of("ORG_ADMIN", "POSITION_ADMIN"), "test");
        OrgUnit unit = orgService.create("Vụ A", null, "test");
        Position pos = positionService.create("Vụ trưởng", unit.getId(), "test");
        roleService.assignRoleToPosition(pos.getId(), "ORG_MANAGER_T", "test");

        String u1 = uid("r1_user1");
        String u2 = uid("r1_user2");

        // u1 giữ vị trí → có quyền của vai trò
        positionService.assignHolder(pos.getId(), u1, "test");
        assertThat(roleService.permissionsForUser(u1)).contains("ORG_ADMIN", "POSITION_ADMIN");
        assertThat(roleService.roleCodesForUser(u1)).contains("ORG_MANAGER_T");

        // đổi người giữ sang u2 → u2 kế thừa, u1 không còn
        positionService.assignHolder(pos.getId(), u2, "test");
        assertThat(roleService.permissionsForUser(u2)).contains("ORG_ADMIN");
        assertThat(roleService.permissionsForUser(u1)).doesNotContain("ORG_ADMIN");
    }

    @Test
    void updateRole_changesNameAndPerms_deleteBlockedWhenAssigned() {
        roleService.createRole("EDITABLE_T", "Cũ", Set.of("A"), "test");
        roleService.updateRole("EDITABLE_T", "Mới", Set.of("B", "C"), "test");
        var r = roleService.listRoles().stream().filter(x -> x.getCode().equals("EDITABLE_T")).findFirst().orElseThrow();
        assertThat(r.getName()).isEqualTo("Mới");
        assertThat(r.getPermissions()).containsExactlyInAnyOrder("B", "C");

        // gán cho vị trí → không xóa được
        OrgUnit unit = orgService.create("Vụ R", null, "test");
        Position pos = positionService.create("Trưởng", unit.getId(), "test");
        roleService.assignRoleToPosition(pos.getId(), "EDITABLE_T", "test");
        assertThatThrownBy(() -> roleService.deleteRole("EDITABLE_T", "test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteRole_unassigned_ok() {
        roleService.createRole("THROWAWAY_T", "Tạm", Set.of("X"), "test");
        roleService.deleteRole("THROWAWAY_T", "test");
        assertThat(roleService.listRoles().stream().noneMatch(r -> r.getCode().equals("THROWAWAY_T"))).isTrue();
    }

    @Test
    void userWithoutPosition_hasNoResolvedPermissions() {
        String u = uid("r2_lonely");
        assertThat(roleService.permissionsForUser(u)).isEmpty();
        assertThat(roleService.roleCodesForUser(u)).isEmpty();
    }
}
