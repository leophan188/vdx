package com.bpm;

import com.bpm.application.OrgUnitService;
import com.bpm.domain.org.OrgUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrgUnitServiceTest {

    @Autowired
    OrgUnitService service;

    @Test
    void create_multiLevel_buildsClosure() {
        OrgUnit tapDoan = service.create("Tập đoàn", null, "test");
        OrgUnit ban = service.create("Ban A", tapDoan.getId(), "test");
        OrgUnit vu = service.create("Vụ A1", ban.getId(), "test");

        // tổ tiên của Vụ A1 = Ban A (depth1), Tập đoàn (depth2)
        List<String> ancestors = service.ancestors(vu.getId()).stream().map(OrgUnit::getName).toList();
        assertThat(ancestors).containsExactly("Ban A", "Tập đoàn");

        // hậu duệ của Tập đoàn gồm Ban A, Vụ A1
        List<String> desc = service.descendants(tapDoan.getId()).stream().map(OrgUnit::getName).toList();
        assertThat(desc).containsExactlyInAnyOrder("Ban A", "Vụ A1");
    }

    @Test
    void move_reparents_andUpdatesClosure() {
        OrgUnit root = service.create("Root", null, "test");
        OrgUnit banA = service.create("Ban A", root.getId(), "test");
        OrgUnit banB = service.create("Ban B", root.getId(), "test");
        OrgUnit vu = service.create("Vụ X", banA.getId(), "test");

        // di chuyển Vụ X từ Ban A sang Ban B
        service.move(vu.getId(), banB.getId(), "test");

        List<String> ancestors = service.ancestors(vu.getId()).stream().map(OrgUnit::getName).toList();
        assertThat(ancestors).containsExactly("Ban B", "Root");
        assertThat(service.descendants(banA.getId())).isEmpty();
        assertThat(service.descendants(banB.getId()).stream().map(OrgUnit::getName).toList())
                .containsExactly("Vụ X");
    }

    @Test
    void move_intoOwnSubtree_isRejected() {
        OrgUnit a = service.create("A", null, "test");
        OrgUnit b = service.create("B", a.getId(), "test");
        assertThatThrownBy(() -> service.move(a.getId(), b.getId(), "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_withChildren_isBlocked_butLeafDeletes() {
        OrgUnit a = service.create("A", null, "test");
        OrgUnit b = service.create("B", a.getId(), "test");

        assertThatThrownBy(() -> service.delete(a.getId(), "test"))
                .isInstanceOf(IllegalArgumentException.class);

        service.delete(b.getId(), "test"); // lá → xóa được
        assertThat(service.descendants(a.getId())).isEmpty();
    }
}
