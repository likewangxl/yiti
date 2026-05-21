package com.bank.branch.platform.performance.service.engine;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指标 SQL 日期宏解析器.
 *
 * <p>以 {@code dataDate} 为锚点，产出 8 个强类型 {@link LocalDate} 命名参数，
 * 供 SQL 类指标 sql_text 通过 {@code :dateXxx} 引用。
 *
 * <p>详见 spec {@code docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md}.
 */
public final class DateMacroResolver {

    private DateMacroResolver() {
    }

    /**
     * 按 base 日期解析 8 个日期宏（顺序与 spec §2 一致）.
     *
     * @param base 锚点日期（必填）
     * @return 有序 Map，key 即 SQL 中的 {@code :name}
     * @throws IllegalArgumentException base 为 null
     */
    public static Map<String, LocalDate> resolve(LocalDate base) {
        if (base == null) {
            throw new IllegalArgumentException("dataDate 不能为空");
        }
        Map<String, LocalDate> macros = new LinkedHashMap<>();
        macros.put("dateToday", base);
        macros.put("dateYesterday", base.minusDays(1));
        macros.put("dateMonthEnd", base.with(TemporalAdjusters.lastDayOfMonth()));
        macros.put("datePrevMonthEnd", base.withDayOfMonth(1).minusDays(1));
        int quarter = (base.getMonthValue() - 1) / 3 + 1;
        LocalDate firstDayOfQuarter = LocalDate.of(base.getYear(), (quarter - 1) * 3 + 1, 1);
        macros.put("dateQuarterEnd",
                firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
        macros.put("datePrevQuarterEnd", firstDayOfQuarter.minusDays(1));
        macros.put("dateYearEnd", LocalDate.of(base.getYear(), 12, 31));
        macros.put("datePrevYearEnd", LocalDate.of(base.getYear() - 1, 12, 31));
        return macros;
    }
}
