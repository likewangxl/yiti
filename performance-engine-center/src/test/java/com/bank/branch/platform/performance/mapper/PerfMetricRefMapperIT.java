package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfMetricRefMapper 集成测试.
 */
class PerfMetricRefMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfMetricRefMapper mapper;

    @Test
    @DisplayName("insertBatch 可批量插入引用关系")
    void insertBatch_ok() {
        List<PerfMetricRef> refs = List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_A", "TEST_METRIC_CHILD_A"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_A", "TEST_METRIC_CHILD_B"));

        int rows = mapper.insertBatch(refs);

        assertThat(rows).isEqualTo(2);
        assertThat(mapper.selectByMetricCode("TEST_METRIC_PARENT_A")).hasSize(2);
    }

    @Test
    @DisplayName("metric_code + ref_metric_code 重复时触发 UK 冲突")
    void insertBatch_whenUkConflict_throwsDuplicateKey() {
        mapper.insertBatch(List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_DUP", "TEST_METRIC_CHILD_DUP")));

        List<PerfMetricRef> dup = List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_DUP", "TEST_METRIC_CHILD_DUP"));

        assertThatThrownBy(() -> mapper.insertBatch(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("deleteByMetricCode 只删除指定上层指标的引用")
    void deleteByMetricCode_deletesOnlyOwnRefs() {
        mapper.insertBatch(List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_DEL", "TEST_METRIC_CHILD_A"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_DEL", "TEST_METRIC_CHILD_B"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_KEEP", "TEST_METRIC_CHILD_A")));

        int rows = mapper.deleteByMetricCode("TEST_METRIC_PARENT_DEL");

        assertThat(rows).isEqualTo(2);
        assertThat(mapper.selectByMetricCode("TEST_METRIC_PARENT_DEL")).isEmpty();
        assertThat(mapper.selectByMetricCode("TEST_METRIC_PARENT_KEEP")).hasSize(1);
    }

    @Test
    @DisplayName("selectByMetricCode 返回某个上层指标的全部引用")
    void selectByMetricCode_returnsRefs() {
        mapper.insertBatch(List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_Q", "TEST_METRIC_CHILD_A"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_Q", "TEST_METRIC_CHILD_B")));

        List<PerfMetricRef> refs = mapper.selectByMetricCode("TEST_METRIC_PARENT_Q");

        assertThat(refs).extracting(PerfMetricRef::getRefMetricCode)
                .containsExactlyInAnyOrder("TEST_METRIC_CHILD_A", "TEST_METRIC_CHILD_B");
    }

    @Test
    @DisplayName("selectByRefMetricCode 可反查被谁引用")
    void selectByRefMetricCode_returnsDependents() {
        mapper.insertBatch(List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_X", "TEST_METRIC_CHILD_X"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_Y", "TEST_METRIC_CHILD_X")));

        List<PerfMetricRef> refs = mapper.selectByRefMetricCode("TEST_METRIC_CHILD_X");

        assertThat(refs).extracting(PerfMetricRef::getMetricCode)
                .containsExactlyInAnyOrder("TEST_METRIC_PARENT_X", "TEST_METRIC_PARENT_Y");
    }

    @Test
    @DisplayName("selectAll 可加载整张引用图")
    void selectAll_returnsWholeGraph() {
        mapper.insertBatch(List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_ALL_A", "TEST_METRIC_CHILD_ALL_A"),
                MetricTestDataBuilder.ref("TEST_METRIC_PARENT_ALL_B", "TEST_METRIC_CHILD_ALL_B")));

        List<PerfMetricRef> refs = mapper.selectAll();

        assertThat(refs).extracting(PerfMetricRef::getMetricCode)
                .contains("TEST_METRIC_PARENT_ALL_A", "TEST_METRIC_PARENT_ALL_B");
    }
}
