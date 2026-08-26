package com.bpm;

import com.bpm.api.dto.EmployeeDto;
import com.bpm.api.dto.ProjectDto;
import com.bpm.application.EmployeeService;
import com.bpm.application.ProjectService;
import com.bpm.domain.hr.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Trạng thái nhân sự: lọc theo nhóm, và nghỉ việc thì mất quyền vào dự án. */
@SpringBootTest
@ActiveProfiles("test")
class EmployeeStatusSyncTest {

    @Autowired EmployeeService employeeService;
    @Autowired ProjectService projectService;

    private Employee create(String code, String status) {
        return employeeService.createManual(new EmployeeDto.CreateRequest(
                code, status, "Nhân sự " + code, "DEV", "Nhân Viên", "PDX.1", "VMO",
                "01/01/2024", "01/01/1995", null, null, null, null, "Middle"), "tester");
    }

    /** Bộ lọc dùng hai NHÓM khớp ô đếm, thay vì từng chuỗi trạng thái thô. */
    @Test
    void listFiltersByStatusGroup() {
        create("SYNC01", "Đang làm việc");
        create("SYNC02", "Đã nghỉ việc");
        create("SYNC03", "Chưa Onboard");

        var active = employeeService.list(EmployeeService.STATUS_GROUP_ACTIVE, null, null, "SYNC");
        var inactive = employeeService.list(EmployeeService.STATUS_GROUP_INACTIVE, null, null, "SYNC");

        assertThat(active).extracting(Employee::getEmpCode).containsExactly("SYNC01");
        assertThat(inactive).extracting(Employee::getEmpCode).containsExactlyInAnyOrder("SYNC02", "SYNC03");
    }

    /** Biến thể chuỗi vẫn được coi là đang làm việc — khớp cách màn Nhân sự đếm. */
    @Test
    void activeAcceptsStatusVariants() {
        Employee e = create("SYNC04", "Đang làm việc - thử việc");
        assertThat(e.isActive()).isTrue();
        assertThat(employeeService.list(EmployeeService.STATUS_GROUP_ACTIVE, null, null, "SYNC04"))
                .hasSize(1);
    }

    /** Chuyển sang đã nghỉ → mọi dự án đang tham gia bị cắt quyền, nhưng vẫn còn tên trong danh sách. */
    @Test
    void leavingRevokesProjectAccess() {
        Employee e = create("SYNC05", "Đang làm việc");
        String username = e.getEmpCode();

        String pid = projectService.create(new ProjectDto.ProjectRequest(
                "SYNCP", "Dự án đồng bộ", null, null, null, null, null, null, null), "tester").id();
        var m = projectService.addMember(pid, e.getUserAccountId(), "MEMBER", null, null, 50, "tester");
        assertThat(m.active()).isTrue();
        assertThatCode(() -> projectService.requireMember(pid, username, false)).doesNotThrowAnyException();

        employeeService.update(e.getId(), new EmployeeDto.UpdateRequest(
                "Đã nghỉ việc", e.getFullName(), e.getJobPosition(), e.getTitle(), e.getDeptCode(),
                e.getUnit(), "01/01/2024", "01/01/1995", null, null, null, null, "Middle"), "tester");

        assertThatThrownBy(() -> projectService.requireMember(pid, username, false))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(projectService.listMembers(pid))
                .anySatisfy(x -> {
                    assertThat(x.userId()).isEqualTo(e.getUserAccountId());
                    assertThat(x.active()).isFalse();
                });
    }
}
