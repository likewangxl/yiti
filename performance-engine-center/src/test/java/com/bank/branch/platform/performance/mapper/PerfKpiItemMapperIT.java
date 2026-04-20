package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfKpiItemMapper 集成测试.
 *
 * <p>覆盖 DoD 硬性场景：
 * <ul>
 *   <li>insert_whenSchemeIdMetricCodeDup_throwsDuplicateKey：uk_scheme_metric 唯一键冲突</li>
 * </ul>
 * 以及基础路径：insert/insertBatch / selectById / selectBySchemeId / selectBySchemeAndMetric /
 * updateByIdSelective / deleteById / deleteBySchemeId。
 */
class PerfKpiItemMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfKpiItemMapper mapper;

    @Autowired
    private PerfKpiSchemeMapper schemeMapper;

    /**
     * 先创建一个父方案，返回其 id。
     * 子表 DDL 无 FK 约束，理论上可以用随机 schemeId；但为了接近真实使用路径，这里仍构造真实的父方案。
     */
    private String insertParentScheme(String codeSuffix) {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme(codeSuffix);
        schemeMapper.insert(scheme);
        return scheme.getId();
    }

    @Test
    @DisplayName("insert 后可按 id 查回方案项")
    void insertAndSelectById_ok() {
        String schemeId = insertParentScheme("I_SEL_001");
        PerfKpiItem item = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_A");

        mapper.insert(item);
        PerfKpiItem loaded = mapper.selectById(item.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getSchemeId()).isEqualTo(schemeId);
        assertThat(loaded.getMetricCode()).isEqualTo("TEST_KPI_METRIC_A");
        assertThat(loaded.getWeight()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(loaded.getMultiplier()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(loaded.getMinScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(loaded.getMaxScore()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    @DisplayName("DoD: 同 schemeId + metricCode 再次插入触发 UK 冲突")
    void insert_whenSchemeIdMetricCodeDup_throwsDuplicateKey() {
        String schemeId = insertParentScheme("I_DUP");
        PerfKpiItem first = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_DUP");
        mapper.insert(first);
        // 不同 id，但 (scheme_id, metric_code) 相同，触发 uk_scheme_metric
        PerfKpiItem dup = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_DUP");

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("insertBatch 一次写入多条项")
    void insertBatch_ok() {
        String schemeId = insertParentScheme("I_BATCH");
        List<PerfKpiItem> items = List.of(
                KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_B1"),
                KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_B2"),
                KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_B3")
        );

        int rows = mapper.insertBatch(items);

        assertThat(rows).isEqualTo(3);
        List<PerfKpiItem> loaded = mapper.selectBySchemeId(schemeId);
        assertThat(loaded).hasSize(3);
        assertThat(loaded).extracting(PerfKpiItem::getMetricCode)
                .containsExactlyInAnyOrder("TEST_KPI_METRIC_B1", "TEST_KPI_METRIC_B2", "TEST_KPI_METRIC_B3");
    }

    @Test
    @DisplayName("selectBySchemeAndMetric 存在时返回对应项，不存在返回 null")
    void selectBySchemeAndMetric_ok() {
        String schemeId = insertParentScheme("I_SEL_SM");
        PerfKpiItem item = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_SM");
        mapper.insert(item);

        PerfKpiItem loaded = mapper.selectBySchemeAndMetric(schemeId, "TEST_KPI_METRIC_SM");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(item.getId());

        assertThat(mapper.selectBySchemeAndMetric(schemeId, "TEST_KPI_METRIC_NO_SUCH")).isNull();
    }

    @Test
    @DisplayName("updateByIdSelective 只更新非空字段")
    void updateByIdSelective_onlyUpdatesNonNullFields() {
        String schemeId = insertParentScheme("I_UPD");
        PerfKpiItem item = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_UPD");
        mapper.insert(item);

        PerfKpiItem patch = new PerfKpiItem();
        patch.setId(item.getId());
        patch.setWeight(new BigDecimal("80.0000"));
        int rows = mapper.updateByIdSelective(patch);

        assertThat(rows).isEqualTo(1);
        PerfKpiItem loaded = mapper.selectById(item.getId());
        assertThat(loaded.getWeight()).isEqualByComparingTo(new BigDecimal("80.0000"));
        assertThat(loaded.getMetricCode()).isEqualTo("TEST_KPI_METRIC_UPD");
    }

    @Test
    @DisplayName("deleteById 可删除单条项")
    void deleteById_ok() {
        String schemeId = insertParentScheme("I_DEL");
        PerfKpiItem item = KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_DEL");
        mapper.insert(item);

        int rows = mapper.deleteById(item.getId());

        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(item.getId())).isNull();
    }

    @Test
    @DisplayName("deleteBySchemeId 可级联清理方案下所有项")
    void deleteBySchemeId_cascadeClear() {
        String schemeId = insertParentScheme("I_DEL_ALL");
        mapper.insert(KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_DA1"));
        mapper.insert(KpiTestDataBuilder.item(schemeId, "TEST_KPI_METRIC_DA2"));

        int rows = mapper.deleteBySchemeId(schemeId);

        assertThat(rows).isEqualTo(2);
        assertThat(mapper.selectBySchemeId(schemeId)).isEmpty();
    }
}
