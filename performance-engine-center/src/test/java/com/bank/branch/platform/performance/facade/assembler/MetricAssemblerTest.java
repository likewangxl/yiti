package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricAssemblerTest {

    @Test
    @DisplayName("MetricDefDTO 保留单位、小数位、分类和详细说明")
    void toDto_keepsMetricContractFields() {
        PerfMetricDef entity = new PerfMetricDef();
        entity.setMetricCode("TEST_RATE");
        entity.setMetricName("机构达成率");
        entity.setDescription("实际值除以目标值乘百分百");
        entity.setMetricDesc("机构级目标达成率");
        entity.setBaseDim("ORG");
        entity.setValSlot(17);
        entity.setUnit("PERCENT");
        entity.setDecimalPlaces(4);
        entity.setMetricCategory("BRANCH_DASHBOARD");
        entity.setStatus("ACTIVE");

        MetricDefDTO dto = MetricAssembler.toDto(entity);

        assertThat(dto.getDescription()).isEqualTo("实际值除以目标值乘百分百");
        assertThat(dto.getMetricDesc()).isEqualTo("机构级目标达成率");
        assertThat(dto.getUnit()).isEqualTo("PERCENT");
        assertThat(dto.getDecimalPlaces()).isEqualTo(4);
        assertThat(dto.getMetricCategory()).isEqualTo("BRANCH_DASHBOARD");
        assertThat(dto.getBaseDim()).isEqualTo("ORG");
        assertThat(dto.getValSlot()).isEqualTo(17);
    }
}
