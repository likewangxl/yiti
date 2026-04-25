package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A.1 GET /api/reports/query-dimensions Controller IT（Task M1.1.1）.
 *
 * <p>继承 {@link BaseControllerIT} 旁路 PT_RESOURCE 时序问题；上游 MetricApi / DictApi
 * 以 @MockBean 注入假数据。
 */
class MetaControllerIT extends BaseControllerIT {

    @Test
    void getQueryDimensions_EMP_returns200AndTree() throws Exception {
        MetricDefDTO m = new MetricDefDTO();
        m.setMetricCode("M_DEPOSIT_BAL");
        m.setMetricName("存款余额");
        m.setBaseDim("EMP");
        when(metricApi.listMetrics(eq("EMP"), any())).thenReturn(List.of(m));
        when(dictApi.getDictLabel("REPORT_DIM", "EMP")).thenReturn("人员");
        when(dictApi.getDictLabel(eq("METRIC_CATEGORY"), any())).thenReturn("默认分组");

        mvc.perform(get("/api/reports/query-dimensions").param("dim", "EMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.dim").value("EMP"))
                .andExpect(jsonPath("$.data.dimName").value("人员"));
    }

    @Test
    void getQueryDimensions_invalidDim_returns40006Code() throws Exception {
        mvc.perform(get("/api/reports/query-dimensions").param("dim", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RPT-40006"));
    }
}
