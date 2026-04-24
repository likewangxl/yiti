package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.5 P4.1：EmpIndexResultMapper.selectSlotValuesByDates batch 查询 IT.
 *
 * <p>验证点：
 * <ul>
 *   <li>单次 IN 查询返回多日期 slot 值的 Map（未命中日期不入 Map）</li>
 *   <li>dates 为空时 short-circuit 返回空 Map（不下发 SQL）</li>
 * </ul>
 *
 * <p>测试数据前缀 {@code TEST_P4_*}，与既有 TEST_EMPIDX_* / CONCUR_* 隔离。
 */
class EmpIndexResultMapperBatchDatesIT extends PerformanceMapperTestBase {

    @Autowired
    private EmpIndexResultMapper mapper;

    @Test
    @DisplayName("selectSlotValuesByDates：单次 IN 查询返回多日期 slot 值")
    void selectSlotValuesByDates_returnsPerDateMap() {
        String emp = "TEST_P4_E001";
        String version = "TEST_P4_v1";
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 1);
        LocalDate d3 = LocalDate.of(2025, 4, 1);
        mapper.insertSlotValue(emp, d1, version, 5, new BigDecimal("120"));
        mapper.insertSlotValue(emp, d2, version, 5, new BigDecimal("100"));
        mapper.insertSlotValue(emp, d3, version, 5, new BigDecimal("80"));
        // d4 故意不插入，验证返回 Map 不含缺失日期
        LocalDate d4 = LocalDate.of(2023, 1, 1);

        Map<LocalDate, BigDecimal> values = mapper.selectSlotValuesByDates(
                emp, List.of(d1, d2, d3, d4), version, 5);

        assertThat(values).hasSize(3);
        assertThat(values.get(d1)).isEqualByComparingTo("120");
        assertThat(values.get(d2)).isEqualByComparingTo("100");
        assertThat(values.get(d3)).isEqualByComparingTo("80");
        assertThat(values).doesNotContainKey(d4);
    }

    @Test
    @DisplayName("selectSlotValuesByDates：dates 为空或 null 时返回空 Map（不下发 SQL）")
    void selectSlotValuesByDates_emptyDates_returnsEmpty() {
        Map<LocalDate, BigDecimal> values = mapper.selectSlotValuesByDates(
                "TEST_P4_E001", List.of(), "v1", 5);
        assertThat(values).isEmpty();
    }
}
