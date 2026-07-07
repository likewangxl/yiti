package com.bank.branch.platform.performance.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 统计展示表旬度归档「日期逻辑」纯计算器（无副作用、无依赖，便于单测）.
 *
 * <p>旬归属：1~10→_H2，11~20→_H3，21~月末→_H1。日期一律 yyyy-MM-dd 定宽字符串。
 */
public final class StatShowArchiveDates {

    private static final DateTimeFormatter DT = DateTimeFormatter.ISO_LOCAL_DATE;

    private StatShowArchiveDates() {
    }

    /** 边界清理区间：历史表后缀 + 闭区间 [start, end]. */
    public record CleanupRange(String histSuffix, LocalDate start, LocalDate end) {
    }

    /** 主表瘦身月区间：闭区间 [start, end]. */
    public record MonthRange(LocalDate start, LocalDate end) {
    }

    /** 某日所属旬的历史表后缀（_H2 / _H3 / _H1）. */
    public static String histSuffix(LocalDate d) {
        int day = d.getDayOfMonth();
        if (day <= 10) {
            return "_H2";
        }
        if (day <= 20) {
            return "_H3";
        }
        return "_H1";
    }

    /** 某日所属旬的旬首日（1 / 11 / 21 号）. */
    public static LocalDate sliceStart(LocalDate d) {
        int day = d.getDayOfMonth();
        if (day <= 10) {
            return d.withDayOfMonth(1);
        }
        if (day <= 20) {
            return d.withDayOfMonth(11);
        }
        return d.withDayOfMonth(21);
    }

    /**
     * 旬边界清理区间：仅当 today 为 1/11/21 号返回「上一代」旧旬；否则 empty.
     * 11→上月1~10(_H2)；21→上月11~20(_H3)；1→上上月21~末(_H1，因该旬跨月上一代前推两月).
     */
    public static Optional<CleanupRange> boundaryCleanup(LocalDate today) {
        return switch (today.getDayOfMonth()) {
            case 11 -> {
                LocalDate pm = today.minusMonths(1);
                yield Optional.of(new CleanupRange("_H2", pm.withDayOfMonth(1), pm.withDayOfMonth(10)));
            }
            case 21 -> {
                LocalDate pm = today.minusMonths(1);
                yield Optional.of(new CleanupRange("_H3", pm.withDayOfMonth(11), pm.withDayOfMonth(20)));
            }
            case 1 -> {
                LocalDate p2 = today.minusMonths(2);
                yield Optional.of(new CleanupRange("_H1", p2.withDayOfMonth(21), monthEnd(p2)));
            }
            default -> Optional.empty();
        };
    }

    /** 主表瘦身月：仅 today 为 1 号返回上月整月区间；否则 empty. */
    public static Optional<MonthRange> pruneMonth(LocalDate today) {
        if (today.getDayOfMonth() != 1) {
            return Optional.empty();
        }
        LocalDate pm = today.minusMonths(1);
        return Optional.of(new MonthRange(pm.withDayOfMonth(1), monthEnd(pm)));
    }

    /** 闭区间 [start, end] 的所有日期，升序. */
    public static List<LocalDate> datesInclusive(LocalDate start, LocalDate end) {
        List<LocalDate> list = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            list.add(d);
        }
        return list;
    }

    /** yyyy-MM-dd. */
    public static String fmt(LocalDate d) {
        return d.format(DT);
    }

    private static LocalDate monthEnd(LocalDate m) {
        return m.withDayOfMonth(m.lengthOfMonth());
    }
}
