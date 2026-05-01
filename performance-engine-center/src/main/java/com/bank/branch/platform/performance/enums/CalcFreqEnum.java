package com.bank.branch.platform.performance.enums;

/**
 * 指标计算频率枚举（V1.7）.
 *
 * <p>用于 PerfMetricDef.calcFreq 字段约束 + cron 表达式默认值推导.
 * 与 CycleTypeEnum（KPI 考核周期）解耦：指标"多久跑一次" vs KPI"多久评分一次".
 */
public enum CalcFreqEnum {

    /** 每日：默认 cron "0 0 2 * * ?". */
    DAY,
    /** 每周：默认 cron "0 0 2 ? * MON". */
    WEEK,
    /** 每月：默认 cron "0 0 2 1 * ?". */
    MONTH,
    /** 每季度：默认 cron "0 0 2 1 1,4,7,10 ?". */
    QUARTER,
    /** 每年：默认 cron "0 0 2 1 1 ?". */
    YEAR;

    /**
     * 校验取值是否合法（大小写不敏感）.
     *
     * @param v 待校验字符串
     * @return true 表示合法
     */
    public static boolean isValid(String v) {
        if (v == null || v.isBlank()) return false;
        for (CalcFreqEnum e : values()) {
            if (e.name().equalsIgnoreCase(v)) return true;
        }
        return false;
    }
}
