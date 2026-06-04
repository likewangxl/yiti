package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfApprovalCmdFacade#approveAllocAdjust 单测：callpu 无会话审批编排。
 * <p>
 * 校验：按 empId 候选组可见性解析 taskId（即角色校验）→ 按 apprStatus 走 workflow 无会话通过/驳回；
 * 解析不到待办 taskId 时拒绝审批（无权）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PerfApprovalCmdFacadeTest {

    @Mock private AllocAdjustService allocAdjustService;
    @Mock private UserApi userApi;
    @Mock private TodoQueryApi todoQueryApi;
    @Mock private WorkflowApi workflowApi;

    @InjectMocks private PerfApprovalCmdFacade facade;

    private static final String BIZ_KEY = "ALLOC_ADJUST:A1";

    private TaskRespDTO task(String taskId) {
        TaskRespDTO t = new TaskRespDTO();
        t.setTaskId(taskId);
        t.setBusinessKey(BIZ_KEY);
        return t;
    }

    @Test
    void approveAllocAdjust_pass_callsApproveByEmpWithTaskId() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        facade.approveAllocAdjust("A1", "E001", "1", "同意");

        verify(workflowApi).approveByEmp("T1", "E001", "同意");
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }

    @Test
    void approveAllocAdjust_reject_callsRejectByEmpWithTaskId() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        facade.approveAllocAdjust("A1", "E001", "2", "不同意");

        verify(workflowApi).rejectByEmp("T1", "E001", "不同意");
        verify(workflowApi, never()).approveByEmp(any(), any(), any());
    }

    @Test
    void approveAllocAdjust_blankOpinion_usesDefaultRejectText() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        facade.approveAllocAdjust("A1", "E001", "2", null);

        verify(workflowApi).rejectByEmp("T1", "E001", "手机端驳回");
    }

    @Test
    void approveAllocAdjust_noVisibleTask_throwsAndDoesNotApprove() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Collections.emptyMap());

        assertThatThrownBy(() -> facade.approveAllocAdjust("A1", "E001", "1", "同意"))
                .isInstanceOf(IllegalStateException.class);

        verify(workflowApi, never()).approveByEmp(any(), any(), any());
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }

    @Test
    void approveAllocAdjust_illegalStatus_throws() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        assertThatThrownBy(() -> facade.approveAllocAdjust("A1", "E001", "9", "x"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(workflowApi, never()).approveByEmp(any(), any(), any());
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }
}
