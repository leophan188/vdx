package com.bpm;

import com.bpm.application.CollaborationService;
import com.bpm.application.UserAccountService;
import com.bpm.domain.collab.CoordinationRound;
import com.bpm.domain.collab.Opinion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CollaborationServiceTest {

    @Autowired CollaborationService svc;
    @Autowired UserAccountService userService;

    @Test
    void comment_addEdit_onlyAuthorWithinWindow() {
        var c = svc.addComment("DOCUMENT", "doc-cmt", "Nội dung cần rà soát", "tester");
        assertThat(c.isEditable()).isTrue();
        var e = svc.editComment(c.getId(), "Đã sửa nội dung", "tester");
        assertThat(e.isEdited()).isTrue();
        assertThatThrownBy(() -> svc.editComment(c.getId(), "x", "nguoi_khac"))
                .isInstanceOf(IllegalStateException.class); // không phải tác giả
    }

    @Test
    void opinion_give_consolidate_resolve_lockEdit() {
        var o = svc.giveOpinion("DOCUMENT", "doc-op", null, "KHONG_DONG_Y", "Cần bổ sung căn cứ", "u_op");
        assertThat(svc.opinions("DOCUMENT", "doc-op")).hasSize(1);

        var r = svc.resolveOpinion(o.getId(), "TIEP_THU", "Đã bổ sung", "chu_tri");
        assertThat(r.getResolution()).isEqualTo("TIEP_THU");
        // đã xử lý → tác giả không sửa được nữa (3.14 guard)
        assertThatThrownBy(() -> svc.editOpinion(o.getId(), "DONG_Y", "x", "u_op"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void coordination_parallel_join_whenAllResponded() {
        userService.createAccount("collab_p1", "Secret123", "Phối hợp 1", "USER", "test");
        userService.createAccount("collab_p2", "Secret123", "Phối hợp 2", "USER", "test");
        CoordinationRound round = svc.requestCoordination("DOCUMENT", "doc-coord",
                List.of("collab_p1", "collab_p2"), 24, "chu_tri");

        Map<String, Object> st0 = svc.roundStatus(round.getId());
        assertThat(st0.get("complete")).isEqualTo(false);     // chưa ai phản hồi

        svc.giveOpinion("DOCUMENT", "doc-coord", round.getId(), "DONG_Y", "Nhất trí", "collab_p1");
        Map<String, Object> st1 = svc.roundStatus(round.getId());
        @SuppressWarnings("unchecked")
        List<String> pending = (List<String>) (Object) st1.get("pending");
        assertThat(pending).containsExactly("collab_p2");
        assertThat(st1.get("complete")).isEqualTo(false);

        svc.giveOpinion("DOCUMENT", "doc-coord", round.getId(), "DONG_Y", "OK", "collab_p2");
        Map<String, Object> st2 = svc.roundStatus(round.getId());
        assertThat(st2.get("complete")).isEqualTo(true);      // đủ người → join (không treo)
        assertThat(st2.get("respondedCount")).isEqualTo(2);
    }
}
