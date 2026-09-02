package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.dto.resp.ClaimedCustomerRespDTO;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.event.ClaimCancelledEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.governance.api.DictApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClaimService 单元测试（TDD RED 阶段）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private CustClaimMapper claimMapper;

    @Mock
    private CustMasterMapper masterMapper;

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @Mock
    private TouchTaskService touchTaskService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OrgApi orgApi;

    @Mock
    private DictApi dictApi;

    @InjectMocks
    private ClaimService claimService;

    // ==================== claim ====================

    @Test
    void claim_shouldInsertAndReturnClaim() {
        // given: 客户存在，认领成功
        CustMaster customer = new CustMaster();
        customer.setId("cust-001");
        customer.setCustName("测试客户A");
        when(masterMapper.selectById("cust-001")).thenReturn(customer);
        when(claimMapper.insert(any(CustClaim.class))).thenReturn(1);

        // when
        CustClaim result = claimService.claim("cust-001", "ORG_SZ_001", "E10001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCustId()).isEqualTo("cust-001");
        assertThat(result.getOrgId()).isEqualTo("ORG_SZ_001");
        assertThat(result.getClaimedBy()).isEqualTo("E10001");
        // 认领时 maintainerEmpId 默认等于 claimedBy
        assertThat(result.getMaintainerEmpId()).isEqualTo("E10001");
        assertThat(result.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED.getCode());
        assertThat(result.getClaimTime()).isNotNull();

        verify(claimMapper).insert(any(CustClaim.class));

        // 最新会议口径：认领只建立个人关系，不自动创建触达任务
        verify(touchTaskService, never()).createFromClaim(any(), any(), any());
    }

    @Test
    void startTouch_shouldCreateFirstTouchForClaimOwner() {
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG_SZ_001");
        claim.setClaimedBy("E10001");
        claim.setMaintainerEmpId("E10001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(claimMapper.selectById("claim-001")).thenReturn(claim);
        when(touchTaskMapper.selectActiveByCustAndAssignee("cust-001", "E10001")).thenReturn(List.of());
        TouchTask created = new TouchTask();
        created.setId(1L);
        when(touchTaskService.createFirstTouchTask("cust-001", "ORG_SZ_001", "E10001", null))
                .thenReturn(created);

        TouchTask result = claimService.startTouch("claim-001", null, "E10001", "ORG_SZ_001");

        assertThat(result.getId()).isEqualTo(1L);
        verify(touchTaskService).createFirstTouchTask("cust-001", "ORG_SZ_001", "E10001", null);
    }

    @Test
    void startTouch_shouldRejectSameOrgNonOwner() {
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG_SZ_001");
        claim.setMaintainerEmpId("E10001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(claimMapper.selectById("claim-001")).thenReturn(claim);

        assertThatThrownBy(() -> claimService.startTouch("claim-001", null, "E10002", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode());

        verify(touchTaskService, never()).createFirstTouchTask(any(), any(), any(), any());
    }

    @Test
    void claim_shouldThrowWhenAlreadyClaimed() {
        // given: 客户存在，但 INSERT 触发唯一键冲突
        CustMaster customer = new CustMaster();
        customer.setId("cust-001");
        when(masterMapper.selectById("cust-001")).thenReturn(customer);
        doThrow(new DuplicateKeyException("uk_cust_claim_emp")).when(claimMapper).insert(any(CustClaim.class));

        // when/then
        assertThatThrownBy(() -> claimService.claim("cust-001", "ORG_SZ_001", "E10001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode());

        // 发布事件不应被调用
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== cancelClaim ====================

    @Test
    void cancelClaim_shouldUpdateStatusAndPublishEvent() {
        // given: 认领记录存在
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG_SZ_001");
        claim.setClaimedBy("E10001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(claimMapper.selectById("claim-001")).thenReturn(claim);
        when(claimMapper.updateById(any(CustClaim.class))).thenReturn(1);

        // when
        claimService.cancelClaim("claim-001", "客户不符合条件", "E10001", "ORG_SZ_001");

        // then: 验证 updateById 被调用，且字段已更新
        ArgumentCaptor<CustClaim> captor = ArgumentCaptor.forClass(CustClaim.class);
        verify(claimMapper).updateById(captor.capture());
        CustClaim updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo("claim-001");
        assertThat(updated.getClaimStatus()).isEqualTo(ClaimStatus.CANCELLED.getCode());
        assertThat(updated.getCancelReason()).isEqualTo("客户不符合条件");
        assertThat(updated.getCancelTime()).isNotNull();

        // 验证发布了 ClaimCancelledEvent
        ArgumentCaptor<ClaimCancelledEvent> eventCaptor = ArgumentCaptor.forClass(ClaimCancelledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ClaimCancelledEvent event = eventCaptor.getValue();
        assertThat(event.getClaimId()).isEqualTo("claim-001");
        assertThat(event.getCustId()).isEqualTo("cust-001");
        assertThat(event.getCancelReason()).isEqualTo("客户不符合条件");
        assertThat(event.getOperatorEmpId()).isEqualTo("E10001");
    }

    @Test
    void cancelClaim_shouldThrowWhenReasonBlank() {
        // given: 取消原因为空

        // when/then: cancelReason 必填，否则抛 CANCEL_REASON_REQUIRED
        assertThatThrownBy(() -> claimService.cancelClaim("claim-001", "", "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CANCEL_REASON_REQUIRED.getCode());

        // 不应查询数据库
        verify(claimMapper, never()).selectById(any());
        verify(claimMapper, never()).updateById(any(CustClaim.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelClaim_shouldThrowCust40305WhenCrossOrg() {
        // P1C：claim 归属机构与 operator 机构不一致 → CUST-40305
        CustClaim claim = new CustClaim();
        claim.setId("claim-002");
        claim.setOrgId("ORG_OTHER");
        claim.setMaintainerEmpId("E10002");
        when(claimMapper.selectById("claim-002")).thenReturn(claim);

        assertThatThrownBy(() -> claimService.cancelClaim("claim-002", "原因说明", "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode());

        verify(claimMapper, never()).updateById(any(CustClaim.class));
    }

    @Test
    void cancelClaim_shouldThrowWhenNotFound() {
        // given: 认领记录不存在
        when(claimMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> claimService.cancelClaim("not-exist", "原因说明", "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CLAIM_NOT_FOUND.getCode());

        verify(claimMapper, never()).updateById(any(CustClaim.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== listMyClaims ====================

    @Test
    void listMyClaims_shouldReturnPagedResult() {
        // given: pageNo=2, pageSize=10 -> offset = (2-1)*10 = 10
        CustClaim claim1 = new CustClaim();
        claim1.setId("claim-011");
        claim1.setClaimedBy("E10001");
        CustClaim claim2 = new CustClaim();
        claim2.setId("claim-012");
        claim2.setClaimedBy("E10001");
        List<CustClaim> mockList = Arrays.asList(claim1, claim2);

        when(claimMapper.selectMyClaimsPage(eq("E10001"), eq(10), eq(10))).thenReturn(mockList);
        when(claimMapper.countMyClaimsPage(eq("E10001"))).thenReturn(12L);

        // when
        PageResult<CustClaim> result = claimService.listMyClaims("E10001", 2, 10);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(12L);
        assertThat(result.getRecords()).hasSize(2);

        // 验证 offset 计算正确：(2-1)*10=10
        verify(claimMapper).selectMyClaimsPage("E10001", 10, 10);
        verify(claimMapper).countMyClaimsPage("E10001");
    }

    @Test
    void listMyClaimedCustomers_shouldReturnCustomerAndLatestTask() {
        ClaimedCustomerRespDTO row = new ClaimedCustomerRespDTO();
        row.setClaimId("claim-001");
        row.setCustName("测试客户A");
        row.setIndustry("IT");
        row.setCustomerType("CORP");
        row.setOwnerOrgId("ORG001");
        row.setLatestTaskStatus("IN_PROGRESS");
        when(claimMapper.selectMyClaimedCustomerPage("E10001", 0, 20)).thenReturn(List.of(row));
        when(claimMapper.countMyClaimsPage("E10001")).thenReturn(1L);
        when(dictApi.getDictLabel("INDUSTRY", "IT")).thenReturn("信息技术业");
        when(dictApi.getDictLabel("CUSTOMER_TYPE", "CORP")).thenReturn("公司客户");
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG001");
        org.setOrgName("西安分行营业部");
        when(orgApi.getOrgsByCodes(List.of("ORG001"))).thenReturn(List.of(org));

        PageResult<ClaimedCustomerRespDTO> result = claimService.listMyClaimedCustomers("E10001", 1, 20);

        assertThat(result.getRecords()).singleElement()
                .extracting(ClaimedCustomerRespDTO::getCustName,
                        ClaimedCustomerRespDTO::getIndustryName,
                        ClaimedCustomerRespDTO::getCustomerTypeName,
                        ClaimedCustomerRespDTO::getOwnerOrgName)
                .containsExactly("测试客户A", "信息技术业", "公司客户", "西安分行营业部");
    }
}
