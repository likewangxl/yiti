package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P1-D 目标子域测试数据构造器.
 *
 * <p>统一构造带 {@code TEST_TGT_} 前缀的 PerfTargetPlan / PerfTargetValue 实体，
 * 避免污染生产数据；单线程 IT 的事务会回滚，这里的前缀主要保证并发/手工排查时可识别。
 */
public final class TargetTestDataBuilder implements TestDataBuilder {

    /** 统一测试编码前缀. */
    public static final String CODE_PREFIX = "TEST_TGT_";

    private TargetTestDataBuilder() {
    }

    /**
     * 构造默认目标方案（ACTIVE / EMP / YEAR / effectiveDate=今天）.
     *
     * @param codeSuffix  plan_code 后缀
     * @param kpiSchemeId 关联 KPI 方案ID
     * @return 方案实体
     */
    public static PerfTargetPlan plan(String codeSuffix, String kpiSchemeId) {
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId(randomId());
        plan.setPlanCode(CODE_PREFIX + codeSuffix);
        plan.setPlanName("测试目标方案-" + codeSuffix);
        plan.setKpiSchemeId(kpiSchemeId);
        plan.setTargetDim("EMP");
        plan.setTargetCycle("YEAR");
        plan.setEffectiveDate(LocalDate.now());
        plan.setStatus("ACTIVE");
        plan.setCreatedBy("test");
        plan.setCreatedTime(LocalDateTime.now());
        plan.setUpdatedBy("test");
        plan.setUpdatedTime(LocalDateTime.now());
        return plan;
    }

    /**
     * 构造目标值（默认 baseValue=0）.
     *
     * @param planId      所属方案ID
     * @param subjectType 对象类型（EMP/ORG）
     * @param subjectId   对象ID
     * @param cycleKey    周期键
     * @param metricCode  指标编码（由调用方保证已在 perf_metric_def 中存在——IT 中不做 FK）
     * @param target      目标值
     * @return 目标值实体
     */
    public static PerfTargetValue value(String planId, String subjectType, String subjectId,
                                        String cycleKey, String metricCode, BigDecimal target) {
        PerfTargetValue v = new PerfTargetValue();
        v.setId(randomId());
        v.setPlanId(planId);
        v.setSubjectType(subjectType);
        v.setSubjectId(subjectId);
        v.setCycleKey(cycleKey);
        v.setMetricCode(metricCode);
        v.setTargetValue(target);
        v.setBaseValue(new BigDecimal("0.0000"));
        v.setCreatedBy("test");
        return v;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
