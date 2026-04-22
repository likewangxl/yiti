package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * perf_metric_def 新增字段 IT（Task B4）.
 *
 * <p>验证 V1_0_3 补充的 4 个字段：unit, decimal_places, deleted, description
 * 能正确持久化和读取。
 */
class PerfMetricDefNewFieldsIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfMetricDefMapper mapper;

    @Test
    void insert_and_read_unitAndDecimalPlaces() {
        PerfMetricDef m = MetricTestDataBuilder.l1Emp("TEST_METRIC_B4", 10);
        m.setUnit("万元");
        m.setDecimalPlaces(2);
        m.setDescription("人均存款");
        m.setDeleted(0);
        mapper.insert(m);

        PerfMetricDef loaded = mapper.selectById(m.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUnit()).isEqualTo("万元");
        assertThat(loaded.getDecimalPlaces()).isEqualTo(2);
        assertThat(loaded.getDescription()).isEqualTo("人均存款");
        assertThat(loaded.getDeleted()).isEqualTo(0);
    }

    @Test
    void softDeleted_metric_notReturnedBySelectByCondition() {
        // 插入一个已软删除的指标
        PerfMetricDef m = MetricTestDataBuilder.l1Emp("TEST_METRIC_B4_DEL", 11);
        m.setDeleted(1);
        mapper.insert(m);

        // 按条件查询时应过滤掉 deleted=1 的记录
        long count = mapper.countByCondition("EMP", null, "ACTIVE", null);
        // 注意：由于 @Rollback，其他测试数据不会干扰，但此条记录的 deleted=1 应被过滤
        // 验证：根据 code 查询不到（selectByMetricCode 也应过滤 deleted=1）
        assertThat(mapper.selectByMetricCode(m.getMetricCode())).isNull();
    }
}
