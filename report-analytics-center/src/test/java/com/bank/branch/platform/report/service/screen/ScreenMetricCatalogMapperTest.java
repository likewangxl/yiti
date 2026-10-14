package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.resp.ScreenMetricCatalogItemDTO;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao.MetricDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标目录只翻译 PERF_METRIC_DEF 的可绑定快照，不猜测单位，也不暴露计算 SQL。
 */
class ScreenMetricCatalogMapperTest {

    @Test
    void mapsOrgMetricAndKeepsConfirmedAmountUnitWhenColumnIsBlank() {
        List<ScreenMetricCatalogItemDTO> items = ScreenMetricCatalogMapper.toItems(List.of(
                definition("M_0265", "一般性存款月均余额-机构", "ORG", 12, null,
                        "规模类", "SUM", 1, "DAY", null, "ACTIVE")),
                null, null);

        assertThat(items).hasSize(1);
        ScreenMetricCatalogItemDTO item = items.get(0);
        assertThat(item.getMetricCode()).isEqualTo("M_0265");
        assertThat(item.getBaseDim()).isEqualTo("ORG");
        assertThat(item.getTableName()).isEqualTo("ORG_INDEX_RESULT");
        assertThat(item.getSubjectColumn()).isEqualTo("org_code");
        assertThat(item.getOutputColumn()).isEqualTo("一般性存款月均余额-机构");
        assertThat(item.getValSlot()).isEqualTo(12);
        assertThat(item.getUnit()).isEqualTo("YUAN");
        assertThat(item.getUnitSource()).isEqualTo("CONFIRMED_CODE");
        assertThat(item.isBindable()).isTrue();
        assertThat(item.getSummaryRule()).isEqualTo("SUM");
    }

    @Test
    void prefersExplicitUnitAndDescriptionFallbackWithoutGuessingUnknownText() {
        List<ScreenMetricCatalogItemDTO> items = ScreenMetricCatalogMapper.toItems(List.of(
                definition("M_0001", "完成率", "EMP", 3, "%", null, "AVG", 2, "MONTH",
                        "{\"_unit\":\"万元\",\"_category\":\"效益类/收入\"}", "ACTIVE"),
                definition("M_0002", "未知口径", "CUST", 4, "箱", "质量类", "SUM", 1, "DAY",
                        "{\"_unit\":\"\"}", "ACTIVE")),
                null, null);

        assertThat(items.get(0).getUnit()).isEqualTo("PERCENT");
        assertThat(items.get(0).getUnitSource()).isEqualTo("COLUMN");
        assertThat(items.get(0).getMetricCategory()).isEqualTo("效益类");
        assertThat(items.get(0).getTableName()).isEqualTo("EMP_INDEX_RESULT");
        assertThat(items.get(1).getUnit()).isNull();
        assertThat(items.get(1).getUnitRaw()).isEqualTo("箱");
        assertThat(items.get(1).getUnitSource()).isEqualTo("NONE");
        assertThat(items.get(1).isBindable()).isTrue();
    }

    @Test
    void marksMissingSlotUnbindableAndFiltersDimensionAndKeyword() {
        List<MetricDefinition> rows = List.of(
                definition("M_0090", "无槽位指标", "ORG", null, "元", "规模类", "SUM", 1, "DAY", null, "ACTIVE"),
                definition("M_0277", "对公一般性存款余额", "ORG", 20, "元", "规模类", "SUM", 1, "DAY", null, "ACTIVE"),
                definition("M_0106", "对公一般性存款余额较上季", "CUST", 59, null, "规模类", "SUM", 2, "DAY", null, "ACTIVE"));

        assertThat(ScreenMetricCatalogMapper.toItems(rows, "ORG", "存款")).extracting(ScreenMetricCatalogItemDTO::getMetricCode)
                .containsExactly("M_0277");
        ScreenMetricCatalogItemDTO unbound = ScreenMetricCatalogMapper.toItems(rows, null, "无槽位").get(0);
        assertThat(unbound.isBindable()).isFalse();
        assertThat(unbound.getUnavailableReason()).contains("val_slot");
        assertThat(ScreenMetricCatalogMapper.toItems(rows, "BRANCH", null)).isEmpty();
    }

    @Test
    void stripsAliasBreakingCharactersFromOutputColumn() {
        ScreenMetricCatalogItemDTO item = ScreenMetricCatalogMapper.toItems(List.of(
                definition("M_0347", "对公`贷款\"余额", "ORG", 8, "元", "规模类", "SUM", 1, "DAY", null, "ACTIVE")),
                null, null).get(0);

        assertThat(item.getOutputColumn()).isEqualTo("对公贷款余额");
        assertThat(item.getMetricName()).isEqualTo("对公`贷款\"余额");
    }

    private static MetricDefinition definition(String code, String name, String baseDim, Integer slot,
                                               String unit, String category, String summaryRule,
                                               Integer level, String freq, String desc, String status) {
        return new MetricDefinition(code, name, baseDim, slot, unit, category, summaryRule, level, freq, desc, status);
    }
}
