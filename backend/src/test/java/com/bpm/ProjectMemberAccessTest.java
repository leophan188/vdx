package com.bpm;

import com.bpm.api.dto.ProjectDto;
import com.bpm.application.ProjectService;
import com.bpm.application.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tạm ngưng thành viên = chặn hẳn quyền vào dự án, không chỉ ẩn nút trên giao diện. */
@SpringBootTest
@ActiveProfiles("test")
class ProjectMemberAccessTest {

    @Autowired ProjectService projectService;
    @Autowired UserAccountService userService;

    @Test
    void deactivatedMemberLosesAccessAndCanBeRestored() {
        var owner = userService.createAccount("prj_owner", "Secret123", "Chủ dự án", "USER", "test");
        var member = userService.createAccount("prj_member", "Secret123", "Thành viên", "USER", "test");

        String pid = projectService.create(new ProjectDto.ProjectRequest(
                "ACC1", "Dự án kiểm quyền", null, null, null, null, owner.getId(), null, null), "prj_owner").id();
        var m = projectService.addMember(pid, member.getId(), "MEMBER", null, null, 50, "prj_owner");
        assertThat(m.active()).isTrue();

        // đang tham gia → vào được
        assertThatCode(() -> projectService.requireMember(pid, "prj_member", false)).doesNotThrowAnyException();

        // tạm ngưng → bị chặn
        var off = projectService.setMemberActive(pid, m.id(), false, "prj_owner");
        assertThat(off.active()).isFalse();
        assertThatThrownBy(() -> projectService.requireMember(pid, "prj_member", false))
                .isInstanceOf(ResponseStatusException.class);

        // chủ dự án và admin vẫn vào được bình thường
        assertThatCode(() -> projectService.requireMember(pid, "prj_owner", false)).doesNotThrowAnyException();
        assertThatCode(() -> projectService.requireMember(pid, "nguoi_la", true)).doesNotThrowAnyException();

        // mở lại → vào được, và thành viên vẫn còn nguyên trong danh sách (không bị gỡ)
        projectService.setMemberActive(pid, m.id(), true, "prj_owner");
        assertThatCode(() -> projectService.requireMember(pid, "prj_member", false)).doesNotThrowAnyException();
        assertThat(projectService.listMembers(pid)).extracting(ProjectDto.MemberResponse::userId)
                .contains(member.getId());
    }
}
