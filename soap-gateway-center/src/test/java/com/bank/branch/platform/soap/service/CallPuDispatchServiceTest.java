package com.bank.branch.platform.soap.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.soap.controller.dto.PerfDetailData;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.controller.dto.DictItemData;
import com.bank.branch.platform.soap.controller.dto.OrigAllocData;
import com.bank.branch.platform.soap.controller.dto.PerfListData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CallPuDispatchService 单元测试。
 *
 * <p>该 service 承载 callpu 分发逻辑（原 CallPuController 内），供 HTTP 入口与 SOAP 端点共用。
 * 这里直接断言返回的 {@link CallPuResponse} 对象（不经 MockMvc）。</p>
 *
 * <p><b>身份转换</b>：报文 EmployeeNo / allocater 工号实为 {@code PT_USER.USERNAME}，
 * dispatch 须经 {@link UserApi#mapUsernamesToEmpId} 转成 perf 所需的 {@code USER_ID}
 * 再下传。下列测试统一约定工号 {@code E001 → U001}。</p>
 */
@ExtendWith(MockitoExtension.class)
class CallPuDispatchServiceTest {

    @Mock
    private PerfApprovalQueryApi perfApprovalQueryApi;
    @Mock
    private PerfApprovalCmdApi perfApprovalCmdApi;
    @Mock
    private CustStatQueryApi custStatQueryApi;
    @Mock
    private UserApi userApi;
    @Mock
    private AllocApi allocApi;
    @Mock
    private DictApi dictApi;

    @InjectMocks
    private CallPuDispatchService service;


    @Test
    void unsupportedRuleName_returnsFail() {
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("NOT_A_RULE");

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
    }

    @Test
    void perfList_resolvesEmployeeNoToUserId_andReturnsMappedItems() {
        // 工号 E001 → USER_ID U001；perf 必须收到 U001 而非工号 E001
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        AllocAdjustApprovalItemDTO dto = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_001")
                .applyFullname("张三")
                .custName("某某客户")
                .status("IN_APPROVAL")
                .build();
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U001"), eq(null), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 1L, List.of(dto)));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        assertThat(resp.getRspMsg()).isInstanceOf(PerfListData.class);
        PerfListData data = (PerfListData) resp.getRspMsg();
        assertThat(data.getPerfs()).hasSize(1);
        assertThat(data.getPerfs().get(0).getPerfAdjustNo()).isEqualTo("PA_001");
        assertThat(data.getPerfs().get(0).getApprStatus()).isEqualTo("0");
        // 关键回归：透传的是 USER_ID，工号 E001 不得直达 perf
        verify(perfApprovalQueryApi).listAllocAdjustApprovals("U001", null, 1, 100);
        verify(perfApprovalQueryApi, never()).listAllocAdjustApprovals(eq("E001"), any(), anyInt(), anyInt());
    }

    @Test
    void unknownEmployeeNo_returnsFail_andNoPerfCall() {
        // 工号在 PT_USER 查不到 → 失败信封，且不触达 perf
        when(userApi.mapUsernamesToEmpId(List.of("E404"))).thenReturn(Map.of());

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E404");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        verify(perfApprovalQueryApi, never()).listAllocAdjustApprovals(any(), any(), anyInt(), anyInt());
    }

    // ==================== PERF_LIST queryStatus 透传 ====================

    @Test
    void perfList_passesQueryStatusToPerf() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U001"), eq("PENDING"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 0L, List.of()));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setQueryStatus("PENDING");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        verify(perfApprovalQueryApi).listAllocAdjustApprovals("U001", "PENDING", 1, 100);
    }

    // ==================== PERF_MY_LIST 我的申请列表 ====================

    @Test
    void perfMyList_resolvesEmployeeNo_andMapsWithdrawnTo3() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        AllocAdjustApprovalItemDTO dto = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_900").applyFullname("张三").custName("某某客户")
                .status("WITHDRAWN").category("MINE").build();
        when(perfApprovalQueryApi.listMyAllocAdjustApplications(eq("U001"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 1L, List.of(dto)));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_MY_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        PerfListData data = (PerfListData) resp.getRspMsg();
        assertThat(data.getPerfs()).hasSize(1);
        assertThat(data.getPerfs().get(0).getApprStatus()).isEqualTo("3");
        verify(perfApprovalQueryApi).listMyAllocAdjustApplications("U001", 1, 100);
    }

    // ==================== PERF_APPR 审批（通过/驳回）====================

    @Test
    void perfAppr_pass_resolvesUserId_callsApproveAndReturnsOk() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("A1");
        parm.setApprStatus("1");
        parm.setApprOpinion("同意");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        // 未传 routeTo（老渠道/非经办节点），透传 null，由 perf Facade 兜底默认链路
        verify(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "1", "同意", null);
    }

    @Test
    void perfAppr_pass_forwardsRouteTo_toPerf() {
        // 经办节点「同意」带下一步路由选择：网关原样透传 routeTo 给 perf（由 perf 按节点落 corp/finRouteTo）
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("A1");
        parm.setApprStatus("1");
        parm.setApprOpinion("同意");
        parm.setRouteTo("OWNER");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        verify(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "1", "同意", "OWNER");
    }

    @Test
    void perfAppr_reject_resolvesUserId_callsApproveWithStatus2() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("A1");
        parm.setApprStatus("2");
        parm.setApprOpinion("不同意");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        // 驳回不涉及路由，routeTo 透传 null
        verify(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "2", "不同意", null);
    }

    @Test
    void perfAppr_missingPerfAdjustNo_returnsFailAndNoCall() {
        // 入参校验在身份解析之前，不应触达 UserApi / perf
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setApprStatus("1");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        verify(perfApprovalCmdApi, never()).approveAllocAdjust(any(), any(), any(), any(), any());
        verify(userApi, never()).mapUsernamesToEmpId(any());
    }

    @Test
    void perfAppr_illegalStatus_returnsFailAndNoCall() {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("A1");
        parm.setApprStatus("9");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        verify(perfApprovalCmdApi, never()).approveAllocAdjust(any(), any(), any(), any(), any());
        verify(userApi, never()).mapUsernamesToEmpId(any());
    }

    @Test
    void perfAppr_businessException_returnsFail() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        doThrow(new IllegalStateException("无权审批"))
                .when(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "1", null, null);
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("A1");
        parm.setApprStatus("1");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_APPR");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
    }

    // ==================== PERF_ORIG_ALLOC 客户原分配关系回显 ====================

    /** 构造一条「原业绩分配预览」明细（工号 username 已是快照，无需 USER_ID 反查）。 */
    private static AllocAdjustPreviewItemDTO previewItem(String username, String empChnName, String ratio) {
        AllocAdjustPreviewItemDTO dto = new AllocAdjustPreviewItemDTO();
        dto.setUsername(username);
        dto.setEmpChnName(empChnName);
        dto.setRatio(new BigDecimal(ratio));
        return dto;
    }

    @Test
    void perfOrigAlloc_returnsLastApprovedPreview_byCustIdOnly() {
        // 与 PC/管理端口径一致：只按客户号取「最近一次审批通过」的原分配关系（allocDim=null → 两维度合并）
        when(allocApi.getLastApprovedAllocPreview("C001", null))
                .thenReturn(List.of(previewItem("E002", "李四", "60")));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        // 故意不传 applyType / businessType：新口径只依赖客户号，不应再要求这两项
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        OrigAllocData data = (OrigAllocData) resp.getRspMsg();
        assertThat(data.getAllocaters()).hasSize(1);
        OrigAllocData.OrigAllocItem item = data.getAllocaters().get(0);
        assertThat(item.getUsername()).isEqualTo("E002");
        assertThat(item.getFullname()).isEqualTo("李四");
        assertThat(item.getRatio()).isEqualTo("60");
        assertThat(item.getIsOriginal()).isEqualTo(1);
        // 新口径不再调用「当前分配关系」与 USER_ID 反查
        verify(allocApi, never()).getCurrentAllocations(any(), any());
        verify(userApi, never()).getUserByEmpIds(any());
    }

    @Test
    void perfOrigAlloc_emptyPreview_returnsEmptyList() {
        when(allocApi.getLastApprovedAllocPreview("C001", null)).thenReturn(List.of());
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        assertThat(((OrigAllocData) resp.getRspMsg()).getAllocaters()).isEmpty();
    }

    @Test
    void perfOrigAlloc_blankCustId_returnsFail() {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
    }

    // ==================== PERF_SAVE 原/新分配拆分 ====================

    /** 构造一条分配明细行（含 isOriginal 标记）。 */
    private static CallPuRequest.Allocater allocater(String username, String fullname, String ratio, Integer isOriginal) {
        CallPuRequest.Allocater a = new CallPuRequest.Allocater();
        a.setUsername(username);
        a.setFullname(fullname);
        a.setRatio(ratio);
        a.setIsOriginal(isOriginal);
        return a;
    }

    @Test
    void perfSave_splitsOriginalAndNewAllocaters_byIsOriginal() {
        // 申请人工号解析为 USER_ID（applicant 用短代理键）；分配明细 empId 直接落工号(USERNAME)，与 PC 一致
        when(userApi.mapUsernamesToEmpId(any())).thenReturn(Map.of(
                "E001", "U001",
                "E100", "U100",
                "E900", "U900"));
        when(perfApprovalCmdApi.submitAllocAdjust(any())).thenReturn("AA123");
        // 前端已传字典码，网关只校验合法性（不再做中文→码翻译）
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_DEPOSIT")).thenReturn(true);

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");          // CORP
        parm.setApplyRule("1");          // ACCOUNT
        parm.setIouNo("ACC1");
        parm.setBusinessType("CORP_DEPOSIT");   // 字典码直传
        parm.setAdjustExplain("调整理由");
        parm.setAllocaters(List.of(
                allocater("E900", "原始人", "100", 1),   // 原分配
                allocater("E100", "新人", "70", 2)));     // 新分配
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        ArgumentCaptor<AllocAdjustSubmitCmd> captor = ArgumentCaptor.forClass(AllocAdjustSubmitCmd.class);
        verify(perfApprovalCmdApi).submitAllocAdjust(captor.capture());
        AllocAdjustSubmitCmd cmd = captor.getValue();

        // isOriginal != 1 的行 → items（NEW），empId 为工号(USERNAME)，审批通过后原样写 cust_alloc_relation.emp_id
        assertThat(cmd.getItems()).hasSize(1);
        assertThat(cmd.getItems().get(0).getEmpId()).isEqualTo("E100");
        assertThat(cmd.getItems().get(0).getRatio()).isEqualByComparingTo(new BigDecimal("70"));

        // isOriginal == 1 的行 → originalAllocList（ORIGIN），empId 为工号(USERNAME) + 姓名/工号快照（与 PC 一致）
        assertThat(cmd.getOriginalAllocList()).hasSize(1);
        AllocAdjustSubmitCmd.OriginalItem orig = cmd.getOriginalAllocList().get(0);
        assertThat(orig.getEmpId()).isEqualTo("E900");
        assertThat(orig.getUsername()).isEqualTo("E900");
        assertThat(orig.getEmpChnName()).isEqualTo("原始人");
        assertThat(orig.getRatio()).isEqualByComparingTo(new BigDecimal("100"));

        assertThat(cmd.getApplicant()).isEqualTo("U001");
    }

    @Test
    void perfSave_multipleBusinessTypes_joinsBizKindCodesWithComma() {
        // 选了「存款 + 贷款」两个业务类型字典码 → bizKind 原样逗号拼接落库（不翻译、不丢项）
        when(userApi.mapUsernamesToEmpId(any())).thenReturn(Map.of(
                "E001", "U001",
                "E100", "U100"));
        when(perfApprovalCmdApi.submitAllocAdjust(any())).thenReturn("AA124");
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_DEPOSIT")).thenReturn(true);
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_LOAN")).thenReturn(true);

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");              // CORP
        parm.setApplyRule("2");              // RULE
        parm.setBusinessType("CORP_DEPOSIT,CORP_LOAN");   // 字典码直传
        parm.setAdjustExplain("调整理由");
        parm.setAllocaters(List.of(allocater("E100", "新人", "100", 2)));
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        ArgumentCaptor<AllocAdjustSubmitCmd> captor = ArgumentCaptor.forClass(AllocAdjustSubmitCmd.class);
        verify(perfApprovalCmdApi).submitAllocAdjust(captor.capture());
        assertThat(captor.getValue().getBizKind()).isEqualTo("CORP_DEPOSIT,CORP_LOAN");
    }

    @Test
    void perfSave_unknownBusinessTypeAmongMultiple_returnsFail() {
        // 多项里有一个字典码非法（isValidDictValue=false）→ 整体判非法，拒绝提交
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_DEPOSIT")).thenReturn(true);
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "NOT_A_CODE")).thenReturn(false);

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setApplyRule("2");
        parm.setBusinessType("CORP_DEPOSIT,NOT_A_CODE");
        parm.setAdjustExplain("调整理由");
        parm.setAllocaters(List.of(allocater("E100", "新人", "100", 2)));
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        // 必须因「业务类型不合法」被拒（而非更早的工号解析等其它原因）
        assertThat(String.valueOf(resp.getRspMsg())).contains("业务类型不合法");
        verify(perfApprovalCmdApi, never()).submitAllocAdjust(any());
    }

    @Test
    void perfSave_noNewAllocaters_returnsFailAndNoSubmit() {
        // 全部为原分配行（无新分配）→ 调整明细为空，拒绝提交
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_DEPOSIT")).thenReturn(true);
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setApplyRule("1");
        parm.setIouNo("ACC1");
        parm.setBusinessType("CORP_DEPOSIT");
        parm.setAdjustExplain("调整理由");
        parm.setAllocaters(List.of(allocater("E900", "原始人", "100", 1)));
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        verify(perfApprovalCmdApi, never()).submitAllocAdjust(any());
    }

    // ==================== PERF_INFO 单据详情 ====================

    @Test
    void perfInfo_translatesDetailToFrontendDataForm() {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        AllocAdjustDetailDTO.AllocItem orig = AllocAdjustDetailDTO.AllocItem.builder()
                .username("E1").fullname("张三").ratio("70").isOriginal(1).build();
        AllocAdjustDetailDTO.AllocItem adj = AllocAdjustDetailDTO.AllocItem.builder()
                .username("E2").fullname("李四").ratio("30").isOriginal(2).build();
        AllocAdjustDetailDTO detail = AllocAdjustDetailDTO.builder()
                .perfAdjustNo("PA_1").applyFullname("王五").custId("C001").custName("某客户")
                .custType("CORP").allocDim("ACCOUNT").bizKind("CORP_DEPOSIT").accountNo("ACC9")
                .status("IN_APPROVAL").reason("理由").canApprove(true).canDelete(false)
                .currentNode("资财部经办审批").nextNode("资财部负责人审批")
                .allocaters(List.of(orig, adj)).build();
        when(perfApprovalQueryApi.getAllocAdjustDetail("PA_1", "U001")).thenReturn(detail);

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setPerfAdjustNo("PA_1");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_INFO");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        PerfDetailData d = (PerfDetailData) resp.getRspMsg();
        assertThat(d.getApplyType()).isEqualTo("1");   // CORP→1
        assertThat(d.getApplyRule()).isEqualTo("1");    // ACCOUNT→1
        assertThat(d.getIouNo()).isEqualTo("ACC9");
        assertThat(d.getApprStatus()).isEqualTo("0");   // IN_APPROVAL→0
        assertThat(d.getIsCanAppr()).isEqualTo(1);
        assertThat(d.getIsCanDelete()).isEqualTo(0);
        assertThat(d.getAllocaters()).hasSize(2);
        assertThat(d.getCurrentNode()).isEqualTo("资财部经办审批");
        assertThat(d.getNextNode()).isEqualTo("资财部负责人审批");
        verify(perfApprovalQueryApi).getAllocAdjustDetail("PA_1", "U001");
    }

    // ==================== SYS_DICT_ITEMS 查字典（业务类型选项）====================

    /** 构造一条字典项 DTO（仅 code/label 用于本场景）。 */
    private static DictItemDTO dictItem(String dictCode, String dictLabel) {
        DictItemDTO dto = new DictItemDTO();
        dto.setDictType("PERF_BIZ_KIND");
        dto.setDictCode(dictCode);
        dto.setDictLabel(dictLabel);
        return dto;
    }

    @Test
    void dictItems_returnsActiveItemsMappedToCodeAndLabel() {
        // DictApi.getDictItems 已只返 ACTIVE 且按 sort_order；网关原样映射为 {dictCode,dictLabel}
        when(dictApi.getDictItems("PERF_BIZ_KIND")).thenReturn(List.of(
                dictItem("CORP_DEPOSIT", "存款"),
                dictItem("CORP_LOAN", "贷款"),
                dictItem("FEE_BIZ", "中收")));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setDictType("PERF_BIZ_KIND");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("SYS_DICT_ITEMS");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        assertThat(resp.getRspMsg()).isInstanceOf(DictItemData.class);
        DictItemData data = (DictItemData) resp.getRspMsg();
        assertThat(data.getItems()).hasSize(3);
        assertThat(data.getItems().get(0).getDictCode()).isEqualTo("CORP_DEPOSIT");
        assertThat(data.getItems().get(0).getDictLabel()).isEqualTo("存款");
        assertThat(data.getItems().get(2).getDictCode()).isEqualTo("FEE_BIZ");
        verify(dictApi).getDictItems("PERF_BIZ_KIND");
    }

    @Test
    void dictItems_blankDictType_returnsFailAndNoDictCall() {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();   // dictType 缺省
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("SYS_DICT_ITEMS");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("99");
        verify(dictApi, never()).getDictItems(any());
    }
}
