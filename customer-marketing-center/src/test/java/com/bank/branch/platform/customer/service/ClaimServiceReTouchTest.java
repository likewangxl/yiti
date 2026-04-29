package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClaimService.reTouch 单元测试（P1a TDD：先红后绿）
 *
 * <p>覆盖 4 个核心路径：
 * 成功 / 认领不存在 / 跨机构操作 / 客户当前已有在途任务。</p>
 *
 * <p>CUST-40909（最近未完成）枚举保留作占位，但实际不在 reTouch 触发
 * （selectActiveByCust 已覆盖所有"最近未完成"场景），故无对应测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceReTouchTest {

    @Mock CustClaimMapper claimMapper;
    @Mock CustMasterMapper masterMapper;
    @Mock TouchTaskMapper touchTaskMapper;
    @Mock TouchTaskService touchTaskService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ClaimService claimService;

    private static ReTouchReqDTO buildReq() {
        ReTouchReqDTO req = new ReTouchReqDTO();
        req.setReason("客户提出新需求需要重新触达");
        return req;
    }

    private static CustClaim claimFixture(String orgCode) {
        CustClaim c = new CustClaim();
        c.setId("claim-001");
        c.setCustId("cust-001");
        c.setOrgId(orgCode);
        c.setMaintainerEmpId("E10001");
        c.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        return c;
    }

    @Test
    void reTouch_success_createsFollowUpTask() {
        when(claimMapper.selectById("claim-001")).thenReturn(claimFixture("ORG_SZ_001"));
        when(touchTaskMapper.selectActiveByCust("cust-001")).thenReturn(Collections.emptyList());
        TouchTask created = new TouchTask();
        created.setId("touch-new-001");
        when(touchTaskService.createFollowUpTask(eq("cust-001"), eq("ORG_SZ_001"),
                eq("E10001"), eq("客户提出新需求需要重新触达"), any()))
                .thenReturn(created);

        TouchTask result = claimService.reTouch("claim-001", buildReq(), "E10001", "ORG_SZ_001");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("touch-new-001");
        verify(touchTaskService).createFollowUpTask(eq("cust-001"), eq("ORG_SZ_001"),
                eq("E10001"), eq("客户提出新需求需要重新触达"), any());
    }

    @Test
    void reTouch_claimNotFound_throwsCust40404() {
        when(claimMapper.selectById("not-exist")).thenReturn(null);

        assertThatThrownBy(() -> claimService.reTouch("not-exist", buildReq(), "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CLAIM_NOT_FOUND.getCode());

        verify(touchTaskService, never()).createFollowUpTask(any(), any(), any(), any(), any());
    }

    @Test
    void reTouch_otherOrg_throwsCust40305() {
        // 批次 A 顺手补 CLAIM_ORG_FORBIDDEN（CUST-40305）枚举，因 reTouch 必然需要 403 码
        when(claimMapper.selectById("claim-001")).thenReturn(claimFixture("ORG_OTHER"));

        assertThatThrownBy(() -> claimService.reTouch("claim-001", buildReq(), "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode());

        verify(touchTaskService, never()).createFollowUpTask(any(), any(), any(), any(), any());
    }

    @Test
    void reTouch_runningTouchTask_throwsCust40908() {
        when(claimMapper.selectById("claim-001")).thenReturn(claimFixture("ORG_SZ_001"));
        TouchTask running = new TouchTask();
        running.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        when(touchTaskMapper.selectActiveByCust("cust-001")).thenReturn(List.of(running));

        assertThatThrownBy(() -> claimService.reTouch("claim-001", buildReq(), "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode());

        verify(touchTaskService, never()).createFollowUpTask(any(), any(), any(), any(), any());
    }
}
