package com.bank.branch.platform.performance.enums;

import com.bank.branch.platform.performance.service.engine.DateMacroResolver;

import java.time.LocalDate;

/**
 * 引用指标取值时间枚举.
 *
 * <p>token 后缀 ↔ {@link DateMacroResolver} 宏 key ↔ 相对 dataDate 的锚点日期。
 * <p>TODAY 无后缀（默认）；其余为系统保留后缀，禁止真实指标编码以其结尾。
 */
public enum MetricValueTimeEnum {

    /** 今日（默认，无后缀）. */
    TODAY(null, "dateToday"),
    /** 昨日. */
    D1("D1", "dateYesterday"),
    /** 上月末. */
    PME("PME", "datePrevMonthEnd"),
    /** 上季末. */
    PQE("PQE", "datePrevQuarterEnd"),
    /** 上年末. */
    PYE("PYE", "datePrevYearEnd");

    private final String suffix;
    private final String macroKey;

    MetricValueTimeEnum(String suffix, String macroKey) {
        this.suffix = suffix;
        this.macroKey = macroKey;
    }

    /** token 后缀（TODAY 为 null）. */
    public String getSuffix() {
        return suffix;
    }

    /** 按后缀返回枚举；无后缀/不匹配返回 null（TODAY 有意不通过 suffix 匹配）. */
    public static MetricValueTimeEnum bySuffix(String suffix) {
        if (suffix == null) {
            return null;
        }
        for (MetricValueTimeEnum e : values()) {
            if (suffix.equals(e.suffix)) {
                return e;
            }
        }
        return null;
    }

    /** 以 dataDate 为锚点解析目标日期（复用 DateMacroResolver 已算好的锚点，零新增日期数学）. */
    public LocalDate resolve(LocalDate dataDate) {
        return DateMacroResolver.resolve(dataDate).get(macroKey);
    }
}
