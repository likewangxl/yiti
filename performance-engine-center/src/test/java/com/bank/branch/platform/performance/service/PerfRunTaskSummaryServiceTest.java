package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfRunTaskSummaryServiceTest {

    @Mock PerfRunTaskMapper runTaskMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock BizScopeApi bizScopeApi;
    @Mock com.bank.branch.platform.performance.mapper.PerfMetricDefMapper metricDefMapper;
    @Mock com.bank.branch.platform.portal.api.AddressBookApi addressBookApi;

    @InjectMocks PerfRunTaskService service;

    @Test
    void pageMetricSummary_admin_noScopeFilter_returnsPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ADMIN");
        when(bizScopeApi.resolveScope(eq("ADMIN"), any())).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countMetricSummary(eq("METRIC_RUN"), isNull(), isNull())).thenReturn(1L);
        when(runTaskMapper.selectMetricSummary(eq("METRIC_RUN"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(MetricSummaryDTO.builder()
                        .metricCode("M_1").metricName("指标一").runCount(3L).build()));

        PageResult<MetricSummaryDTO> page = service.pageMetricSummary("METRIC_RUN", null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getRunCount()).isEqualTo(3L);
    }

    @Test
    void pageMetricSummary_emptyTotal_skipsSelect() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("U");
        when(bizScopeApi.resolveScope(eq("U"), any())).thenReturn(DataScopeType.SELF);
        when(runTaskMapper.countMetricSummary(eq("METRIC_RUN"), isNull(), any())).thenReturn(0L);

        PageResult<MetricSummaryDTO> page = service.pageMetricSummary("METRIC_RUN", null, 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }
}
