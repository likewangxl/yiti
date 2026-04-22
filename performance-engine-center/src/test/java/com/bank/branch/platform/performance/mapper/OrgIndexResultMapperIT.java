package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.OrgIndexResult;
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
 * OrgIndexResultMapper 集成测试（Task P1.2 Red，机构宽表）.
 *
 * <p>结构与 EmpIndexResultMapperIT 完全对称，唯一不同：维度键是 {@code org_code}。
 * <p>测试数据前缀 {@code TEST_ORGIDX_*}。
 */
class OrgIndexResultMapperIT extends PerformanceMapperTestBase {

    private static final String VERSION = "V_ORGIDX_TEST";
    private static final LocalDate DATA_DATE = LocalDate.of(2026, 4, 1);

    @Autowired
    private OrgIndexResultMapper mapper;

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=1）")
    void insertAndSelect_slot1_returnsValue() {
        mapper.insertSlotValue("TEST_ORGIDX_O001", DATA_DATE, VERSION, 1, new BigDecimal("1000000.00"));
        BigDecimal got = mapper.selectSlotValue("TEST_ORGIDX_O001", DATA_DATE, VERSION, 1);
        assertThat(got).isEqualByComparingTo("1000000.00");
    }

    @Test
    @DisplayName("insertSlotValue 后可用 selectSlotValue 回读（slot=200 边界）")
    void insertAndSelect_slot200_returnsValue() {
        mapper.insertSlotValue("TEST_ORGIDX_O002", DATA_DATE, VERSION, 200, new BigDecimal("123.4567"));
        BigDecimal got = mapper.selectSlotValue("TEST_ORGIDX_O002", DATA_DATE, VERSION, 200);
        assertThat(got).isEqualByComparingTo("123.4567");
    }

    @Test
    @DisplayName("selectSlotValuesByOrgs 批量查询返回 orgCode → value 列表")
    void selectSlotValuesByOrgs_returnsAllPresent() {
        mapper.insertSlotValue("TEST_ORGIDX_O010", DATA_DATE, VERSION, 3, new BigDecimal("100"));
        mapper.insertSlotValue("TEST_ORGIDX_O011", DATA_DATE, VERSION, 3, new BigDecimal("200"));

        List<OrgMetricValueRow> rows = mapper.selectSlotValuesByOrgs(
                List.of("TEST_ORGIDX_O010", "TEST_ORGIDX_O011"),
                DATA_DATE, VERSION, 3);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OrgMetricValueRow::getOrgCode)
                .containsExactlyInAnyOrder("TEST_ORGIDX_O010", "TEST_ORGIDX_O011");
    }

    @Test
    @DisplayName("UK 冲突：同 orgCode+dataDate+version 第二次 insertRow 抛 DuplicateKeyException")
    void insertRow_duplicate_throwsDuplicateKey() {
        OrgIndexResult r1 = new OrgIndexResult();
        r1.setOrgCode("TEST_ORGIDX_O020");
        r1.setDataDate(DATA_DATE);
        r1.setVersion(VERSION);
        mapper.insertRow(r1);

        OrgIndexResult r2 = new OrgIndexResult();
        r2.setOrgCode("TEST_ORGIDX_O020");
        r2.setDataDate(DATA_DATE);
        r2.setVersion(VERSION);
        assertThatThrownBy(() -> mapper.insertRow(r2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectSlotValue 未命中返回 null")
    void selectSlotValue_whenNotFound_returnsNull() {
        assertThat(mapper.selectSlotValue("TEST_ORGIDX_NOEXIST", DATA_DATE, VERSION, 1)).isNull();
    }
}
