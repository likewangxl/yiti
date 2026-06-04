package com.bank.branch.platform.soap.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.controller.dto.OrigAllocData;
import com.bank.branch.platform.soap.controller.dto.PerfListData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
 * dispatch 须经 {@link UserApi#getUsersByUsernames} 转成 perf 所需的 {@code USER_ID}（{@code UserDTO.empId}）
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

    @InjectMocks
    private CallPuDispatchService service;

    /** 构造一个 UserDTO（username=工号/PT_USER.USERNAME，empId=USER_ID/PT_USER.USER_ID）。 */
    private static UserDTO user(String employeeNo, String userId) {
        UserDTO dto = new UserDTO();
        dto.setUsername(employeeNo);
        dto.setEmpId(userId);
        return dto;
    }

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
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
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
        when(userApi.getUsersByUsernames(List.of("E404"))).thenReturn(List.of());

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
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
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
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
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
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
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
        verify(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "1", "同意");
    }

    @Test
    void perfAppr_reject_resolvesUserId_callsApproveWithStatus2() {
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
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
        verify(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "2", "不同意");
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
        verify(perfApprovalCmdApi, never()).approveAllocAdjust(any(), any(), any(), any());
        verify(userApi, never()).getUsersByUsernames(any());
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
        verify(perfApprovalCmdApi, never()).approveAllocAdjust(any(), any(), any(), any());
        verify(userApi, never()).getUsersByUsernames(any());
    }

    @Test
    void perfAppr_businessException_returnsFail() {
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
        doThrow(new IllegalStateException("无权审批"))
                .when(perfApprovalCmdApi).approveAllocAdjust("A1", "U001", "1", null);
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

    @Test
    void perfOrigAlloc_returnsCurrentAllocations_withUsernameReverseLookup() {
        CustAllocRelationDTO rel = new CustAllocRelationDTO();
        rel.setEmpId("U002");
        rel.setEmpName("李四");
        rel.setRatio(new java.math.BigDecimal("60"));
        when(allocApi.getCurrentAllocations("C001", "CORP_DEPOSIT")).thenReturn(List.of(rel));
        UserDTO u = new UserDTO();
        u.setEmpId("U002");
        u.setUsername("E002");
        when(userApi.getUserByEmpIds(List.of("U002"))).thenReturn(List.of(u));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setBusinessType("存款");
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
    }

    @Test
    void perfOrigAlloc_emptyAllocations_returnsEmptyList() {
        when(allocApi.getCurrentAllocations("C001", "CORP_DEPOSIT")).thenReturn(List.of());
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setBusinessType("存款");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        assertThat(((OrigAllocData) resp.getRspMsg()).getAllocaters()).isEmpty();
    }
}
