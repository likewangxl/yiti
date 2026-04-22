package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.EmpIndexResult;
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
 * EmpIndexResultMapper 集成测试（Task P1.1 Red）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>insertSlotValue / selectSlotValue 基础回读（slot=1 / slot=200 边界）</li>
 *   <li>selectSlotValuesByEmps 批量查询（empId → value 映射）</li>
 *   <li>UK (emp_id, data_date, version) 冲突：同主键第二次插入抛 DuplicateKeyException</li>
 *   <li>同一行内可同时写入多个 slot（insertSlotValue 自动 UPSERT 单列）</li>
 * </ul>
 *
 * <p>测试数据前缀 {@code TEST_EMPIDX_*}，避免与其他子域冲突。
 * <p>继承 {@link PerformanceMapperTestBase} 获得 @Transactional + @Rollback 自动清理。
 */
class EmpIndexResultMapperIT extends PerformanceMapperTestBase {

    private static final String VERSION = "V_EMPIDX_TEST";
    private static final LocalDate DATA_DATE = LocalDate.of(2026, 4, 1);

    @Autowired
    private EmpIndexResultMapper mapper;

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=1）")
    void insertAndSelect_slot1_returnsValue() {
        mapper.insertSlotValue("TEST_EMPIDX_E001", DATA_DATE, VERSION, 1, new BigDecimal("123456.78"));

        BigDecimal got = mapper.selectSlotValue("TEST_EMPIDX_E001", DATA_DATE, VERSION, 1);

        assertThat(got).isEqualByComparingTo("123456.78");
    }

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=200 边界）")
    void insertAndSelect_slot200_returnsValue() {
        mapper.insertSlotValue("TEST_EMPIDX_E002", DATA_DATE, VERSION, 200, new BigDecimal("9999.9999"));

        BigDecimal got = mapper.selectSlotValue("TEST_EMPIDX_E002", DATA_DATE, VERSION, 200);

        assertThat(got).isEqualByComparingTo("9999.9999");
    }

    @Test
    @DisplayName("同一员工同一日期同一版本可追加写入不同 slot")
    void insertSlotValue_differentSlots_sameRow_allReadable() {
        mapper.insertSlotValue("TEST_EMPIDX_E003", DATA_DATE, VERSION, 5, new BigDecimal("100.00"));
        mapper.insertSlotValue("TEST_EMPIDX_E003", DATA_DATE, VERSION, 10, new BigDecimal("200.00"));

        assertThat(mapper.selectSlotValue("TEST_EMPIDX_E003", DATA_DATE, VERSION, 5))
                .isEqualByComparingTo("100.00");
        assertThat(mapper.selectSlotValue("TEST_EMPIDX_E003", DATA_DATE, VERSION, 10))
                .isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("selectSlotValuesByEmps 批量查询返回 empId → value 列表")
    void selectSlotValuesByEmps_returnsAllPresent() {
        mapper.insertSlotValue("TEST_EMPIDX_E010", DATA_DATE, VERSION, 7, new BigDecimal("10"));
        mapper.insertSlotValue("TEST_EMPIDX_E011", DATA_DATE, VERSION, 7, new BigDecimal("20"));
        mapper.insertSlotValue("TEST_EMPIDX_E012", DATA_DATE, VERSION, 7, new BigDecimal("30"));

        List<EmpMetricValueRow> rows = mapper.selectSlotValuesByEmps(
                List.of("TEST_EMPIDX_E010", "TEST_EMPIDX_E011", "TEST_EMPIDX_E012"),
                DATA_DATE, VERSION, 7);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(EmpMetricValueRow::getEmpId)
                .containsExactlyInAnyOrder("TEST_EMPIDX_E010", "TEST_EMPIDX_E011", "TEST_EMPIDX_E012");
        assertThat(rows).extracting(EmpMetricValueRow::getMetricValue)
                .extracting(BigDecimal::intValue)
                .containsExactlyInAnyOrder(10, 20, 30);
    }

    @Test
    @DisplayName("UK 冲突：同 empId+dataDate+version 第二次 insertRow 抛 DuplicateKeyException")
    void insertRow_duplicate_throwsDuplicateKey() {
        EmpIndexResult row = new EmpIndexResult();
        row.setEmpId("TEST_EMPIDX_E020");
        row.setDataDate(DATA_DATE);
        row.setVersion(VERSION);
        mapper.insertRow(row);

        EmpIndexResult dup = new EmpIndexResult();
        dup.setEmpId("TEST_EMPIDX_E020");
        dup.setDataDate(DATA_DATE);
        dup.setVersion(VERSION);
        assertThatThrownBy(() -> mapper.insertRow(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectSlotValue 记录不存在时返回 null")
    void selectSlotValue_whenNotFound_returnsNull() {
        BigDecimal got = mapper.selectSlotValue("TEST_EMPIDX_NOEXIST", DATA_DATE, VERSION, 1);
        assertThat(got).isNull();
    }
}
