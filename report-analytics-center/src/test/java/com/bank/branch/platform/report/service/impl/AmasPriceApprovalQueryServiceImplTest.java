package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasPriceApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasPriceApprovalVO;
import com.bank.branch.platform.report.entity.AmasPriceApproval;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.AmasPriceApprovalMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AmasPriceApprovalQueryServiceImpl 单元测试（只读查询，已取消数据范围控制）.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmasPriceApprovalQueryServiceImplTest {

    @Mock
    private AmasPriceApprovalMapper priceApprovalMapper;

    @InjectMocks
    private AmasPriceApprovalQueryServiceImpl service;

    private AmasPriceApproval sample() {
        AmasPriceApproval e = new AmasPriceApproval();
        e.setPriceApprId("PA_001");
        e.setApplyUsername("finance_zhou");
        e.setApplyFullname("周八");
        e.setApplyDeptno("O11");
        e.setCustName("某某公司");
        e.setMoney("1000");
        e.setApprStatus("1");
        return e;
    }

    private Page<AmasPriceApproval> onePage() {
        Page<AmasPriceApproval> page = new Page<>(1, 20);
        page.setRecords(List.of(sample()));
        page.setTotal(1);
        return page;
    }

    @Test
    void pageList_custNameAndApplyFullnameFuzzy_mapsVo() {
        when(priceApprovalMapper.selectPage(any(IPage.class), any())).thenReturn(onePage());

        AmasPriceApprovalQueryReqDTO req = new AmasPriceApprovalQueryReqDTO();
        req.setCustName("某某");
        req.setApplyFullname("周");
        PageResult<AmasPriceApprovalVO> result = service.pageList(req, new PageRequest());

        assertThat(result.getTotal()).isEqualTo(1);
        AmasPriceApprovalVO vo = result.getRecords().get(0);
        assertThat(vo.getCustName()).isEqualTo("某某公司");
        assertThat(vo.getApplyFullname()).isEqualTo("周八");
    }

    @Test
    void pageList_noDataScopeFilter_queriesWithoutOrgOrUserNarrowing() {
        // 数据范围控制已取消：不注入 CurrentUserApi/BizScopeApi，授权用户可见全量数据
        when(priceApprovalMapper.selectPage(any(IPage.class), any())).thenReturn(onePage());

        PageResult<AmasPriceApprovalVO> result = service.pageList(new AmasPriceApprovalQueryReqDTO(), new PageRequest());
        assertThat(result.getRecords()).hasSize(1);
        verify(priceApprovalMapper, times(1)).selectPage(any(IPage.class), any());
    }

    @Test
    void detail_found_returnsVo() {
        when(priceApprovalMapper.selectById("PA_001")).thenReturn(sample());
        AmasPriceApprovalVO vo = service.detail("PA_001");
        assertThat(vo.getPriceApprId()).isEqualTo("PA_001");
        assertThat(vo.getApplyFullname()).isEqualTo("周八");
    }

    @Test
    void detail_notFound_throwsRptException() {
        when(priceApprovalMapper.selectById("NOPE")).thenReturn(null);
        assertThatThrownBy(() -> service.detail("NOPE"))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40011");
    }
}
