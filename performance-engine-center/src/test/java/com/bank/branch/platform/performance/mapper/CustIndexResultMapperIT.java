package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.CustIndexResult;
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
 * CustIndexResultMapper 集成测试（Task P1.2 Red，客户宽表）.
 *
 * <p>结构与 EmpIndexResultMapperIT 对称，维度键为 {@code cust_id}。
 * <p>测试数据前缀 {@code TEST_CUSTIDX_*}。
 */
class CustIndexResultMapperIT extends PerformanceMapperTestBase {

    private static final String VERSION = "V_CUSTIDX_TEST";
    private static final LocalDate DATA_DATE = LocalDate.of(2026, 4, 1);

    @Autowired
    private CustIndexResultMapper mapper;

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=1）")
    void insertAndSelect_slot1_returnsValue() {
        mapper.insertSlotValue("TEST_CUSTIDX_C001", DATA_DATE, VERSION, 1, new BigDecimal("5000.00"));
        BigDecimal got = mapper.selectSlotValue("TEST_CUSTIDX_C001", DATA_DATE, VERSION, 1);
        assertThat(got).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=200 边界）")
    void insertAndSelect_slot200_returnsValue() {
        mapper.insertSlotValue("TEST_CUSTIDX_C002", DATA_DATE, VERSION, 200, new BigDecimal("0.0001"));
        assertThat(mapper.selectSlotValue("TEST_CUSTIDX_C002", DATA_DATE, VERSION, 200))
                .isEqualByComparingTo("0.0001");
    }

    @Test
    @DisplayName("selectSlotValuesByCusts 批量查询返回 custId → value 列表")
    void selectSlotValuesByCusts_returnsAllPresent() {
        mapper.insertSlotValue("TEST_CUSTIDX_C010", DATA_DATE, VERSION, 2, new BigDecimal("10"));
        mapper.insertSlotValue("TEST_CUSTIDX_C011", DATA_DATE, VERSION, 2, new BigDecimal("20"));

        List<CustMetricValueRow> rows = mapper.selectSlotValuesByCusts(
                List.of("TEST_CUSTIDX_C010", "TEST_CUSTIDX_C011"),
                DATA_DATE, VERSION, 2);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(CustMetricValueRow::getCustId)
                .containsExactlyInAnyOrder("TEST_CUSTIDX_C010", "TEST_CUSTIDX_C011");
    }

    @Test
    @DisplayName("UK 冲突：同 custId+dataDate+version 第二次 insertRow 抛 DuplicateKeyException")
    void insertRow_duplicate_throwsDuplicateKey() {
        CustIndexResult r1 = new CustIndexResult();
        r1.setCustId("TEST_CUSTIDX_C020");
        r1.setDataDate(DATA_DATE);
        r1.setVersion(VERSION);
        mapper.insertRow(r1);

        CustIndexResult r2 = new CustIndexResult();
        r2.setCustId("TEST_CUSTIDX_C020");
        r2.setDataDate(DATA_DATE);
        r2.setVersion(VERSION);
        assertThatThrownBy(() -> mapper.insertRow(r2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectSlotValue 未命中返回 null")
    void selectSlotValue_whenNotFound_returnsNull() {
        assertThat(mapper.selectSlotValue("TEST_CUSTIDX_NOEXIST", DATA_DATE, VERSION, 1)).isNull();
    }
}
