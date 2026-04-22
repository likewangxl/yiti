package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KpiResultMapper 集成测试（Task P1.2 Red，KPI 结果表）.
 *
 * <p>DDL 对齐 V1_0_0 §13：UK=(emp_id, cycle_type, cycle_date, as_of_date)；
 * 核心字段 kpi_total_score(decimal) + detail_json(longtext)。
 * <p>测试数据前缀 {@code TEST_KPIRES_*}。
 */
class KpiResultMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private KpiResultMapper mapper;

    private KpiResult newResult(String empId, LocalDate cycleDate, LocalDate asOfDate, BigDecimal total) {
        KpiResult r = new KpiResult();
        r.setEmpId(empId);
        r.setCycleType("MONTHLY");
        r.setCycleDate(cycleDate);
        r.setAsOfDate(asOfDate);
        r.setDataVersion("V_KPIRES_TEST");
        r.setKpiTotalScore(total);
        r.setDetailJson("{\"items\":[]}");
        return r;
    }

    @Test
    @DisplayName("insert 后可按主键 selectById 回读")
    void insert_and_selectById_returnsSaved() {
        KpiResult r = newResult("TEST_KPIRES_E001", LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1), new BigDecimal("85.1234"));

        mapper.insert(r);
        assertThat(r.getId()).isNotNull();

        KpiResult got = mapper.selectById(r.getId());
        assertThat(got).isNotNull();
        assertThat(got.getEmpId()).isEqualTo("TEST_KPIRES_E001");
        assertThat(got.getKpiTotalScore()).isEqualByComparingTo("85.1234");
        assertThat(got.getDetailJson()).contains("items");
    }

    @Test
    @DisplayName("selectByEmpCycle 查询指定员工的 KPI 历史")
    void selectByEmpCycle_returnsAllMatching() {
        mapper.insert(newResult("TEST_KPIRES_E010", LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 1), new BigDecimal("80")));
        mapper.insert(newResult("TEST_KPIRES_E010", LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1), new BigDecimal("90")));

        List<KpiResult> list = mapper.selectByEmpCycle("TEST_KPIRES_E010", "MONTHLY");

        assertThat(list).hasSize(2);
        assertThat(list).extracting(KpiResult::getKpiTotalScore)
                .extracting(BigDecimal::intValue)
                .containsExactlyInAnyOrder(80, 90);
    }

    @Test
    @DisplayName("UK 冲突：同 (empId, cycleType, cycleDate, asOfDate) 第二次 insert 抛 DuplicateKeyException")
    void insert_duplicate_throwsDuplicateKey() {
        KpiResult r1 = newResult("TEST_KPIRES_E020", LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1), new BigDecimal("70"));
        mapper.insert(r1);

        KpiResult r2 = newResult("TEST_KPIRES_E020", LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1), new BigDecimal("75"));

        assertThatThrownBy(() -> mapper.insert(r2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById(-1L)).isNull();
    }
}
