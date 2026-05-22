package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.TargetAdjustRespDTO;
import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitTargetAdjustCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetAdjustService 单元测试 (V1.2 Q3.2a).
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正常提交 → 启动 perf_target_adjust_v1 BPMN（不分对公/零售）</li>
 *   <li>目标方案不存在抛 TARGET_PLAN_NOT_FOUND</li>
 *   <li>必填字段校验：planId / subjectType / subjectId / cycleKey / ownerOrgId / reason / adjustments</li>
 *   <li>subjectType 非法值抛 VALIDATION_FAILED</li>
 *   <li>adjustments 为空抛 VALIDATION_FAILED</li>
 *   <li>adjustments.metricCode 重复抛 VALIDATION_FAILED</li>
 *   <li>applyNo 自动生成、status=IN_APPROVAL、remark JSON 包含 adjustments</li>
 *   <li>回写 processInstanceId</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TargetAdjustServiceTest {

    @Mock
    private PerfTargetAdjustApplyMapper applyMapper;

    @Mock
    private PerfTargetPlanMapper targetPlanMapper;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private UserApi userApi;

    @Mock
    private OrgApi orgApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    private TargetAdjustService service;

    private SubmitTargetAdjustCmd baseCmd() {
        return SubmitTargetAdjustCmd.builder()
                .planId("PLAN_001")
                .subjectType("EMP")
                .subjectId("EMP_001")
                .cycleKey("2026Q1")
                .ownerOrgId("ORG_001")
                .reason("2026 Q1 目标上调 20%")
                .applicant("admin")
                .adjustments(Arrays.asList(
                        SubmitTargetAdjustCmd.TargetAdjustment.builder()
                                .metricCode("M_DEP_BAL")
                                .oldValue(new BigDecimal("100"))
                                .newValue(new BigDecimal("120"))
                                .build(),
                        SubmitTargetAdjustCmd.TargetAdjustment.builder()
                                .metricCode("M_FEE_INCOME")
                                .oldValue(new BigDecimal("50"))
                                .newValue(new BigDecimal("60"))
                                .build()))
                .build();
    }

    @BeforeEach
    void setUp() {
        // 2026-05-21：注入 7 依赖（V1.4 S1.3 起 5 个 + 2026-05-21 加 UserApi/OrgApi 用于 getByIdDto 申请人姓名/机构展开）
        service = new TargetAdjustService(applyMapper, targetPlanMapper, workflowApi,
                currentUserApi, userApi, orgApi, perfScopeHelper);

        // 默认目标方案存在
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("PLAN_001");
        plan.setStatus("ACTIVE");
        when(targetPlanMapper.selectById("PLAN_001")).thenReturn(plan);
    }

    @Test
    @DisplayName("提交成功 → 启动 perf_target_adjust_v1 BPMN，状态 IN_APPROVAL")
    void submit_ok_startsWorkflow() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_T_001", null, null));

        String id = service.submit(baseCmd());

        assertThat(id).isNotBlank();
        ArgumentCaptor<StartProcessCmd> cmdCap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCap.capture());
        assertThat(cmdCap.getValue().getProcessDefinitionKey()).isEqualTo("perf_target_adjust_v1");
        assertThat(cmdCap.getValue().getBizType()).isEqualTo("TARGET_ADJUST");

        // 流程变量包含业务关键字段
        assertThat(cmdCap.getValue().getVariables()).containsEntry("planId", "PLAN_001");
        assertThat(cmdCap.getValue().getVariables()).containsEntry("subjectType", "EMP");
        assertThat(cmdCap.getValue().getVariables()).containsEntry("subjectId", "EMP_001");
        assertThat(cmdCap.getValue().getVariables()).containsEntry("cycleKey", "2026Q1");

        // 回写 processInstanceId
        verify(applyMapper).updateStatus(id, "IN_APPROVAL", "PI_T_001");
    }

    @Test
    @DisplayName("insert apply 状态=IN_APPROVAL，remark JSON 承载 adjustments + reason")
    void submit_persistsApplyWithRemarkJson() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_T_002", null, null));

        service.submit(baseCmd());

        ArgumentCaptor<PerfTargetAdjustApply> applyCap =
                ArgumentCaptor.forClass(PerfTargetAdjustApply.class);
        verify(applyMapper).insert(applyCap.capture());
        PerfTargetAdjustApply apply = applyCap.getValue();
        assertThat(apply.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(apply.getPlanId()).isEqualTo("PLAN_001");
        assertThat(apply.getSubjectType()).isEqualTo("EMP");
        assertThat(apply.getSubjectId()).isEqualTo("EMP_001");
        assertThat(apply.getCycleKey()).isEqualTo("2026Q1");
        assertThat(apply.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(apply.getCreatedBy()).isEqualTo("admin");
        assertThat(apply.getBusinessKey()).startsWith("TARGET_ADJUST:");

        // remark JSON 包含 adjustments 和 reason
        assertThat(apply.getRemark()).contains("adjustments");
        assertThat(apply.getRemark()).contains("M_DEP_BAL");
        assertThat(apply.getRemark()).contains("120");
        assertThat(apply.getRemark()).contains("M_FEE_INCOME");
        assertThat(apply.getRemark()).contains("reason");
        assertThat(apply.getRemark()).contains("目标上调");
    }

    @Test
    @DisplayName("目标方案不存在 → 抛 TARGET_PLAN_NOT_FOUND，不发起流程")
    void submit_planNotFound_throws() {
        when(targetPlanMapper.selectById("PLAN_001")).thenReturn(null);

        assertThatThrownBy(() -> service.submit(baseCmd()))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.TARGET_PLAN_NOT_FOUND);

        verify(workflowApi, never()).startProcess(any());
        verify(applyMapper, never()).insert(any(PerfTargetAdjustApply.class));
    }

    @Test
    @DisplayName("subjectType 非法 → 抛 VALIDATION_FAILED")
    void submit_invalidSubjectType_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setSubjectType("INVALID_TYPE");

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("planId 为空 → 抛 VALIDATION_FAILED")
    void submit_blankPlanId_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setPlanId(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("cycleKey 为空 → 抛 VALIDATION_FAILED")
    void submit_blankCycleKey_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setCycleKey(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("reason 为空 → 抛 VALIDATION_FAILED（高危必填）")
    void submit_blankReason_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setReason(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("adjustments 为空 → 抛 VALIDATION_FAILED")
    void submit_emptyAdjustments_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setAdjustments(Collections.emptyList());

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("adjustments.metricCode 重复 → 抛 VALIDATION_FAILED")
    void submit_duplicateMetricCode_throws() {
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setAdjustments(Arrays.asList(
                SubmitTargetAdjustCmd.TargetAdjustment.builder()
                        .metricCode("M_DEP_BAL").oldValue(new BigDecimal("100"))
                        .newValue(new BigDecimal("120")).build(),
                SubmitTargetAdjustCmd.TargetAdjustment.builder()
                        .metricCode("M_DEP_BAL").oldValue(new BigDecimal("100"))
                        .newValue(new BigDecimal("130")).build()));

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("subjectType=ORG 正常提交（双维度支持）")
    void submit_subjectTypeOrg_ok() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_T_ORG_001", null, null));
        SubmitTargetAdjustCmd cmd = baseCmd();
        cmd.setSubjectType("ORG");
        cmd.setSubjectId("ORG_101");

        String id = service.submit(cmd);

        assertThat(id).isNotBlank();
        ArgumentCaptor<PerfTargetAdjustApply> applyCap =
                ArgumentCaptor.forClass(PerfTargetAdjustApply.class);
        verify(applyMapper).insert(applyCap.capture());
        assertThat(applyCap.getValue().getSubjectType()).isEqualTo("ORG");
        assertThat(applyCap.getValue().getSubjectId()).isEqualTo("ORG_101");
    }

    @Test
    @DisplayName("getById 不存在 → 抛 TARGET_ADJUST_APPLY_NOT_FOUND")
    void getById_notFound_throws() {
        when(applyMapper.selectByTargetApplyId("NO_SUCH")).thenReturn(null);

        assertThatThrownBy(() -> service.getById("NO_SUCH"))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.TARGET_ADJUST_APPLY_NOT_FOUND);
    }

    @Test
    @DisplayName("withdraw IN_APPROVAL → 状态置 REJECTED")
    void withdraw_ok() {
        PerfTargetAdjustApply apply = new PerfTargetAdjustApply();
        apply.setId("TAA_001");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByTargetApplyId("TAA_001")).thenReturn(apply);

        service.withdraw("TAA_001", "用户取消", "admin");

        verify(applyMapper).updateStatus("TAA_001", "REJECTED", null);
    }

    @Test
    @DisplayName("withdraw APPROVED → 抛 VALIDATION_FAILED（非撤回态）")
    void withdraw_approvedApply_throws() {
        PerfTargetAdjustApply apply = new PerfTargetAdjustApply();
        apply.setId("TAA_002");
        apply.setStatus("APPROVED");
        when(applyMapper.selectByTargetApplyId("TAA_002")).thenReturn(apply);

        assertThatThrownBy(() -> service.withdraw("TAA_002", "试图撤回", "admin"))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    // ========== V1.4 S1.3: WORKFLOW_PARTICIPANT scope 路径 ==========

    @Test
    @DisplayName("V1.4 S1.3: pageDto → 调 PerfScopeHelper 5 参 overload，bizKeyCol=\"business_key\"，prefix=\"perf_target_adjust_\"")
    void pageDto_callsPerfScopeHelperWithBusinessKeyColAndTargetPrefix() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_WF");
        // Mock scope helper 返回 business_key IN (...) 片段
        PerfScopeHelper.Fragment frag = new PerfScopeHelper.Fragment(
                "business_key IN (#{scopeParams.bizKey0})",
                java.util.Map.of("bizKey0", "TARGET_ADJUST:APPLY_001"));
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_target_adjust_")))
                .thenReturn(frag);
        // Mock mapper 返回空列表（不关心结果）
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.emptyList());
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        service.pageDto(null, null, null, null, null, null, 1, 20);

        // 验证 perfScopeHelper 被调用时 bizKeyCol="business_key" + prefix="perf_target_adjust_"
        verify(perfScopeHelper).getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_target_adjust_"));
    }

    @Test
    @DisplayName("V1.4 S1.3: WORKFLOW_PARTICIPANT 无参与记录（fail-close）→ 分页结果空")
    void pageDto_workflowParticipantFailClose_returnsEmptyPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NOWF");
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(PerfScopeHelper.ScopeColumns.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(PerfScopeHelper.Fragment.failClose());
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq("1=0"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.emptyList());
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("1=0"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        PageResult<TargetAdjustRespDTO> result = service.pageDto(null, null, null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
    }
}
