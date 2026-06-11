package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void submitAllocAdjust_passesOriginalAllocListThroughToService() {
        // ownerOrgId 已带入，避免触发主机构反查；断言 items 与 originalAllocList 均透传到 Service Cmd
        AllocAdjustSubmitCmd cmd = AllocAdjustSubmitCmd.builder()
                .custType("CORP")
                .custId("C001")
                .allocDim("ACCOUNT")
                .bizKind("CORP_DEPOSIT")
                .accountNo("ACC1")
                .ownerOrgId("ORG1")
                .reason("理由")
                .applicant("U001")
                .items(List.of(AllocAdjustSubmitCmd.Item.builder()
                        .empId("U100").ratio(new BigDecimal("70")).build()))
                .originalAllocList(List.of(AllocAdjustSubmitCmd.OriginalItem.builder()
                        .empId("U900").username("E900").empChnName("原始人")
                        .ratio(new BigDecimal("100")).build()))
                .build();
        when(allocAdjustService.submit(any())).thenReturn("AA123");

        String applyId = facade.submitAllocAdjust(cmd);

        assertThat(applyId).isEqualTo("AA123");
        ArgumentCaptor<SubmitAllocAdjustCmd> captor = ArgumentCaptor.forClass(SubmitAllocAdjustCmd.class);
        verify(allocAdjustService).submit(captor.capture());
        SubmitAllocAdjustCmd serviceCmd = captor.getValue();
        assertThat(serviceCmd.getItems()).hasSize(1);
        assertThat(serviceCmd.getOriginalAllocList()).hasSize(1);
        SubmitAllocAdjustCmd.OriginalItem orig = serviceCmd.getOriginalAllocList().get(0);
        assertThat(orig.getEmpId()).isEqualTo("U900");
        assertThat(orig.getEmpChnName()).isEqualTo("原始人");
        assertThat(orig.getRatio()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void approveAllocAdjust_pass_passesDefaultRouteVarsToApproveByEmp() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        facade.approveAllocAdjust("A1", "E001", "1", "同意");

        // 手机端「同意」不带网关路由选择，后端按业务约定默认走最全链路：
        // gw1_corp_route → OWNER（原业绩所属人会签）；gw2_fin_route → LEADER（资财部负责人审批）。
        // 否则排他网关无分支命中，complete() 抛异常，流程到不了 <end>，状态无法回写 APPROVED。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> formCap = ArgumentCaptor.forClass(Map.class);
        verify(workflowApi).approveByEmp(eq("T1"), eq("E001"), eq("同意"), formCap.capture());
        assertThat(formCap.getValue())
                .containsEntry("corpRouteTo", "OWNER")
                .containsEntry("finRouteTo", "LEADER");
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }

    @Test
    void approveAllocAdjust_reject_callsRejectByEmpWithTaskId() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        facade.approveAllocAdjust("A1", "E001", "2", "不同意");

        verify(workflowApi).rejectByEmp("T1", "E001", "不同意");
        verify(workflowApi, never()).approveByEmp(any(), any(), any(), any());
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

        verify(workflowApi, never()).approveByEmp(any(), any(), any(), any());
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }

    @Test
    void approveAllocAdjust_illegalStatus_throws() {
        when(todoQueryApi.findTaskRespByBusinessKeysByEmp("E001", List.of(BIZ_KEY)))
                .thenReturn(Map.of(BIZ_KEY, task("T1")));

        assertThatThrownBy(() -> facade.approveAllocAdjust("A1", "E001", "9", "x"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(workflowApi, never()).approveByEmp(any(), any(), any(), any());
        verify(workflowApi, never()).rejectByEmp(any(), any(), any());
    }
}
