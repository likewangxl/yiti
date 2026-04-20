package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfMetricDefMapper 集成测试.
 */
class PerfMetricDefMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfMetricDefMapper mapper;

    @Test
    @DisplayName("insert 后可按 id 查回指标定义")
    void insertAndSelectById_ok() {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("DEP_BAL", 1);

        mapper.insert(def);
        PerfMetricDef loaded = mapper.selectById(def.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getMetricCode()).isEqualTo("TEST_METRIC_DEP_BAL");
        assertThat(loaded.getValSlot()).isEqualTo(1);
    }

    @Test
    @DisplayName("相同 metric_code 再次插入触发 UK 冲突")
    void insert_whenMetricCodeDup_throwsDuplicateKey() {
        PerfMetricDef first = MetricTestDataBuilder.l1Emp("DUP_CODE", 2);
        mapper.insert(first);
        PerfMetricDef dup = MetricTestDataBuilder.l1Emp("DUP_CODE", 3);

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectByMetricCode 不存在时返回 null")
    void selectByMetricCode_whenNotExists_returnsNull() {
        assertThat(mapper.selectByMetricCode("TEST_METRIC_NO_SUCH")).isNull();
    }

    @Test
    @DisplayName("selectByMetricCodes 可批量查询多条指标")
    void selectByMetricCodes_batch_ok() {
        mapper.insert(MetricTestDataBuilder.l1Emp("BATCH_A", 10));
        mapper.insert(MetricTestDataBuilder.l1Emp("BATCH_B", 11));

        List<PerfMetricDef> list = mapper.selectByMetricCodes(
                List.of("TEST_METRIC_BATCH_A", "TEST_METRIC_BATCH_B"));

        assertThat(list).hasSize(2);
        assertThat(list).extracting(PerfMetricDef::getMetricCode)
                .containsExactlyInAnyOrder("TEST_METRIC_BATCH_A", "TEST_METRIC_BATCH_B");
    }

    @Test
    @DisplayName("selectOccupiedSlots 返回维度下全部已占用槽位，不区分状态")
    void selectOccupiedSlots_returnsAllSlotsRegardlessOfStatus() {
        PerfMetricDef active = MetricTestDataBuilder.l1Emp("SLOT_A", 20);
        PerfMetricDef disabled = MetricTestDataBuilder.l1Emp("SLOT_B", 21);
        disabled.setStatus("DISABLED");
        mapper.insert(active);
        mapper.insert(disabled);

        Set<Integer> slots = mapper.selectOccupiedSlots("EMP");

        assertThat(slots).contains(20, 21);
    }

    @Test
    @DisplayName("selectByCondition 关键字可匹配编码或名称")
    void selectByCondition_withKeyword_matchesCodeOrName() {
        mapper.insert(MetricTestDataBuilder.l1Emp("KW_CARD", 30));

        List<PerfMetricDef> list = mapper.selectByCondition("EMP", 1, null, "KW_CARD", 0, 10);

        assertThat(list).extracting(PerfMetricDef::getMetricCode)
                .contains("TEST_METRIC_KW_CARD");
    }

    @Test
    @DisplayName("countByCondition 与条件查询保持一致")
    void countByCondition_filtersSameAsSelect() {
        mapper.insert(MetricTestDataBuilder.l1Emp("CNT_A", 40));
        mapper.insert(MetricTestDataBuilder.l1Emp("CNT_B", 41));
        PerfMetricDef disabled = MetricTestDataBuilder.l1Emp("CNT_C", 42);
        disabled.setStatus("DISABLED");
        mapper.insert(disabled);

        long count = mapper.countByCondition("EMP", 1, "ACTIVE", "CNT_");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("updateStatusById 会更新状态和更新人")
    void updateStatusById_changesStatusAndUpdatedBy() {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("STATUS_T", 50);
        mapper.insert(def);

        int rows = mapper.updateStatusById(def.getId(), "DISABLED", "test-admin");

        assertThat(rows).isEqualTo(1);
        PerfMetricDef loaded = mapper.selectById(def.getId());
        assertThat(loaded.getStatus()).isEqualTo("DISABLED");
        assertThat(loaded.getUpdatedBy()).isEqualTo("test-admin");
    }

    @Test
    @DisplayName("releaseSlotById 仅对 DISABLED 指标释放槽位")
    void releaseSlotById_whenDisabled_setsSlotToNull() {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("REL_T", 60);
        def.setStatus("DISABLED");
        mapper.insert(def);

        int rows = mapper.releaseSlotById(def.getId(), "test-admin");

        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(def.getId()).getValSlot()).isNull();
    }
}
