package com.bpm;

import com.bpm.application.OrgUnitService;
import com.bpm.application.PositionService;
import com.bpm.application.ProcessService;
import com.bpm.application.RoleService;
import com.bpm.application.UserAccountService;
import com.bpm.application.WorkflowService;
import com.bpm.domain.UserAccount;
import com.bpm.domain.assignment.AssignmentStatus;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.position.Position;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.workflow.WorkflowInstance;
import com.bpm.infrastructure.TaskAssignmentRepository;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowServiceTest {

    @Autowired ProcessService processService;
    @Autowired WorkflowService workflowService;
    @Autowired TaskService taskService;
    @Autowired OrgUnitService orgService;
    @Autowired PositionService positionService;
    @Autowired UserAccountService userService;
    @Autowired RoleService roleService;
    @Autowired TaskAssignmentRepository assignmentRepo;
    @Autowired ProcessEngine processEngine;

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
              <process id="TestProc" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="Task_Tao"/>
                <userTask id="Task_Tao" name="Soạn"/>
                <sequenceFlow id="f2" sourceRef="Task_Tao" targetRef="e"/>
                <endEvent id="e"/>
              </process>
            </definitions>""";

    @Test
    void start_publishedProcess_createsInstance_andFirstTaskAssignedToHolder() {
        // org + người + vị trí (giữ bởi user)
        OrgUnit unit = orgService.create("Vụ WF", null, "test");
        UserAccount holder = userService.createAccount("wf_holder", "Secret123", "Người Giữ", "USER", "test");
        Position pos = positionService.create("Chuyên viên", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), holder.getId(), "test");

        // quy trình: bước Task_Tao phân công theo vị trí
        ProcessDefinition p = processService.create("wf-test", "QT test", "test");
        String meta = "{\"Task_Tao\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + pos.getId() + "\"}}";
        processService.saveDesign(p.getId(), BPMN, meta, "test");
        processService.publish(p.getId(), "test");

        WorkflowInstance wi = workflowService.start(p.getId(), Map.of("muc_uu_tien", "Cao"), "test");
        assertThat(wi.getFlowableInstanceId()).isNotBlank();
        assertThat(wi.getProcessVersion()).isEqualTo(1);

        // việc bước đầu được tạo VÀ gán đúng người giữ vị trí (lõi phân công + mirror Flowable)
        Task task = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getAssignee()).isEqualTo(holder.getId());
    }

    private static final String BPMN2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
              <process id="LoopProc" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="Task_Tao"/>
                <userTask id="Task_Tao" name="Soạn"/>
                <sequenceFlow id="f2" sourceRef="Task_Tao" targetRef="Task_Duyet"/>
                <userTask id="Task_Duyet" name="Duyệt"/>
                <sequenceFlow id="f3" sourceRef="Task_Duyet" targetRef="e"/>
                <endEvent id="e"/>
              </process>
            </definitions>""";

    @Test
    void fullLoop_inbox_complete_advancesAndAssignsNextStep() {
        OrgUnit unit = orgService.create("Vụ Loop", null, "test");
        UserAccount u1 = userService.createAccount("loop_cv", "Secret123", "CV", "USER", "test");
        UserAccount u2 = userService.createAccount("loop_tp", "Secret123", "TP", "USER", "test");
        Position p1 = positionService.create("CV Loop", unit.getId(), "test");
        Position p2 = positionService.create("TP Loop", unit.getId(), "test");
        positionService.assignHolder(p1.getId(), u1.getId(), "test");
        positionService.assignHolder(p2.getId(), u2.getId(), "test");

        ProcessDefinition p = processService.create("wf-loop", "QT loop", "test");
        String meta = "{\"Task_Tao\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + p1.getId() + "\",\"actions\":[\"Gửi duyệt\"]},"
                + "\"Task_Duyet\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + p2.getId() + "\",\"actions\":[\"Duyệt\",\"Trả lại\"]}}";
        processService.saveDesign(p.getId(), BPMN2, meta, "test");
        processService.publish(p.getId(), "test");

        workflowService.start(p.getId(), Map.of("tieu_de", "HS"), "test");

        // u1 thấy việc bước đầu
        var inbox1 = workflowService.inbox("loop_cv");
        assertThat(inbox1).hasSize(1);
        assertThat(inbox1.get(0).stepName()).isEqualTo("Soạn");

        // chi tiết: có hành động cấu hình
        var detail = workflowService.detail(inbox1.get(0).taskId());
        assertThat(detail.actions()).containsExactly("Gửi duyệt");

        // u1 hoàn thành → việc chuyển sang u2 (gán bước kế tự động)
        workflowService.complete(inbox1.get(0).taskId(), "Gửi duyệt", Map.of("noi_dung", "abc"), "loop_cv");
        assertThat(workflowService.inbox("loop_cv")).isEmpty();
        var inbox2 = workflowService.inbox("loop_tp");
        assertThat(inbox2).hasSize(1);
        assertThat(inbox2.get(0).stepName()).isEqualTo("Duyệt");

        // u2 duyệt → instance kết thúc, hộp thư rỗng
        workflowService.complete(inbox2.get(0).taskId(), "Duyệt", Map.of(), "loop_tp");
        assertThat(workflowService.inbox("loop_tp")).isEmpty();
    }

    private static final String BPMN_GW = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" targetNamespace="test">
              <process id="GwProc" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f0" sourceRef="s" targetRef="gw"/>
                <exclusiveGateway id="gw" default="fLow"/>
                <sequenceFlow id="fHigh" sourceRef="gw" targetRef="Task_Cao"/>
                <sequenceFlow id="fLow" sourceRef="gw" targetRef="Task_Thuong"/>
                <userTask id="Task_Cao" name="Ưu tiên cao"/>
                <userTask id="Task_Thuong" name="Thường"/>
                <sequenceFlow id="e1" sourceRef="Task_Cao" targetRef="end"/>
                <sequenceFlow id="e2" sourceRef="Task_Thuong" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>""";

    @Test
    void gateway_routesByFormValue_condition() {
        ProcessDefinition p = processService.create("wf-gw", "QT gateway", "test");
        String meta = "{\"fHigh\":{\"condition\":{\"field\":\"muc\",\"op\":\"eq\",\"value\":\"Cao\"}}}";
        processService.saveDesign(p.getId(), BPMN_GW, meta, "test");
        processService.publish(p.getId(), "test");

        // muc = Cao → nhánh "Ưu tiên cao"
        WorkflowInstance hi = workflowService.start(p.getId(), Map.of("muc", "Cao"), "test");
        Task th = taskService.createTaskQuery().processInstanceId(hi.getFlowableInstanceId()).singleResult();
        assertThat(th.getName()).isEqualTo("Ưu tiên cao");

        // muc khác → nhánh mặc định "Thường"
        WorkflowInstance lo = workflowService.start(p.getId(), Map.of("muc", "Thấp"), "test");
        Task tl = taskService.createTaskQuery().processInstanceId(lo.getFlowableInstanceId()).singleResult();
        assertThat(tl.getName()).isEqualTo("Thường");
    }

    @Test
    void monitor_listsInstance_timeline_andCancel() {
        ProcessDefinition p = processService.create("wf-mon", "QT theo dõi", "test");
        processService.saveDesign(p.getId(), BPMN, "{}", "test");
        processService.publish(p.getId(), "test");
        WorkflowInstance wi = workflowService.start(p.getId(), Map.of(), "test");

        var item = workflowService.instances().stream()
                .filter(i -> i.id().equals(wi.getId())).findFirst().orElseThrow();
        assertThat(item.status()).isEqualTo("RUNNING");
        assertThat(item.currentStep()).isEqualTo("Soạn");

        var tl = workflowService.timeline(wi.getId());
        assertThat(tl.steps()).anyMatch(s -> "Soạn".equals(s.stepName()) && "ACTIVE".equals(s.status()));

        workflowService.cancel(wi.getId(), "không cần nữa", "test");
        assertThat(workflowService.get(wi.getId()).getStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> workflowService.cancel(wi.getId(), "x", "test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sla_overdue_flaggedInInbox() {
        OrgUnit unit = orgService.create("Vụ SLA", null, "test");
        UserAccount holder = userService.createAccount("sla_holder", "Secret123", "Người SLA", "USER", "test");
        Position pos = positionService.create("CV SLA", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), holder.getId(), "test");

        ProcessDefinition p = processService.create("wf-sla", "QT SLA", "test");
        String meta = "{\"Task_Tao\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + pos.getId() + "\",\"slaHours\":4}}";
        processService.saveDesign(p.getId(), BPMN, meta, "test");
        processService.publish(p.getId(), "test");

        // Lùi đồng hồ Flowable 10h để việc được tạo "10h trước" → quá hạn 4h.
        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(10))));
        try {
            workflowService.start(p.getId(), Map.of(), "test");
        } finally {
            clock.reset();
        }

        var item = workflowService.inbox("sla_holder").get(0);
        assertThat(item.slaHours()).isEqualTo(4);
        assertThat(item.dueAt()).isNotNull();
        assertThat(item.overdue()).isTrue();
    }

    @Test
    void claim_roleTask_movesFromQueueToMyTask() {
        roleService.createRole("CV_ROLE", "Chuyên viên (vai trò)", Set.of(), "test");
        OrgUnit unit = orgService.create("Vụ Claim", null, "test");
        UserAccount u = userService.createAccount("claim_u", "Secret123", "Người Claim", "USER", "test");
        Position pos = positionService.create("CV Claim", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), u.getId(), "test");
        roleService.assignRoleToPosition(pos.getId(), "CV_ROLE", "test");

        ProcessDefinition p = processService.create("wf-claim", "QT claim", "test");
        processService.saveDesign(p.getId(), BPMN, "{\"Task_Tao\":{\"assigneeType\":\"ROLE\",\"assigneeId\":\"CV_ROLE\"}}", "test");
        processService.publish(p.getId(), "test");
        workflowService.start(p.getId(), Map.of(), "test");

        var inbox = workflowService.inbox("claim_u");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).claimable()).isTrue(); // việc chờ nhận theo vai trò

        workflowService.claim(inbox.get(0).taskId(), "claim_u");
        var after = workflowService.inbox("claim_u");
        assertThat(after).hasSize(1);
        assertThat(after.get(0).claimable()).isFalse(); // đã thành việc của tôi
    }

    @Test
    void extend_addsBonusHours_clearsOverdue() {
        OrgUnit unit = orgService.create("Vụ Ext", null, "test");
        UserAccount h = userService.createAccount("ext_holder", "Secret123", "Người Ext", "USER", "test");
        Position pos = positionService.create("CV Ext", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), h.getId(), "test");
        ProcessDefinition p = processService.create("wf-ext", "QT ext", "test");
        processService.saveDesign(p.getId(), BPMN,
                "{\"Task_Tao\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + pos.getId() + "\",\"slaHours\":4}}", "test");
        processService.publish(p.getId(), "test");

        var clock = processEngine.getProcessEngineConfiguration().getClock();
        clock.setCurrentTime(Date.from(Instant.now().minus(Duration.ofHours(6))));
        try {
            workflowService.start(p.getId(), Map.of(), "test");
        } finally {
            clock.reset();
        }

        var item = workflowService.inbox("ext_holder").get(0);
        assertThat(item.overdue()).isTrue(); // tạo 6h trước, SLA 4h → quá hạn
        workflowService.extend(item.taskId(), 10, "cần thêm thời gian", "ext_holder");
        assertThat(workflowService.inbox("ext_holder").get(0).overdue()).isFalse(); // +10h → hạn = tạo+14h
    }

    @Test
    void cancel_cascadesTaskAssignments() {
        OrgUnit unit = orgService.create("Vụ Cas", null, "test");
        UserAccount h = userService.createAccount("cas_h", "Secret123", "Cas", "USER", "test");
        Position pos = positionService.create("CV Cas", unit.getId(), "test");
        positionService.assignHolder(pos.getId(), h.getId(), "test");
        ProcessDefinition p = processService.create("wf-cas", "QT cas", "test");
        processService.saveDesign(p.getId(), BPMN,
                "{\"Task_Tao\":{\"assigneeType\":\"POSITION\",\"assigneeId\":\"" + pos.getId() + "\"}}", "test");
        processService.publish(p.getId(), "test");
        WorkflowInstance wi = workflowService.start(p.getId(), Map.of(), "test");
        String tid = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).singleResult().getId();
        assertThat(assignmentRepo.findByTaskId(tid)).isPresent();

        workflowService.cancel(wi.getId(), "không cần nữa", "test");
        assertThat(assignmentRepo.findByTaskId(tid).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
    }

    private static final String BPMN_PARALLEL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
              <process id="ParProc" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f0" sourceRef="s" targetRef="fork"/>
                <parallelGateway id="fork"/>
                <sequenceFlow id="fa" sourceRef="fork" targetRef="Task_A"/>
                <sequenceFlow id="fb" sourceRef="fork" targetRef="Task_B"/>
                <userTask id="Task_A" name="Nhánh A"/>
                <userTask id="Task_B" name="Nhánh B"/>
                <sequenceFlow id="fa2" sourceRef="Task_A" targetRef="join"/>
                <sequenceFlow id="fb2" sourceRef="Task_B" targetRef="join"/>
                <parallelGateway id="join"/>
                <sequenceFlow id="fe" sourceRef="join" targetRef="e"/>
                <endEvent id="e"/>
              </process>
            </definitions>""";

    @Test
    void parallelGateway_forksTwoTasks_thenJoins() {
        ProcessDefinition p = processService.create("wf-par", "QT song song", "test");
        processService.saveDesign(p.getId(), BPMN_PARALLEL, "{}", "test");
        processService.publish(p.getId(), "test");
        WorkflowInstance wi = workflowService.start(p.getId(), Map.of(), "test");

        // fork → 2 việc song song cùng tồn tại
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(wi.getFlowableInstanceId()).list();
        assertThat(tasks).hasSize(2);

        // hoàn thành cả 2 → join → instance kết thúc
        for (Task t : tasks) {
            workflowService.complete(t.getId(), "Xong", Map.of(), "test");
        }
        assertThat(workflowService.get(wi.getId()).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void start_unpublishedProcess_blocked() {
        ProcessDefinition p = processService.create("wf-unpub", "QT chưa publish", "test");
        processService.saveDesign(p.getId(), BPMN, null, "test");
        assertThatThrownBy(() -> workflowService.start(p.getId(), null, "test"))
                .isInstanceOf(IllegalStateException.class);
    }
}
