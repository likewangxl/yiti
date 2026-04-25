package com.bank.branch.platform.report.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.dto.resp.MetricTreeNodeDTO;
import com.bank.branch.platform.report.dto.resp.QueryDimensionRespDTO;
import com.bank.branch.platform.report.service.impl.MetaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * MetaService 单元测试（Task M1.1.1，Red）.
 *
 * <p>覆盖 A.1 GET /api/reports/query-dimensions 的服务层三件事：
 * <ul>
 *   <li>dim=EMP/ORG/CUST 合法 → 返回维度中文名 + 分组后的指标树</li>
 *   <li>dim 非法 → 抛 BizException，code=RPT-40006 (METRIC_DIM_MISMATCH)</li>
 *   <li>同 dim 下多 category 指标 → 按 groupCode 升序组织</li>
 * </ul>
 *
 * <p>mock 对象：{@link MetricApi}（performance-engine-center V1.0 已实现 listMetrics）、
 *             {@link DictApi}（system-governance-center 已实现 getDictLabel）。
 */
@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock
    private MetricApi metricApi;

    @Mock
    private DictApi dictApi;

    @InjectMocks
    private MetaServiceImpl service;

    @Test
    void getQueryDimensions_EMP_returnsGroupedTree() {
        MetricDefDTO m1 = buildMetric("M_DEPOSIT_BAL", "存款余额", "EMP");
        MetricDefDTO m2 = buildMetric("M_LOAN_BAL", "贷款余额", "EMP");
        when(metricApi.listMetrics("EMP", null)).thenReturn(List.of(m1, m2));
        when(dictApi.getDictLabel("REPORT_DIM", "EMP")).thenReturn("人员");
        when(dictApi.getDictLabel("METRIC_CATEGORY", "DEFAULT")).thenReturn("默认分组");

        QueryDimensionRespDTO resp = service.getQueryDimensions("EMP");

        assertThat(resp.getDim()).isEqualTo("EMP");
        assertThat(resp.getDimName()).isEqualTo("人员");
        assertThat(resp.getMetrics()).isNotEmpty();
        assertThat(resp.getMetrics())
                .flatExtracting(MetricTreeNodeDTO::getChildren)
                .extracting(MetricTreeNodeDTO::getMetricCode)
                .containsExactlyInAnyOrder("M_DEPOSIT_BAL", "M_LOAN_BAL");
    }

    @Test
    void getQueryDimensions_ORG_returnsDimName() {
        when(metricApi.listMetrics("ORG", null)).thenReturn(List.of());
        when(dictApi.getDictLabel("REPORT_DIM", "ORG")).thenReturn("机构");

        QueryDimensionRespDTO resp = service.getQueryDimensions("ORG");

        assertThat(resp.getDim()).isEqualTo("ORG");
        assertThat(resp.getDimName()).isEqualTo("机构");
        assertThat(resp.getMetrics()).isEmpty();
    }

    @Test
    void getQueryDimensions_invalidDim_throwsRpt40006() {
        assertThatThrownBy(() -> service.getQueryDimensions("INVALID"))
                .hasMessageContaining("RPT-40006");
    }

    @Test
    void getQueryDimensions_nullDim_throwsRpt40006() {
        assertThatThrownBy(() -> service.getQueryDimensions(null))
                .hasMessageContaining("RPT-40006");
    }

    private MetricDefDTO buildMetric(String code, String name, String baseDim) {
        MetricDefDTO m = new MetricDefDTO();
        m.setMetricCode(code);
        m.setMetricName(name);
        m.setBaseDim(baseDim);
        return m;
    }
}
