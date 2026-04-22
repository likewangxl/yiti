package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfMetricRef;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 指标库测试数据构造器.
 */
public final class MetricTestDataBuilder implements TestDataBuilder {

    private MetricTestDataBuilder() {
    }

    /**
     * 构造一级 EMP 指标.
     *
     * @param codeSuffix 指标编码后缀
     * @param slot       槽位
     * @return 指标定义
     */
    public static PerfMetricDef l1Emp(String codeSuffix, Integer slot) {
        PerfMetricDef def = new PerfMetricDef();
        def.setId(randomId());
        def.setMetricCode("TEST_METRIC_" + codeSuffix);
        def.setMetricName("测试指标-" + codeSuffix);
        def.setMetricNameEn("metric-" + codeSuffix.toLowerCase());
        def.setMetricDesc("指标说明-" + codeSuffix);
        def.setBaseDim("EMP");
        def.setMetricLevel(1);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1");
        def.setSummaryRule("SUM");
        def.setValSlot(slot);
        def.setStatus("ACTIVE");
        def.setDeleted(0); // B4: 软删除字段默认 0（未删除），避免 AND deleted=0 过滤掉测试数据
        def.setCreatedBy("test");
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedBy("test");
        def.setUpdatedTime(LocalDateTime.now());
        return def;
    }

    /**
     * 构造二级 EMP 指标.
     *
     * @param codeSuffix   指标编码后缀
     * @param slot         槽位
     * @param refCodesJson 被引用编码 JSON
     * @return 指标定义
     */
    public static PerfMetricDef l2Emp(String codeSuffix, Integer slot, String refCodesJson) {
        PerfMetricDef def = l1Emp(codeSuffix, slot);
        def.setMetricLevel(2);
        def.setCalcLogicType("EXPR");
        def.setSqlText(null);
        def.setExprText("#A + #B");
        def.setRefMetricCodes(refCodesJson);
        return def;
    }

    /**
     * 构造引用关系.
     *
     * @param metricCode    上层指标编码
     * @param refMetricCode 被引用下层指标编码
     * @return 引用关系实体
     */
    public static PerfMetricRef ref(String metricCode, String refMetricCode) {
        PerfMetricRef ref = new PerfMetricRef();
        ref.setId(randomId());
        ref.setMetricCode(metricCode);
        ref.setRefMetricCode(refMetricCode);
        ref.setCreatedTime(LocalDateTime.now());
        return ref;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
