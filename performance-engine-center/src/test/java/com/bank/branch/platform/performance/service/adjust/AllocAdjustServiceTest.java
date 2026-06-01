package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private CurrentUserApi currentUserApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    @Mock
    private UserApi userApi;

    @Mock
    private AllocAdjustPreviewService allocAdjustPreviewService;

    @InjectMocks
    private AllocAdjustService service;

    private SubmitAllocAdjustCmd baseCmd(String bizKind) {
        return SubmitAllocAdjustCmd.builder()
                .custNo("CN-001")
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
        // apply.cust_id 列保存的是按 custNo 查找到的内部主键 id
        assertThat(apply.getCustId()).isEqualTo("CUST_001");
        assertThat(apply.getAllocDim()).isEqualTo("RULE");
        assertThat(apply.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(apply.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(apply.getCreatedBy()).isEqualTo("admin");
        assertThat(apply.getBusinessKey()).contains(apply.getId());

        verify(itemMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("cust_master 查不到客户 → 不做提交校验，回退用 custNo 落库并正常发起流程")
    void submit_customerNotFoundInMaster_fallsBackToCustNoAndStarts() {
        // 客户编号在前端填写时已基于 XAN_M98 统计表校验过，提交期不再卡 cust_master 存在性
        when(customerQueryApi.getCustomerByCustNo("CN-001")).thenReturn(Optional.empty());
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_NF", null, null));

        String id = service.submit(baseCmd("CORP_LOAN"));

        assertThat(id).isNotBlank();
        // 主档查不到时 cust_id 回退用 custNo 本身落库
        ArgumentCaptor<PerfAllocAdjustApply> applyCap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(applyCap.capture());
        assertThat(applyCap.getValue().getCustId()).isEqualTo("CN-001");
        // 流程照常发起
        verify(workflowApi).startProcess(any(StartProcessCmd.class));
    }

    @Test
    @DisplayName("同客户同维度已有审批中申请 → 抛 ALLOC_ADJUST_APPLY_RUNNING，不发起流程/不落库，消息不重复")
    void submit_inApprovalExistsForSameCustomerAndDim_throws() {
        // 同一客户编号(CN-001 → 内部主键 CUST_001) + 同维度(RULE)已存在 IN_APPROVAL 状态的调整申请
        when(applyMapper.countInApprovalByCustAndDim("CUST_001", "RULE")).thenReturn(1L);

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
    @DisplayName("必填字段缺失（custNo 为空）→ 抛 VALIDATION_FAILED")
    void submit_blankCustNo_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setCustNo(null);

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

    // ========== 响应 DTO 回填 custNo（基于 cust_master 反查） ==========

    @Test
    @DisplayName("getByIdDto → 用 apply.custId(内部主键) 反查客户，回填 custNo 到响应")
    void getByIdDto_populatesCustNoFromCustomerLookup() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_X");
        apply.setApplyNo("AA-X");
        apply.setCustId("CUST_X");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("APPLY_X")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_X")).thenReturn(Collections.emptyList());
        CustomerDTO cust = new CustomerDTO();
        cust.setId("CUST_X");
        cust.setCustNo("CN-X");
        when(customerQueryApi.getCustomer("CUST_X")).thenReturn(Optional.of(cust));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_X");

        assertThat(dto.getCustId()).isEqualTo("CUST_X");
        assertThat(dto.getCustNo()).isEqualTo("CN-X");
    }

    @Test
    @DisplayName("getByIdDto → cust_master 查不到时 custNo 兜底回显 custId（84f227e0 custNo兜底）")
    void getByIdDto_customerMissing_fallsBackToCustId() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_Y");
        apply.setApplyNo("AA-Y");
        apply.setCustId("CUST_GONE");
        when(applyMapper.selectByAllocApplyId("APPLY_Y")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_Y")).thenReturn(Collections.emptyList());
        when(customerQueryApi.getCustomer("CUST_GONE")).thenReturn(Optional.empty());

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_Y");

        assertThat(dto.getCustId()).isEqualTo("CUST_GONE");
        // 查不到主档时 custNo 兜底回退用 custId 展示，不再留 null
        assertThat(dto.getCustNo()).isEqualTo("CUST_GONE");
    }

    @Test
    @DisplayName("getByIdDto → 明细补全 username+中文名（工号 + 登录名双解析）")
    void getByIdDto_enrichesItemEmployeeNames() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_Z");
        apply.setApplyNo("AA-Z");
        apply.setCustId("CUST_Z");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("APPLY_Z")).thenReturn(apply);
        when(customerQueryApi.getCustomer("CUST_Z")).thenReturn(Optional.empty());

        PerfAllocAdjustItem it1 = new PerfAllocAdjustItem();   // 工号存法
        it1.setEmpId("E30001");
        it1.setRatio(new BigDecimal("60.00"));
        PerfAllocAdjustItem it2 = new PerfAllocAdjustItem();   // 登录名存法
        it2.setEmpId("rm_li");
        it2.setRatio(new BigDecimal("40.00"));
        when(itemMapper.selectByApplyId("APPLY_Z")).thenReturn(Arrays.asList(it1, it2));

        UserDTO u1 = new UserDTO();
        u1.setEmpId("E30001"); u1.setUsername("corp_zhao"); u1.setDisplayName("赵公司部审核");
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(u1));   // 仅工号命中 E30001
        UserDTO u2 = new UserDTO();
        u2.setEmpId("E10002"); u2.setUsername("rm_li"); u2.setDisplayName("李客户经理");
        when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of(u2));   // 登录名兜底 rm_li

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_Z");

        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems().get(0).getUsername()).isEqualTo("corp_zhao");
        assertThat(dto.getItems().get(0).getEmpChnName()).isEqualTo("赵公司部审核");
        assertThat(dto.getItems().get(1).getUsername()).isEqualTo("rm_li");
        assertThat(dto.getItems().get(1).getEmpChnName()).isEqualTo("李客户经理");
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
        assertThat(page.getRecords().get(0).getCustNo()).isEqualTo("CN-1");
        assertThat(page.getRecords().get(1).getCustId()).isEqualTo("C2");
        assertThat(page.getRecords().get(1).getCustNo()).isEqualTo("CN-2");
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
        when(userApi.pageUsers("zh", 1, 20)).thenReturn(PageResult.of(1, 20, 2, Arrays.asList(
                userDto("E10001", "rm_zhang", "张客户经理"),
                userDto("E30001", "corp_zhao", "赵公司部审核"))));

        List<com.bank.branch.platform.performance.controller.dto.EmpSuggestRespDTO> result =
                service.suggestEmployees("zh", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmpId()).isEqualTo("E10001");
        assertThat(result.get(0).getUsername()).isEqualTo("rm_zhang");
        assertThat(result.get(0).getEmpChnName()).isEqualTo("张客户经理");
        assertThat(result.get(1).getUsername()).isEqualTo("corp_zhao");
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
    @DisplayName("withdrawByApplicant: 操作人即申请人 → 委托 withdraw 置 REJECTED + 取消流程")
    void withdrawByApplicant_operatorIsApplicant_withdraws() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("AA1");
        apply.setStatus("IN_APPROVAL");
        apply.setCreatedBy("E001");
        apply.setProcessInstanceId("PID1");
        when(applyMapper.selectByAllocApplyId("AA1")).thenReturn(apply);

        service.withdrawByApplicant("AA1", "手机端撤回", "E001");

        verify(applyMapper).updateStatus("AA1", "REJECTED", null);
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
}
