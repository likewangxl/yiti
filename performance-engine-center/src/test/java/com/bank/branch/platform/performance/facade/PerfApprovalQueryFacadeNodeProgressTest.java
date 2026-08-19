package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import com.bank.branch.platform.workflow.api.dto.TaskCandidateUserDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code getAllocAdjustDetail} 的「当前节点 / 下一节点」字段单元测试（TDD Red→Green）。
 *
 * <p>直接以构造器注入 mock（避免与陈旧的 {@code PerfApprovalQueryFacadeDetailTest} 的
 * AddressBookApi 装配混淆），聚焦节点进度逻辑。</p>
 */
class PerfApprovalQueryFacadeNodeProgressTest {

    private final AllocAdjustTodoService todoService = mock(AllocAdjustTodoService.class);
    private final AllocAdjustDoneService doneService = mock(AllocAdjustDoneService.class);
    private final UserApi userApi = mock(UserApi.class);
    private final PerfAllocAdjustApplyMapper applyMapper = mock(PerfAllocAdjustApplyMapper.class);
    private final AllocAdjustService allocAdjustService = mock(AllocAdjustService.class);
    private final WorkflowQueryApi workflowQueryApi = mock(WorkflowQueryApi.class);

    private final PerfApprovalQueryFacade facade = new PerfApprovalQueryFacade(
            todoService, doneService, userApi, applyMapper, allocAdjustService, workflowQueryApi);

    private PerfAllocAdjustApply apply(String status, String custType, String pid) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId("PA_1");
        a.setApplyNo("AA001");
        a.setCustId("C001");
        a.setCustName("客户");
        a.setCustType(custType);
        a.setStatus(status);
        a.setCreatedBy("U001");
        a.setProcessInstanceId(pid);
        return a;
    }

    private void stubLoad(PerfAllocAdjustApply a) {
        when(allocAdjustService.getById("PA_1"))
                .thenReturn(new AllocAdjustService.ApplyWithItems(a, List.of()));
        lenient().when(todoService.listMyTodosByEmp(
                        any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 0L, List.of()));
        lenient().when(userApi.getUserName(any())).thenReturn("王五");
    }

    private ProcessDiagramDTO diagramWithActive(String nodeKey, String nodeName) {
        ProcessDiagramNodeDTO active = new ProcessDiagramNodeDTO();
        active.setNodeKey(nodeKey);
        active.setNodeName(nodeName);
        active.setNodeType("userTask");
        active.setStatus("ACTIVE");

        ProcessDiagramNodeDTO done = new ProcessDiagramNodeDTO();
        done.setNodeKey("branch_approve");
        done.setNodeName("机构负责人审批");
        done.setNodeType("userTask");
        done.setStatus("COMPLETED");

        ProcessDiagramDTO d = new ProcessDiagramDTO();
        d.setNodes(List.of(done, active));
        return d;
    }

    @Test
    void inApproval_corp_financeReviewActive_currentAndNextResolved() {
        PerfAllocAdjustApply a = apply("IN_APPROVAL", "CORP", "PID_1");
        stubLoad(a);
        when(workflowQueryApi.getProcessNodes("PID_1"))
                .thenReturn(diagramWithActive("finance_review", "资财部经办审批"));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("资财部经办审批");
        assertThat(d.getCurrentNodeKey()).isEqualTo("finance_review");
        assertThat(d.getNextNode()).isEqualTo("资财部负责人审批");
    }

    @Test
    void inApproval_returnsCurrentNodeApproverNameAndEmployeeNo() {
        PerfAllocAdjustApply a = apply("IN_APPROVAL", "CORP", "PID_1");
        stubLoad(a);
        when(workflowQueryApi.getProcessNodes("PID_1"))
                .thenReturn(diagramWithActive("finance_review", "资财部经办审批"));
        when(workflowQueryApi.getActiveTaskCandidates("PID_1"))
                .thenReturn(List.of(new TaskCandidateUserDTO("E001", "10001", "张三")));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNodeApprovers()).singleElement().satisfies(approver -> {
            assertThat(approver.getEmployeeName()).isEqualTo("张三");
            assertThat(approver.getEmployeeNo()).isEqualTo("10001");
        });
    }

    @Test
    void inApproval_corp_lastNode_nextIsFlowEnd() {
        PerfAllocAdjustApply a = apply("IN_APPROVAL", "CORP", "PID_1");
        stubLoad(a);
        when(workflowQueryApi.getProcessNodes("PID_1"))
                .thenReturn(diagramWithActive("finance_leader_approve", "资财部负责人审批"));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("资财部负责人审批");
        assertThat(d.getCurrentNodeKey()).isEqualTo("finance_leader_approve");
        assertThat(d.getNextNode()).isEqualTo("流程结束");
    }

    @Test
    void approved_terminalText_nextIsNone() {
        PerfAllocAdjustApply a = apply("APPROVED", "CORP", "PID_1");
        stubLoad(a);

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("已完成");
        assertThat(d.getCurrentNodeKey()).isNull();
        assertThat(d.getNextNode()).isEqualTo("无");
        assertThat(d.getCurrentNodeApprovers()).isEmpty();
    }

    @Test
    void rejected_terminalText() {
        PerfAllocAdjustApply a = apply("REJECTED", "CORP", "PID_1");
        stubLoad(a);

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("已拒绝");
        assertThat(d.getNextNode()).isEqualTo("无");
    }

    @Test
    void withdrawn_terminalText() {
        PerfAllocAdjustApply a = apply("WITHDRAWN", "CORP", "PID_1");
        stubLoad(a);

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("已撤回");
        assertThat(d.getNextNode()).isEqualTo("无");
    }

    @Test
    void inApproval_noPid_gracefulFallback() {
        PerfAllocAdjustApply a = apply("IN_APPROVAL", "CORP", null);
        stubLoad(a);

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("审批中");
        assertThat(d.getCurrentNodeKey()).isNull();
        assertThat(d.getNextNode()).isEmpty();
    }

    @Test
    void inApproval_retail_originalOwner_skipsLeader() {
        PerfAllocAdjustApply a = apply("IN_APPROVAL", "RETAIL", "PID_1");
        stubLoad(a);
        when(workflowQueryApi.getProcessNodes("PID_1"))
                .thenReturn(diagramWithActive("original_owner_approve", "原业绩所属人审批"));

        AllocAdjustDetailDTO d = facade.getAllocAdjustDetail("PA_1", "U001");

        assertThat(d.getCurrentNode()).isEqualTo("原业绩所属人审批");
        assertThat(d.getCurrentNodeKey()).isEqualTo("original_owner_approve");
        assertThat(d.getNextNode()).isEqualTo("资财部经办审批");
    }
}
