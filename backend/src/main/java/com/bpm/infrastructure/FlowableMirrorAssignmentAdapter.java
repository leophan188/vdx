package com.bpm.infrastructure;

import com.bpm.domain.assignment.AssignmentPort;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter mirror phân công sang Flowable (AD-14). App `TASK_ASSIGNMENT` là nguồn sự thật; adapter này
 * set assignee/candidate-group của Flowable task tương ứng. taskId = id của Flowable task (khi chạy thật);
 * nếu task không tồn tại (vd test lõi phân công với taskId tự bịa) thì bỏ qua an toàn.
 */
@Component
public class FlowableMirrorAssignmentAdapter implements AssignmentPort {

    private static final Logger log = LoggerFactory.getLogger(FlowableMirrorAssignmentAdapter.class);

    private final TaskService taskService;

    public FlowableMirrorAssignmentAdapter(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void mirrorAssignee(String taskId, String assigneeUserId) {
        if (taskExists(taskId)) {
            taskService.setAssignee(taskId, assigneeUserId);
        } else {
            log.debug("[mirror] bỏ qua assignee — task {} không có trong Flowable", taskId);
        }
    }

    @Override
    public void mirrorCandidateGroup(String taskId, String orgUnitId) {
        if (taskExists(taskId)) {
            taskService.addCandidateGroup(taskId, orgUnitId);
        } else {
            log.debug("[mirror] bỏ qua candidate-group — task {} không có trong Flowable", taskId);
        }
    }

    private boolean taskExists(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).count() > 0;
    }
}
