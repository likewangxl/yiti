package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.TaskCandidateUserDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustService 单元测试 (V1.2 Q2.2).
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正常提交 CORP_LOAN → 路由 corp_v1 BPMN</li>
 *   <li>客户不存在抛 VALIDATION_FAILED</li>
 *   <li>items 为空抛 VALIDATION_FAILED</li>
 *   <li>items 同一员工重复抛 VALIDATION_FAILED</li>
 *   <li>ratio 之和超过 100（RULE 维度）抛 VALIDATION_FAILED</li>
 *   <li>applyNo 自动生成、持久化 apply + items、回写 processInstanceId</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllocAdjustServiceTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;

    @Mock
    private PerfAllocAdjustItemMapper itemMapper;

    @Mock
    private CustAllocRelationMapper allocRelationMapper;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private WorkflowQueryApi workflowQueryApi;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    @Mock
    private UserApi userApi;

    @Mock
    private OrgApi orgApi;

    @Mock
    private AllocAdjustPreviewService allocAdjustPreviewService;

    @InjectMocks
    private AllocAdjustService service;

    private SubmitAllocAdjustCmd baseCmd(String bizKind) {
        return SubmitAllocAdjustCmd.builder()
                .custId("CN-001")
                .allocDim("RULE")
                .bizKind(bizKind)
                .ownerOrgId("ORG_001")
                .applicant("admin")
                .reason("年度岗位调整")
                .items(Arrays.asList(
                        SubmitAllocAdjustCmd.Item.builder()
                                .empId("EMP_A").ratio(new BigDecimal("60.00")).build(),
                        SubmitAllocAdjustCmd.Item.builder()
                                .empId("EMP_B").ratio(new BigDecimal("40.00")).build()))
                .build();
    }

    @BeforeEach
    void setUp() {
        // 默认按客户编号 CN-001 命中，内部主键 = CUST_001
        CustomerDTO cust = new CustomerDTO();
        cust.setId("CUST_001");
        cust.setCustNo("CN-001");
        when(customerQueryApi.getCustomerByCustNo("CN-001")).thenReturn(Optional.of(cust));
        // 发起机构级别（设计器网关分流依据 + submit 前置校验「仅限 2/3 级机构发起」）：种 2 级
        OrgDTO org = new OrgDTO();
        org.setOrgLevel(2);
        org.setOrgCode("ORG_L2");
        when(orgApi.getOrg(anyString())).thenReturn(org);
        // 原业绩所属机构负责人解析（startApprovalWorkflow 内 fail-fast 前置校验）：
        // 原分配人主机构=2级（就地），该机构 BRANCH_HEAD 持有者非空
        when(orgApi.getUserMainOrg(anyString())).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg(anyString(), anyString()))
                .thenReturn(java.util.List.of("ORG_LEADER_1"));
        // 审批流已改走设计器动态流程：submit 经 resolveDesignerProcDefKey(flowKey) 取已部署 procDefKey。
        // 单测把「对公/零售设计器 flowKey」解析回原静态流程 KEY，路由断言语义保持不变。
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer"))
                .thenReturn(AllocAdjustService.PROCESS_KEY_CORP);
        when(workflowApi.resolveDesignerProcDefKey("alloc_retail_designer"))
                .thenReturn(AllocAdjustService.PROCESS_KEY_RETAIL);
        // 默认「原业绩分配」历史审批通过非空，使提交校验「至少 1 条原业绩分配」通过；
        // 需要测手工录入/无原业绩场景的用例可覆盖此 stub。
        var defaultOwner = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        defaultOwner.setEmpId("rm_zhang");
        when(allocAdjustPreviewService.getLastApprovedAllocPreview(anyString(), anyString()))
                .thenReturn(java.util.List.of(defaultOwner));
    }

    @Test
    @DisplayName("提交 CORP_LOAN → 启动 perf_alloc_adjust_corp_v1 BPMN")
    void submit_corpLoan_startsCorpWorkflow() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_001", null, null));

        String id = service.submit(baseCmd("CORP_LOAN"));

        assertThat(id).isNotBlank();
        ArgumentCaptor<StartProcessCmd> cmdCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().getProcessDefinitionKey())
                .isEqualTo("perf_alloc_adjust_corp_v1");
        assertThat(cmdCaptor.getValue().getBizType()).isEqualTo("ALLOC_ADJUST");

        // 回写 processInstanceId
        verify(applyMapper).updateStatus(id, "IN_APPROVAL", "PI_001");
    }

    @Test
    @DisplayName("提交 RETAIL_CARD → 启动 perf_alloc_adjust_retail_v1 BPMN")
    void submit_retailCard_startsRetailWorkflow() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_002", null, null));

        String id = service.submit(baseCmd("RETAIL_CARD"));

        assertThat(id).isNotBlank();
        ArgumentCaptor<StartProcessCmd> cmdCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().getProcessDefinitionKey())
                .isEqualTo("perf_alloc_adjust_retail_v1");
    }

    @Test
    @DisplayName("insert apply + batchInsert items 均被调用，status=IN_APPROVAL")
    void submit_persistsApplyAndItems() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_003", null, null));

        service.submit(baseCmd("CORP_LOAN"));

        ArgumentCaptor<PerfAllocAdjustApply> applyCap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(applyCap.capture());
        PerfAllocAdjustApply apply = applyCap.getValue();
        assertThat(apply.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(apply.getApplyNo()).isNotBlank();
        // apply.cust_id 直接存用户输入的客户编号
        assertThat(apply.getCustId()).isEqualTo("CN-001");
        assertThat(apply.getAllocDim()).isEqualTo("RULE");
        assertThat(apply.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(apply.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(apply.getCreatedBy()).isEqualTo("admin");
        assertThat(apply.getBusinessKey()).contains(apply.getId());

        verify(itemMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("submit 落库贷款余额快照 loanCurrBal/loanMAvgBal/loanQAvgBal/loanYAvgBal")
    void submit_persistsLoanBalanceSnapshot() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_LOAN", null, null));

        SubmitAllocAdjustCmd cmd = SubmitAllocAdjustCmd.builder()
                .custId("CN-001").allocDim("RULE").bizKind("CORP_LOAN")
                .ownerOrgId("ORG_001").applicant("admin").reason("年度岗位调整")
                .items(Arrays.asList(
                        SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("60.00")).build(),
                        SubmitAllocAdjustCmd.Item.builder().empId("EMP_B").ratio(new BigDecimal("40.00")).build()))
                .loanCurrBal(new BigDecimal("100.00"))
                .loanMAvgBal(new BigDecimal("200.00"))
                .loanQAvgBal(new BigDecimal("300.00"))
                .loanYAvgBal(new BigDecimal("400.00"))
                .build();

        service.submit(cmd);

        ArgumentCaptor<PerfAllocAdjustApply> cap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(cap.capture());
        PerfAllocAdjustApply apply = cap.getValue();
        assertThat(apply.getLoanCurrBal()).isEqualByComparingTo("100.00");
        assertThat(apply.getLoanMAvgBal()).isEqualByComparingTo("200.00");
        assertThat(apply.getLoanQAvgBal()).isEqualByComparingTo("300.00");
        assertThat(apply.getLoanYAvgBal()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("同客户同维度已有审批中申请 → 抛 ALLOC_ADJUST_APPLY_RUNNING，不发起流程/不落库，消息不重复")
    void submit_inApprovalExistsForSameCustomerAndDim_throws() {
        // 同一客户编号(CN-001) + 同维度(RULE)已存在 IN_APPROVAL 状态的调整申请（去重按 cust_no）
        when(applyMapper.countInApprovalByCustAndDim("CN-001", "RULE")).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> {
                    assertThat(((PerfException) e).getErrorCode())
                            .isEqualTo(PerfErrorCode.ALLOC_ADJUST_APPLY_RUNNING);
                    // 消息不再嵌套重复：基础消息 + ": CN-001"，只出现一次
                    assertThat(e.getMessage())
                            .isEqualTo("该客户已有审批中的分配调整申请，不可重复提交: CN-001");
                });

        verify(workflowApi, never()).startProcess(any());
        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
    }

    @Test
    @DisplayName("同客户但不同分配维度（已有 RULE 审批中，提交 ACCOUNT）→ 放行，正常落库发起流程")
    void submit_inApprovalDifferentDim_allowed() {
        // RULE 维度审批中（count=1），但本次提交 ACCOUNT 维度（count=0）→ 不应被去重拦截
        when(applyMapper.countInApprovalByCustAndDim("CUST_001", "RULE")).thenReturn(1L);
        when(applyMapper.countInApprovalByCustAndDim("CUST_001", "ACCOUNT")).thenReturn(0L);
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_ACCT", null, null));

        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setAllocDim("ACCOUNT");
        cmd.setAccountNo("62200000001");

        String applyId = service.submit(cmd);

        assertThat(applyId).isNotBlank();
        verify(applyMapper).insert(any(PerfAllocAdjustApply.class));
        verify(workflowApi).startProcess(any());
    }

    @Test
    @DisplayName("按账号分配(ACCOUNT)但未填账号 → 抛 VALIDATION_FAILED，不落库")
    void submit_accountDimWithoutAccountNo_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_DEPOSIT");
        cmd.setAllocDim("ACCOUNT");
        cmd.setAccountNo(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
    }

    @Test
    @DisplayName("NEW（新开户）维度无原业绩分配也可提交，流程变量使用空安全值")
    void submit_newDimension_withoutOriginal_startsWithSafeEmptyOwnerVariables() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_NEW", null, null));
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setAllocDim("NEW");
        cmd.setOriginalAllocList(Collections.emptyList());
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CN-001", "NEW"))
                .thenReturn(Collections.emptyList());

        service.submit(cmd);

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        Map<String, Object> vars = cap.getValue().getVariables();
        assertThat(vars.get("originalOwnerEmpId")).isNull();
        assertThat(vars.get("originalOwnerEmpIds")).isEqualTo(Collections.emptyList());
        assertThat(vars.get("originalOwnerOrgLeaderEmpIds")).isEqualTo(Collections.emptyList());
    }

    @Test
    @DisplayName("submit → 写 originalOwnerEmpIds 会签名单：原业绩分配员工归一到工号、去重保序")
    void submit_setsOriginalOwnerEmpIdsNormalizedToUserId() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_MI", null, null));
        // 原业绩分配（preview）：rm_zhang(登录名) + E30001(工号)
        var o1 = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        o1.setEmpId("rm_zhang");
        var o2 = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        o2.setEmpId("E30001");
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CN-001", "RULE"))
                .thenReturn(Arrays.asList(o1, o2));
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(userDto("E30001", "corp_zhao", "赵公司部审核")));
        when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of(userDto("E10001", "rm_zhang", "张客户经理")));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        service.submit(baseCmd("CORP_LOAN"));
        verify(workflowApi).startProcess(cap.capture());

        Object ids = cap.getValue().getVariables().get("originalOwnerEmpIds");
        assertThat(ids).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> idList = (List<String>) ids;
        // rm_zhang → 登录名兜底归一为工号 E10001；E30001 工号直命中；保序去重
        assertThat(idList).containsExactly("E10001", "E30001");
    }

    @Test
    @DisplayName("items 为空 → 抛 VALIDATION_FAILED")
    void submit_emptyItems_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Collections.emptyList());

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("items 存在同员工重复 → 抛 VALIDATION_FAILED")
    void submit_duplicateEmpId_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Arrays.asList(
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("60")).build(),
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("40")).build()));

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("RULE 维度 ratio 之和 > 100 → 抛 VALIDATION_FAILED")
    void submit_ratioSumExceeds_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Arrays.asList(
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("70")).build(),
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_B").ratio(new BigDecimal("50")).build()));

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("必填字段缺失（custId 为空）→ 抛 VALIDATION_FAILED")
    void submit_blankCustId_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setCustId(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("allocDim 非法值 → 抛 VALIDATION_FAILED")
    void submit_invalidAllocDim_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setAllocDim("INVALID_DIM");

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("未识别 biz_kind 前缀 → 抛 VALIDATION_FAILED (BIZ_KIND_INVALID)")
    void submit_unknownBizKindPrefix_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("UNKNOWN_KIND");

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class);
    }

    @Test
    @DisplayName("1级发起机构校验失败时，不能先写入申请主表/明细")
    void submit_levelOneOrgRejectedBeforePersistence() {
        OrgDTO levelOne = new OrgDTO();
        levelOne.setOrgCode("ORG_L1");
        levelOne.setOrgLevel(1);
        when(orgApi.getOrg("ORG_001")).thenReturn(levelOne);

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("仅限2级、3级机构");

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(itemMapper, never()).batchInsert(anyList());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("非法业务路由校验失败时，不能先写入申请主表/明细")
    void submit_invalidRouteRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.submit(baseCmd("UNKNOWN_KIND")))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.BIZ_KIND_INVALID);

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(itemMapper, never()).batchInsert(anyList());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("设计器流程定义解析失败时，不能先写入申请主表/明细")
    void submit_designerProcessDefinitionResolutionFailsBeforePersistence() {
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer"))
                .thenThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED, "流程未发布"));

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("流程未发布");

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(itemMapper, never()).batchInsert(anyList());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("原分配人无主机构时，不能先写入申请主表/明细")
    void submit_originalOwnerWithoutMainOrgRejectedBeforePersistence() {
        when(orgApi.getUserMainOrg(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("无机构归属");

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(itemMapper, never()).batchInsert(anyList());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("原分配所属机构未配置 BRANCH_HEAD 时，不能先写入申请主表/明细")
    void submit_originalOwnerWithoutBranchHeadRejectedBeforePersistence() {
        when(userApi.getEmpIdsByRoleCodeAndOrg(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("BRANCH_HEAD");

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(itemMapper, never()).batchInsert(anyList());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("Controller DTO 入口必须建立真实事务边界，且回显不依赖写事务内二次查询")
    void dtoWriteEntrypoints_areTransactional() throws Exception {
        assertThat(AllocAdjustService.class
                .getMethod("submitDto", SubmitAllocAdjustCmd.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(AllocAdjustService.class
                .getMethod("saveDraftDto", SubmitAllocAdjustCmd.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(AllocAdjustService.class
                .getMethod("submitDraftDto", String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("Controller submitDto 经事务代理调用时，工作流异常触发整体回滚")
    void submitDto_proxyRollsBackWhenWorkflowStartFails() {
        PlatformTransactionManager txManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = org.mockito.Mockito.mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);
        doThrow(new IllegalStateException("workflow unavailable"))
                .when(workflowApi).startProcess(any(StartProcessCmd.class));

        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                txManager, new AnnotationTransactionAttributeSource()));
        AllocAdjustService proxiedService = (AllocAdjustService) proxyFactory.getProxy();

        assertThatThrownBy(() -> proxiedService.submitDto(baseCmd("CORP_LOAN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("workflow unavailable");

        verify(txManager).rollback(txStatus);
        verify(txManager, never()).commit(txStatus);
    }

    @Test
    @DisplayName("草稿提交的机构级别校验先于状态更新和流程启动")
    void submitDraft_levelOneOrgRejectedBeforeStatusUpdate() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("D_LEVEL1");
        apply.setApplyNo("AA-D-LEVEL1");
        apply.setStatus("DRAFT");
        apply.setCustId("CN-001");
        apply.setCustType("CORP");
        apply.setAllocDim("RULE");
        apply.setBizKind("CORP_LOAN");
        apply.setOwnerOrgId("ORG_001");
        apply.setCreatedBy("admin");
        when(applyMapper.selectByAllocApplyId("D_LEVEL1")).thenReturn(apply);
        PerfAllocAdjustItem item = new PerfAllocAdjustItem();
        item.setItemKind("NEW");
        item.setEmpId("EMP_A");
        item.setRatio(new BigDecimal("100"));
        when(itemMapper.selectByApplyId("D_LEVEL1")).thenReturn(List.of(item));
        OrgDTO levelOne = new OrgDTO();
        levelOne.setOrgCode("ORG_L1");
        levelOne.setOrgLevel(1);
        when(orgApi.getOrg("ORG_001")).thenReturn(levelOne);

        assertThatThrownBy(() -> service.submitDraft("D_LEVEL1", "admin"))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("仅限2级、3级机构");

        verify(applyMapper, never()).updateStatus(anyString(), anyString(), any());
        verify(workflowApi, never()).startProcess(any(StartProcessCmd.class));
    }

    // ========== 响应 DTO custId/custName 直接读 apply 快照 ==========

    @Test
    @DisplayName("getByIdDto → custId/客户名称直接读 apply 快照，不反查客户主档")
    void getByIdDto_readsCustFromApplySnapshot() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_X");
        apply.setApplyNo("AA-X");
        apply.setCustId("CN-X");
        apply.setCustName("客户X");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("APPLY_X")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_X")).thenReturn(Collections.emptyList());

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_X");

        assertThat(dto.getCustId()).isEqualTo("CN-X");
        assertThat(dto.getCustName()).isEqualTo("客户X");
    }

    @Test
    @DisplayName("getByIdDto → 审批中仅返回当前活动节点可审批员工姓名和工号")
    void getByIdDto_inApprovalReturnsCurrentNodeApprovers() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_ACTIVE");
        apply.setStatus("IN_APPROVAL");
        apply.setProcessInstanceId("PI_ACTIVE");
        when(applyMapper.selectByAllocApplyId("APPLY_ACTIVE")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_ACTIVE")).thenReturn(Collections.emptyList());
        when(workflowQueryApi.getActiveTaskCandidates("PI_ACTIVE")).thenReturn(List.of(
                new TaskCandidateUserDTO("E001", "10001", "张三"),
                new TaskCandidateUserDTO("E002", "10002", "李四")));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_ACTIVE");

        assertThat(dto.getCurrentNodeApprovers()).extracting("employeeName", "employeeNo")
                .containsExactly(tuple("张三", "10001"), tuple("李四", "10002"));
    }

    @Test
    @DisplayName("getByIdDto → 节点已审核的终态单据不返回可审批员工")
    void getByIdDto_approvedDoesNotReturnCurrentNodeApprovers() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_DONE");
        apply.setStatus("APPROVED");
        apply.setProcessInstanceId("PI_DONE");
        when(applyMapper.selectByAllocApplyId("APPLY_DONE")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_DONE")).thenReturn(Collections.emptyList());

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_DONE");

        assertThat(dto.getCurrentNodeApprovers()).isEmpty();
        verify(workflowQueryApi, never()).getActiveTaskCandidates(anyString());
    }

    @Test
    @DisplayName("submit → 明细写入员工 username/中文名/部门快照字段（解析不到回退工号）")
    void submit_persistsItemEmployeeSnapshot() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_SNAP", null, null));
        UserDTO ua = userDto("EMP_A", "u_a", "员工甲");
        ua.setMainOrgCode("D01");
        ua.setMainOrgName("一部");
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(ua));   // 仅 EMP_A 命中，EMP_B 未解析

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfAllocAdjustItem>> cap = ArgumentCaptor.forClass(List.class);
        service.submit(baseCmd("CORP_LOAN"));
        verify(itemMapper).batchInsert(cap.capture());

        List<PerfAllocAdjustItem> items = cap.getValue();
        assertThat(items).hasSize(3);
        assertThat(items).filteredOn(i -> "ORIGIN".equals(i.getItemKind()))
                .extracting(PerfAllocAdjustItem::getEmpId)
                .containsExactly("rm_zhang");
        PerfAllocAdjustItem a = items.stream()
                .filter(i -> "NEW".equals(i.getItemKind()) && "EMP_A".equals(i.getEmpId()))
                .findFirst().orElseThrow();
        assertThat(a.getUsername()).isEqualTo("u_a");
        assertThat(a.getEmpChnName()).isEqualTo("员工甲");
        assertThat(a.getOrgCode()).isEqualTo("D01");
        assertThat(a.getOrgName()).isEqualTo("一部");
        PerfAllocAdjustItem b = items.stream()
                .filter(i -> "NEW".equals(i.getItemKind()) && "EMP_B".equals(i.getEmpId()))
                .findFirst().orElseThrow();
        assertThat(b.getUsername()).isEqualTo("EMP_B");   // 解析不到 → 回退工号
        assertThat(b.getEmpChnName()).isNull();
    }

    @Test
    @DisplayName("getByIdDto → 明细直接读快照的 username/中文名/部门，不再关联 PT_USER")
    void getByIdDto_readsItemSnapshotFields() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_Z");
        apply.setApplyNo("AA-Z");
        apply.setCustId("CUST_Z");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("APPLY_Z")).thenReturn(apply);
        when(customerQueryApi.getCustomer("CUST_Z")).thenReturn(Optional.empty());

        PerfAllocAdjustItem it1 = new PerfAllocAdjustItem();
        it1.setEmpId("E30001");
        it1.setUsername("corp_zhao");
        it1.setEmpChnName("赵公司部审核");
        it1.setOrgCode("BJ_HQ");
        it1.setOrgName("北京分行总部");
        it1.setRatio(new BigDecimal("60.00"));
        PerfAllocAdjustItem it2 = new PerfAllocAdjustItem();   // 快照 username 为空 → 回退工号
        it2.setEmpId("E10002");
        it2.setRatio(new BigDecimal("40.00"));
        when(itemMapper.selectByApplyId("APPLY_Z")).thenReturn(Arrays.asList(it1, it2));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_Z");

        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems().get(0).getUsername()).isEqualTo("corp_zhao");
        assertThat(dto.getItems().get(0).getEmpChnName()).isEqualTo("赵公司部审核");
        assertThat(dto.getItems().get(0).getOrgCode()).isEqualTo("BJ_HQ");
        assertThat(dto.getItems().get(0).getOrgName()).isEqualTo("北京分行总部");
        // 快照为空 → username 回退工号
        assertThat(dto.getItems().get(1).getUsername()).isEqualTo("E10002");
        assertThat(dto.getItems().get(1).getEmpChnName()).isNull();
    }

    @Test
    @DisplayName("getByIdDto 优先返回已保存 ORIGIN 快照，不被当前关系覆盖")
    void getByIdDto_prefersPersistedOriginSnapshot() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_ORIGIN");
        apply.setCustId("CUST_ORIGIN");
        apply.setAllocDim("RULE");
        apply.setStatus("DRAFT");
        when(applyMapper.selectByAllocApplyId("APPLY_ORIGIN")).thenReturn(apply);

        PerfAllocAdjustItem newer = new PerfAllocAdjustItem();
        newer.setItemKind("NEW");
        newer.setEmpId("EMP_NEW");
        newer.setRatio(new BigDecimal("100"));
        PerfAllocAdjustItem origin = new PerfAllocAdjustItem();
        origin.setItemKind("ORIGIN");
        origin.setEmpId("EMP_SAVED_ORIGIN");
        origin.setEmpChnName("保存的原分配人");
        origin.setRatio(new BigDecimal("100"));
        when(itemMapper.selectByApplyId("APPLY_ORIGIN")).thenReturn(Arrays.asList(newer, origin));

        com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO current =
                new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        current.setEmpId("EMP_CURRENT");
        current.setRatio(new BigDecimal("100"));
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CUST_ORIGIN", "RULE"))
                .thenReturn(List.of(current));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_ORIGIN");

        assertThat(dto.getItems()).extracting(AllocAdjustRespDTO.Item::getItemKind,
                        AllocAdjustRespDTO.Item::getEmpId)
                .containsExactly(tuple("NEW", "EMP_NEW"), tuple("ORIGIN", "EMP_SAVED_ORIGIN"));
    }

    @Test
    @DisplayName("getByIdDto 老数据无 item_kind/ORIGIN 快照时回退当前关系")
    void getByIdDto_legacyWithoutOriginSnapshot_fallsBackToCurrentRelation() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_LEGACY");
        apply.setCustId("CUST_LEGACY");
        apply.setAllocDim("RULE");
        apply.setStatus("APPROVED");
        when(applyMapper.selectByAllocApplyId("APPLY_LEGACY")).thenReturn(apply);

        PerfAllocAdjustItem legacyNew = new PerfAllocAdjustItem();
        legacyNew.setEmpId("EMP_NEW");
        legacyNew.setRatio(new BigDecimal("100"));
        when(itemMapper.selectByApplyId("APPLY_LEGACY")).thenReturn(List.of(legacyNew));

        com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO current =
                new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        current.setEmpId("EMP_LEGACY_ORIGIN");
        current.setEmpChnName("历史原分配人");
        current.setRatio(new BigDecimal("100"));
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CUST_LEGACY", "RULE"))
                .thenReturn(List.of(current));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_LEGACY");

        assertThat(dto.getItems()).extracting(AllocAdjustRespDTO.Item::getItemKind,
                        AllocAdjustRespDTO.Item::getEmpId)
                .containsExactly(tuple("NEW", "EMP_NEW"), tuple("ORIGIN", "EMP_LEGACY_ORIGIN"));
    }

    @Test
    @DisplayName("pageDto → 批量反查 cust_master，按内部主键 → custNo 映射回填到每行")
    void pageDto_populatesCustNoFromCustomerBatchLookup() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_PAGE");
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(PerfScopeHelper.ScopeColumns.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new PerfScopeHelper.Fragment("1=1", java.util.Map.of()));

        PerfAllocAdjustApply r1 = new PerfAllocAdjustApply();
        r1.setId("A1"); r1.setCustId("C1");
        PerfAllocAdjustApply r2 = new PerfAllocAdjustApply();
        r2.setId("A2"); r2.setCustId("C2");
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Arrays.asList(r1, r2));
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(2L);

        CustomerDTO c1 = new CustomerDTO(); c1.setId("C1"); c1.setCustNo("CN-1");
        CustomerDTO c2 = new CustomerDTO(); c2.setId("C2"); c2.setCustNo("CN-2");
        when(customerQueryApi.listCustomers(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(c1, c2));

        PageResult<AllocAdjustRespDTO> page = service.pageDto(null, null, null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getCustId()).isEqualTo("C1");
        assertThat(page.getRecords().get(1).getCustId()).isEqualTo("C2");
    }

    // ========== V1.4 S1.3: WORKFLOW_PARTICIPANT scope 路径 ==========

    @Test
    @DisplayName("V1.4 S1.3: pageDto → 调 PerfScopeHelper 5 参 overload，bizKeyCol=\"business_key\"，prefix=\"perf_alloc_adjust_\"")
    void pageDto_callsPerfScopeHelperWithBusinessKeyColAndAllocPrefix() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_WF");
        // Mock scope helper 返回 business_key IN (...) 片段
        PerfScopeHelper.Fragment frag = new PerfScopeHelper.Fragment(
                "business_key IN (#{scopeParams.bizKey0})",
                java.util.Map.of("bizKey0", "ALLOC_ADJUST:APPLY_001"));
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_alloc_adjust_")))
                .thenReturn(frag);
        // Mock mapper 返回空列表（不关心结果）
        when(applyMapper.selectByConditionsWithScope(
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        service.pageDto(null, null, null, null, null, 1, 20);

        // 验证 perfScopeHelper 被调用时 bizKeyCol="business_key" + prefix="perf_alloc_adjust_"
        verify(perfScopeHelper).getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_alloc_adjust_"));
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
                org.mockito.ArgumentMatchers.eq("1=0"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        PageResult<AllocAdjustRespDTO> result = service.pageDto(null, null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
    }

    // ==================== suggestEmployees（员工号自动补齐） ====================

    private static UserDTO userDto(String empId, String username, String chnName) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setUsername(username);
        u.setDisplayName(chnName);
        return u;
    }

    @Test
    @DisplayName("suggestEmployees: 委托 userApi.pageUsers，映射 empId/username/中文名")
    void suggestEmployees_mapsUsernameAndChnName() {
        UserDTO first = userDto("E10001", "rm_zhang", "张客户经理");
        first.setMainOrgCode("ORG-001");
        first.setMainOrgName("第一机构");
        UserDTO second = userDto("E30001", "corp_zhao", "赵公司部审核");
        second.setMainOrgCode("ORG-002");
        second.setMainOrgName("第二机构");
        when(userApi.pageUsers("zh", 1, 20)).thenReturn(PageResult.of(1, 20, 2, Arrays.asList(
                first, second)));

        List<com.bank.branch.platform.performance.controller.dto.EmpSuggestRespDTO> result =
                service.suggestEmployees("zh", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmpId()).isEqualTo("E10001");
        assertThat(result.get(0).getUsername()).isEqualTo("rm_zhang");
        assertThat(result.get(0).getEmpChnName()).isEqualTo("张客户经理");
        assertThat(result.get(0).getMainOrgCode()).isEqualTo("ORG-001");
        assertThat(result.get(0).getMainOrgName()).isEqualTo("第一机构");
        assertThat(result.get(1).getUsername()).isEqualTo("corp_zhao");
        assertThat(result.get(1).getMainOrgCode()).isEqualTo("ORG-002");
        assertThat(result.get(1).getMainOrgName()).isEqualTo("第二机构");
    }

    @Test
    @DisplayName("suggestEmployees: 关键字为空/空白 → 直接返回空，不查库")
    void suggestEmployees_blankKeyword_returnsEmpty() {
        assertThat(service.suggestEmployees(null, null)).isEmpty();
        assertThat(service.suggestEmployees("   ", 10)).isEmpty();
        verify(userApi, never()).pageUsers(anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("suggestEmployees: limit 归一（默认 20、超 50 截断、<1 取默认）")
    void suggestEmployees_normalizesLimit() {
        when(userApi.pageUsers(eq("a"), eq(1), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(PageResult.of(1, 20, 0, Collections.emptyList()));

        service.suggestEmployees("a", 999);
        verify(userApi).pageUsers("a", 1, 50);

        service.suggestEmployees("a", 0);
        verify(userApi).pageUsers("a", 1, 20);
    }

    // ===================== withdrawByApplicant（渠道撤回 + 越权校验）=====================

    @Test
    @DisplayName("withdrawByApplicant: 操作人即申请人 → 委托 withdraw 置 WITHDRAWN + 取消流程")
    void withdrawByApplicant_operatorIsApplicant_withdraws() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("AA1");
        apply.setStatus("IN_APPROVAL");
        apply.setCreatedBy("E001");
        apply.setProcessInstanceId("PID1");
        when(applyMapper.selectByAllocApplyId("AA1")).thenReturn(apply);

        service.withdrawByApplicant("AA1", "手机端撤回", "E001");

        verify(applyMapper).updateStatus("AA1", "WITHDRAWN", null);
        verify(workflowApi).cancelProcess("PID1", "手机端撤回");
    }

    @Test
    @DisplayName("withdrawByApplicant: 操作人非申请人 → 越权拒绝，不改状态")
    void withdrawByApplicant_operatorNotApplicant_rejected() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("AA2");
        apply.setStatus("IN_APPROVAL");
        apply.setCreatedBy("E001");
        when(applyMapper.selectByAllocApplyId("AA2")).thenReturn(apply);

        assertThatThrownBy(() -> service.withdrawByApplicant("AA2", "x", "E999"))
                .isInstanceOf(PerfException.class);

        verify(applyMapper, never()).updateStatus(anyString(), anyString(), any());
        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
    }

    @Test
    @DisplayName("withdrawByApplicant: 申请不存在 → 校验失败")
    void withdrawByApplicant_notFound_throws() {
        when(applyMapper.selectByAllocApplyId("NOPE")).thenReturn(null);

        assertThatThrownBy(() -> service.withdrawByApplicant("NOPE", "x", "E001"))
                .isInstanceOf(PerfException.class);

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
    }

    // ==================== 草稿（保存为草稿 / 编辑 / 草稿提交审批）====================

    @Test
    @DisplayName("saveDraft 新建：落库 status=DRAFT，不发起流程，不回写状态")
    void saveDraft_new_persistsDraftWithoutWorkflow() {
        String id = service.saveDraft(baseCmd("CORP_LOAN"), null);

        assertThat(id).isNotBlank();
        ArgumentCaptor<PerfAllocAdjustApply> cap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(cap.getValue().getApplyNo()).isNotBlank();
        verify(itemMapper).batchInsert(anyList());
        // 草稿不进审批流程
        verify(workflowApi, never()).startProcess(any());
        verify(applyMapper, never()).updateStatus(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("saveDraft 宽松校验：比例合计>100 / 无原业绩分配 也能存草稿（不抛、不起流程）")
    void saveDraft_lenient_allowsIncompleteData() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Arrays.asList(
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("70")).build(),
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_B").ratio(new BigDecimal("50")).build()));
        // 原业绩分配历史查不到也不阻塞草稿
        when(allocAdjustPreviewService.getLastApprovedAllocPreview(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        String id = service.saveDraft(cmd, null);

        assertThat(id).isNotBlank();
        verify(applyMapper).insert(any(PerfAllocAdjustApply.class));
        verify(workflowApi, never()).startProcess(any());
    }

    @Test
    @DisplayName("saveDraft 缺客户编号 → 抛 VALIDATION_FAILED，不落库")
    void saveDraft_blankCustId_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setCustId(null);

        assertThatThrownBy(() -> service.saveDraft(cmd, null))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
    }

    @Test
    @DisplayName("saveDraft 编辑既有草稿：更新主表 + 重建明细（delete+insert），保持 DRAFT，不起流程")
    void saveDraft_updateExistingDraft_updatesAndReplacesItems() {
        PerfAllocAdjustApply existing = new PerfAllocAdjustApply();
        existing.setId("D1");
        existing.setApplyNo("AA-D1");
        existing.setStatus("DRAFT");
        existing.setCreatedBy("admin");
        when(applyMapper.selectByAllocApplyId("D1")).thenReturn(existing);

        String id = service.saveDraft(baseCmd("CORP_LOAN"), "D1");

        assertThat(id).isEqualTo("D1");
        verify(applyMapper).updateDraft(any(PerfAllocAdjustApply.class));
        verify(itemMapper).deleteByApplyId("D1");
        verify(itemMapper).batchInsert(anyList());
        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
        verify(workflowApi, never()).startProcess(any());
    }

    @Test
    @DisplayName("saveDraft 同时持久化 NEW/ORIGIN 明细，不写入客户当前分配关系")
    void saveDraft_persistsNewAndOriginItems_withoutSeedingCurrentRelation() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setOriginalAllocList(List.of(SubmitAllocAdjustCmd.OriginalItem.builder()
                .empId("EMP_ORIGIN").empChnName("原分配人").orgCode("ORG_O")
                .orgName("原机构").ratio(new BigDecimal("100")).build()));
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CN-001", "RULE"))
                .thenReturn(Collections.emptyList());

        service.saveDraft(cmd, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfAllocAdjustItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(cap.capture());
        assertThat(cap.getValue()).extracting(PerfAllocAdjustItem::getItemKind,
                        PerfAllocAdjustItem::getEmpId)
                .containsExactly(tuple("NEW", "EMP_A"), tuple("NEW", "EMP_B"),
                        tuple("ORIGIN", "EMP_ORIGIN"));
        verify(allocRelationMapper, never()).insert(any(com.bank.branch.platform.performance.entity.CustAllocRelation.class));
        verify(allocRelationMapper, never()).selectCurrentOriginalByCust(anyString(), anyString());
    }

    @Test
    @DisplayName("saveDraft 未回传原分配但存在自动预览时，复制当前预览为 ORIGIN 快照")
    void saveDraft_autoPreview_isSnapshottedAsOrigin() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setOriginalAllocList(Collections.emptyList());
        com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO preview =
                new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        preview.setEmpId("EMP_PREVIEW");
        preview.setUsername("preview_user");
        preview.setEmpChnName("预览原分配人");
        preview.setOrgCode("ORG_PREVIEW");
        preview.setOrgName("预览机构");
        preview.setRatio(new BigDecimal("100"));
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CN-001", "RULE"))
                .thenReturn(List.of(preview));

        service.saveDraft(cmd, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfAllocAdjustItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(cap.capture());
        PerfAllocAdjustItem origin = cap.getValue().stream()
                .filter(it -> "ORIGIN".equals(it.getItemKind())).findFirst().orElseThrow();
        assertThat(origin.getEmpId()).isEqualTo("EMP_PREVIEW");
        assertThat(origin.getOrgCode()).isEqualTo("ORG_PREVIEW");
    }

    @Test
    @DisplayName("saveDraft 编辑非草稿（IN_APPROVAL）→ 抛 VALIDATION_FAILED，不更新")
    void saveDraft_updateNonDraft_throws() {
        PerfAllocAdjustApply existing = new PerfAllocAdjustApply();
        existing.setId("X1");
        existing.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("X1")).thenReturn(existing);

        assertThatThrownBy(() -> service.saveDraft(baseCmd("CORP_LOAN"), "X1"))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(applyMapper, never()).updateDraft(any());
        verify(itemMapper, never()).deleteByApplyId(anyString());
    }

    @Test
    @DisplayName("submitDraft：载入 DRAFT → 完整校验通过 → 起流程 → 回写 IN_APPROVAL")
    void submitDraft_validatesAndStartsWorkflow() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("D2");
        apply.setApplyNo("AA-D2");
        apply.setStatus("DRAFT");
        apply.setCustId("CN-001");
        apply.setCustType("CORP");
        apply.setAllocDim("RULE");
        apply.setBizKind("CORP_LOAN");
        apply.setOwnerOrgId("ORG_001");
        apply.setCreatedBy("admin");
        when(applyMapper.selectByAllocApplyId("D2")).thenReturn(apply);
        PerfAllocAdjustItem n1 = new PerfAllocAdjustItem();
        n1.setEmpId("EMP_A"); n1.setRatio(new BigDecimal("60"));
        PerfAllocAdjustItem n2 = new PerfAllocAdjustItem();
        n2.setEmpId("EMP_B"); n2.setRatio(new BigDecimal("40"));
        when(itemMapper.selectByApplyId("D2")).thenReturn(Arrays.asList(n1, n2));
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_D2", null, null));

        String id = service.submitDraft("D2", "admin");

        assertThat(id).isEqualTo("D2");
        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        assertThat(cap.getValue().getProcessDefinitionKey()).isEqualTo("perf_alloc_adjust_corp_v1");
        verify(applyMapper).updateStatus("D2", "IN_APPROVAL", "PI_D2");
        // 不应新建（既有草稿原地提交）
        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
    }

    @Test
    @DisplayName("submitDraft 从已保存 ORIGIN 明细重建原分配会签名单，且不被当前关系覆盖")
    void submitDraft_rebuildsPersistedOriginItems() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("D_NEW");
        apply.setApplyNo("AA-D-NEW");
        apply.setStatus("DRAFT");
        apply.setCustId("CN-001");
        apply.setCustType("CORP");
        apply.setAllocDim("RULE");
        apply.setBizKind("CORP_LOAN");
        apply.setOwnerOrgId("ORG_001");
        apply.setCreatedBy("admin");
        when(applyMapper.selectByAllocApplyId("D_NEW")).thenReturn(apply);
        PerfAllocAdjustItem newer = new PerfAllocAdjustItem();
        newer.setItemKind("NEW");
        newer.setEmpId("EMP_A");
        newer.setRatio(new BigDecimal("100"));
        PerfAllocAdjustItem origin = new PerfAllocAdjustItem();
        origin.setItemKind("ORIGIN");
        origin.setEmpId("EMP_ORIGIN");
        origin.setRatio(new BigDecimal("100"));
        when(itemMapper.selectByApplyId("D_NEW")).thenReturn(Arrays.asList(newer, origin));
        var current = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        current.setEmpId("EMP_CURRENT");
        when(allocAdjustPreviewService.getLastApprovedAllocPreview("CN-001", "RULE"))
                .thenReturn(List.of(current));
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_D_NEW", null, null));

        service.submitDraft("D_NEW", "admin");

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        assertThat(cap.getValue().getVariables().get("originalOwnerEmpIds"))
                .isEqualTo(List.of("EMP_ORIGIN"));
        verify(allocAdjustPreviewService, never())
                .getLastApprovedAllocPreview("CN-001", "RULE");
    }

    @Test
    @DisplayName("submitDraft 非草稿状态 → 抛 VALIDATION_FAILED，不起流程")
    void submitDraft_nonDraft_throws() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("D3");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("D3")).thenReturn(apply);

        assertThatThrownBy(() -> service.submitDraft("D3", "admin"))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(workflowApi, never()).startProcess(any());
    }
}
