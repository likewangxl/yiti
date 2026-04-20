package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P1-C KPI 方案子域测试数据构造器.
 *
 * <p>统一构造带 {@code TEST_KPI_} 前缀的 PerfKpiScheme / PerfKpiItem 实体，
 * 避免污染生产数据；单线程 IT 的事务会回滚，这里的前缀主要保证并发/手工排查时可识别。
 */
public final class KpiTestDataBuilder implements TestDataBuilder {

    /** 统一测试编码前缀. */
    public static final String CODE_PREFIX = "TEST_KPI_";

    private KpiTestDataBuilder() {
    }

    /**
     * 构造默认 KPI 方案（ACTIVE / MONTHLY / openDetail=0）.
     *
     * @param codeSuffix scheme_code 后缀
     * @return 方案实体
     */
    public static PerfKpiScheme scheme(String codeSuffix) {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId(randomId());
        scheme.setSchemeCode(CODE_PREFIX + codeSuffix);
        scheme.setSchemeName("测试KPI方案-" + codeSuffix);
        scheme.setCycleType("MONTHLY");
        scheme.setOpenDetail(0);
        scheme.setStatus("ACTIVE");
        scheme.setCreatedBy("test");
        scheme.setCreatedTime(LocalDateTime.now());
        scheme.setUpdatedBy("test");
        scheme.setUpdatedTime(LocalDateTime.now());
        return scheme;
    }

    /**
     * 构造方案项（默认 weight=50, multiplier=1, minScore=0, maxScore=100）.
     *
     * @param schemeId   所属方案ID
     * @param metricCode 指标编码（由调用方保证已在 perf_metric_def 中存在——IT 中不做 FK）
     * @return 方案项实体
     */
    public static PerfKpiItem item(String schemeId, String metricCode) {
        return item(schemeId, metricCode, new BigDecimal("50.0000"));
    }

    /**
     * 构造方案项，指定权重.
     *
     * @param schemeId   所属方案ID
     * @param metricCode 指标编码
     * @param weight     权重
     * @return 方案项实体
     */
    public static PerfKpiItem item(String schemeId, String metricCode, BigDecimal weight) {
        PerfKpiItem item = new PerfKpiItem();
        item.setId(randomId());
        item.setSchemeId(schemeId);
        item.setMetricCode(metricCode);
        item.setWeight(weight);
        item.setMultiplier(new BigDecimal("1.0000"));
        item.setMinScore(new BigDecimal("0.0000"));
        item.setMaxScore(new BigDecimal("100.0000"));
        item.setCreatedTime(LocalDateTime.now());
        return item;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
