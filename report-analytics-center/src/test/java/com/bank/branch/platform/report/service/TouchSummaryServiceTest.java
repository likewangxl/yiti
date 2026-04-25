package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ReportTouchOrgVO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.impl.TouchSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TouchSummaryService 单元测试（Task M3.1.1，Red）.
 *
 * <p>覆盖 C.2 GET /touch-task-summary 的 3 个分支：
 * <ol>
 *   <li>happy path：上游返回汇总 → VO 装配 + successRate 4 位小数</li>
 *   <li>日期范围 &gt; 366 天 → RPT-40006</li>
 *   <li>上游 TouchTaskQueryApi 抛异常 → 包装 RPT-50001（R4 fail-close）</li>
 * </ol>
 *
 * <p>注：customer.TouchTaskQueryApi.getOrgTouchSummary 实际签名
 * {@code (String orgCode, String startDate, String endDate) → TouchTaskSummaryDTO}
 * （非 plan 文档里的 List&lt;TouchOrgSummaryDTO&gt;），本测试按真实 API 适配.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TouchSummaryServiceTest {

    @Mock
    private TouchTaskQueryApi touchTaskQueryApi;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private OrgApi orgApi;

    @InjectMocks
    private TouchSummaryServiceImpl service;

    @Test
    void getOrgTouchSummary_happyPath_returnsAggregated() {
        // 1) Arrange：上游返回单个 TouchTaskSummaryDTO
        TouchTaskSummaryDTO upstream = new TouchTaskSummaryDTO();
        upstream.setOrgId("BR001");
        upstream.setOrgName("分行 A");
        upstream.setTotalCount(120L);
        upstream.setSuccessCount(80L);
        upstream.setCancelledCount(40L);

        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR001");
        when(touchTaskQueryApi.getOrgTouchSummary(eq("BR001"), anyString(), anyString()))
                .thenReturn(upstream);

        OrgDTO org = new OrgDTO();
        org.setOrgCode("BR001");
        org.setOrgName("分行 A");
        lenient().when(orgApi.getOrg("BR001")).thenReturn(org);

        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2026, 4, 1));
        req.setEndDate(LocalDate.of(2026, 4, 25));
        req.setOrgId("BR001");

        // 2) Act
        PageResult<ReportTouchOrgVO> result = service.getOrgTouchSummary(req, new PageRequest());

        // 3) Assert
        assertThat(result.getTotal()).isEqualTo(1);
        ReportTouchOrgVO vo = result.getRecords().get(0);
        assertThat(vo.getOrgCode()).isEqualTo("BR001");
        assertThat(vo.getTotalTask()).isEqualTo(120);
        assertThat(vo.getSuccessCount()).isEqualTo(80);
        assertThat(vo.getFailedCount()).isEqualTo(40);
        // successRate = 80 / 120 = 0.6667（4 位小数，HALF_UP）
        assertThat(vo.getSuccessRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
    }

    @Test
    void getOrgTouchSummary_dateRangeOver366Days_throwsRpt40006() {
        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2025, 1, 1));
        req.setEndDate(LocalDate.of(2026, 4, 1));  // 455 天
        req.setOrgId("BR001");

        assertThatThrownBy(() -> service.getOrgTouchSummary(req, new PageRequest()))
                .isInstanceOf(RptException.class)
                .satisfies(ex -> assertThat(((RptException) ex).getErrorCode())
                        .isEqualTo(RptErrorCode.METRIC_DIM_MISMATCH));
    }

    @Test
    void getOrgTouchSummary_upstreamFailure_wrapsAsRpt50001() {
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR001");
        when(touchTaskQueryApi.getOrgTouchSummary(any(), any(), any()))
                .thenThrow(new RuntimeException("upstream timeout"));

        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2026, 4, 1));
        req.setEndDate(LocalDate.of(2026, 4, 25));

        assertThatThrownBy(() -> service.getOrgTouchSummary(req, new PageRequest()))
                .isInstanceOf(RptException.class)
                .satisfies(ex -> assertThat(((RptException) ex).getErrorCode())
                        .isEqualTo(RptErrorCode.CROSS_MODULE_CALL_FAILED));
    }

    @Test
    void getOrgTouchSummary_orgIdBlank_fallsBackToCurrentUserOrg() {
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR_CURR");

        TouchTaskSummaryDTO upstream = new TouchTaskSummaryDTO();
        upstream.setOrgId("BR_CURR");
        upstream.setOrgName("当前机构");
        upstream.setTotalCount(10L);
        upstream.setSuccessCount(5L);
        upstream.setCancelledCount(5L);
        when(touchTaskQueryApi.getOrgTouchSummary(eq("BR_CURR"), anyString(), anyString()))
                .thenReturn(upstream);

        TouchSummaryReqDTO req = new TouchSummaryReqDTO();
        req.setStartDate(LocalDate.of(2026, 4, 1));
        req.setEndDate(LocalDate.of(2026, 4, 25));
        req.setOrgId(null);  // 未传 → 兜底到当前用户 orgCode

        PageResult<ReportTouchOrgVO> result = service.getOrgTouchSummary(req, new PageRequest());
        assertThat(result.getRecords().get(0).getOrgCode()).isEqualTo("BR_CURR");
    }
}
