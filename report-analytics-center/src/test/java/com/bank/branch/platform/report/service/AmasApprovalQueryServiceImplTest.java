package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalDetailVO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalRowVO;
import com.bank.branch.platform.report.entity.AmasApprRecord;
import com.bank.branch.platform.report.entity.AmasPerfAdjustApproval;
import com.bank.branch.platform.report.entity.AmasPerformanceAllocation;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.AmasApprRecordMapper;
import com.bank.branch.platform.report.mapper.AmasPerfAdjustApprovalMapper;
import com.bank.branch.platform.report.mapper.AmasPerformanceAllocationMapper;
import com.bank.branch.platform.report.service.impl.AmasApprovalQueryServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AmasApprovalQueryServiceImpl} 单元测试：业绩分配审批历史 列表分页 + 详情聚合.
 */
@ExtendWith(MockitoExtension.class)
class AmasApprovalQueryServiceImplTest {

    @Mock
    private AmasPerfAdjustApprovalMapper approvalMapper;
    @Mock
    private AmasPerformanceAllocationMapper allocationMapper;
    @Mock
    private AmasApprRecordMapper apprRecordMapper;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private BizScopeApi bizScopeApi;

    @InjectMocks
    private AmasApprovalQueryServiceImpl service;

    @Test
    @DisplayName("pageList: 透传分页参数，返回 mapper 的 total 与 records")
    void pageList_returnsPagedResult() {
        AmasPerfAdjustApproval row = new AmasPerfAdjustApproval();
        row.setPerfAdjustNo("PA001");
        Page<AmasPerfAdjustApproval> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(5L);
        when(approvalMapper.selectPage(any(IPage.class), any())).thenReturn(mpPage);

        PageRequest page = new PageRequest();
        page.setPageNo(1);
        page.setPageSize(20);
        PageResult<AmasApprovalRowVO> result = service.pageList(new AmasApprovalQueryReqDTO(), page);

        assertThat(result.getTotal()).isEqualTo(5L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getPerfAdjustNo()).isEqualTo("PA001");
    }

    @Test
    @DisplayName("pageList: null 查询条件不抛异常（按全部+倒序）")
    void pageList_nullReq_ok() {
        Page<AmasPerfAdjustApproval> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0L);
        when(approvalMapper.selectPage(any(IPage.class), any())).thenReturn(mpPage);

        PageRequest page = new PageRequest();
        PageResult<AmasApprovalRowVO> result = service.pageList(null, page);

        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("detail: 主记录存在 → 聚合分配明细 + 审批流程返回")
    void detail_found_aggregates() {
        AmasPerfAdjustApproval approval = new AmasPerfAdjustApproval();
        approval.setPerfAdjustNo("PA001");
        when(approvalMapper.selectById("PA001")).thenReturn(approval);

        AmasPerformanceAllocation alloc = new AmasPerformanceAllocation();
        alloc.setPerfAdjustNo("PA001");
        when(allocationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(alloc));

        AmasApprRecord rec = new AmasApprRecord();
        rec.setRecordId("R1");
        rec.setRegionDtId("PA001");
        when(apprRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rec));

        AmasApprovalDetailVO vo = service.detail("PA001");

        assertThat(vo.getApproval().getPerfAdjustNo()).isEqualTo("PA001");
        assertThat(vo.getAllocations()).hasSize(1);
        assertThat(vo.getApprRecords()).hasSize(1);
    }

    @Test
    @DisplayName("detail: 主记录不存在 → 抛 RPT-40011 AMAS_APPROVAL_NOT_FOUND，不查子表")
    void detail_notFound_throws() {
        when(approvalMapper.selectById("X")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("X"))
                .isInstanceOfSatisfying(RptException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RptErrorCode.AMAS_APPROVAL_NOT_FOUND));
        verify(allocationMapper, never()).selectList(any());
        verify(apprRecordMapper, never()).selectList(any());
    }
}
