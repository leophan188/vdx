package com.bpm;

import com.bpm.api.dto.ProjectDto;
import com.bpm.application.ProjectService;
import com.bpm.application.ProjectTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quy tắc phân cấp công việc: Epic → Story → Task → Sub-task (lồng nhiều cấp) → Bug/Issue. */
@SpringBootTest
@ActiveProfiles("test")
class ProjectTaskHierarchyTest {

    @Autowired ProjectService projectService;
    @Autowired ProjectTaskService taskService;

    private String newProject(String code) {
        return projectService.create(new ProjectDto.ProjectRequest(
                code, "Dự án " + code, null, null, null, null, null, null, null), "tester").id();
    }

    private ProjectDto.TaskResponse task(String projectId, String type, String parentId, String title) {
        return taskService.create(projectId, new ProjectDto.TaskRequest(
                parentId, title, null, type, null, null, null, 1.0, null, null,
                null, null, null, null, null, null, null, null, null, null, null), "tester");
    }

    /** Sub-task lồng trong Sub-task, nhiều cấp — yêu cầu nghiệp vụ khi việc còn phải tách nhỏ tiếp. */
    @Test
    void subtaskCanNestInsideAnotherSubtask() {
        String p = newProject("HIER1");
        var epic = task(p, "EPIC", null, "Epic");
        var story = task(p, "STORY", epic.id(), "Story");
        var parentTask = task(p, "TASK", story.id(), "Task");

        var sub1 = task(p, "SUBTASK", parentTask.id(), "Sub-task cấp 1");
        var sub2 = task(p, "SUBTASK", sub1.id(), "Sub-task cấp 2");
        var sub3 = task(p, "SUBTASK", sub2.id(), "Sub-task cấp 3");

        assertThat(sub2.parentId()).isEqualTo(sub1.id());
        assertThat(sub3.parentId()).isEqualTo(sub2.id());
    }

    /** Bug/Issue vẫn treo được dưới Sub-task ở bất kỳ cấp nào. */
    @Test
    void bugCanHangUnderNestedSubtask() {
        String p = newProject("HIER2");
        var epic = task(p, "EPIC", null, "Epic");
        var story = task(p, "STORY", epic.id(), "Story");
        var parentTask = task(p, "TASK", story.id(), "Task");
        var sub1 = task(p, "SUBTASK", parentTask.id(), "Sub-task cấp 1");
        var sub2 = task(p, "SUBTASK", sub1.id(), "Sub-task cấp 2");

        var bug = taskService.create(p, new ProjectDto.TaskRequest(
                sub2.id(), "Lỗi trong sub-task lồng", null, "BUG", null, null, null, 1.0, null, null,
                null, null, null, null, null, null, null, null, null, 0.5, null), "tester");

        assertThat(bug.parentId()).isEqualTo(sub2.id());
    }

    /** Mở phân cấp Sub-task KHÔNG được nới lỏng các ràng buộc còn lại. */
    @Test
    void otherHierarchyRulesStayStrict() {
        String p = newProject("HIER3");
        var epic = task(p, "EPIC", null, "Epic");
        var story = task(p, "STORY", epic.id(), "Story");

        // Sub-task không được treo thẳng dưới Story
        assertThatThrownBy(() -> task(p, "SUBTASK", story.id(), "Sai cha"))
                .isInstanceOf(IllegalArgumentException.class);
        // Sub-task bắt buộc có cha
        assertThatThrownBy(() -> task(p, "SUBTASK", null, "Không cha"))
                .isInstanceOf(IllegalArgumentException.class);
        // Story vẫn chỉ thuộc Epic
        assertThatThrownBy(() -> task(p, "STORY", story.id(), "Story dưới Story"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
