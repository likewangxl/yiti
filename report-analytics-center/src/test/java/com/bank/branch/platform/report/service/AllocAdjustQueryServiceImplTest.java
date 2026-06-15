package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AllocAdjustApplyQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyDetailVO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyRowVO;
import com.bank.branch.platform.report.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.report.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptAllocAdjustApplyMapper;
import com.bank.branch.platform.report.mapper.RptAllocAdjustItemMapper;
import com.bank.branch.platform.report.service.impl.AllocAdjustQueryServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AllocAdjustQueryServiceImpl} 单元测试：业绩调整 列表分页 + 详情聚合.
 */
@ExtendWith(MockitoExtension.class)
class AllocAdjustQueryServiceImplTest {

    @Mock
    private RptAllocAdjustApplyMapper applyMapper;
    @Mock
    private RptAllocAdjustItemMapper itemMapper;
    @Mock
    private UserApi userApi;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private BizScopeApi bizScopeApi;

    @InjectMocks
    private AllocAdjustQueryServiceImpl service;

    @Test
    @DisplayName("pageList: 返回 total 与 records，createdTime 格式化为字符串")
    void pageList_returnsPagedResultWithFormattedTime() {
        PerfAllocAdjustApply row = new PerfAllocAdjustApply();
        row.setId("AA1");
        row.setCreatedBy("E10001"); // created_by 存的是 USER_ID
        row.setCreatedTime(LocalDateTime.of(2026, 6, 15, 10, 30, 0));
        Page<PerfAllocAdjustApply> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(3L);
        when(applyMapper.selectPage(any(IPage.class), any())).thenReturn(mpPage);
        UserDTO u = new UserDTO();
        u.setEmpId("E10001");        // USER_ID
        u.setUsername("rm_zhang");   // 工号
        u.setDisplayName("张客户经理"); // 姓名
        when(userApi.getUserByEmpIds(List.of("E10001"))).thenReturn(List.of(u));

        PageRequest page = new PageRequest();
        PageResult<AllocAdjustApplyRowVO> r = service.pageList(new AllocAdjustApplyQueryReqDTO(), page);

        assertThat(r.getTotal()).isEqualTo(3L);
        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getRecords().get(0).getId()).isEqualTo("AA1");
        assertThat(r.getRecords().get(0).getCreatedBy()).isEqualTo("E10001");
        assertThat(r.getRecords().get(0).getCreatedByName()).isEqualTo("张客户经理");
        assertThat(r.getRecords().get(0).getCreatedByNo()).isEqualTo("rm_zhang");
        assertThat(r.getRecords().get(0).getCreatedTime()).isEqualTo("2026-06-15 10:30:00");
    }

    @Test
    @DisplayName("detail: 申请存在 → 聚合分配明细返回")
    void detail_found_aggregatesItems() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("AA1");
        when(applyMapper.selectById("AA1")).thenReturn(apply);

        PerfAllocAdjustItem item = new PerfAllocAdjustItem();
        item.setApplyId("AA1");
        item.setUsername("zhangsan");
        when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        AllocAdjustApplyDetailVO vo = service.detail("AA1");

        assertThat(vo.getApply().getId()).isEqualTo("AA1");
        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getUsername()).isEqualTo("zhangsan");
    }

    @Test
    @DisplayName("detail: 申请不存在 → 抛 RPT-40012，不查明细")
    void detail_notFound_throws() {
        when(applyMapper.selectById("X")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("X"))
                .isInstanceOfSatisfying(RptException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RptErrorCode.ALLOC_ADJUST_APPLY_NOT_FOUND));
        verify(itemMapper, never()).selectList(any());
    }
}
