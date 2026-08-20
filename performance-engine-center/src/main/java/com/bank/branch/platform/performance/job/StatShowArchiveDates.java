package com.bank.branch.platform.performance.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 统计展示表归档日期纯函数。
 *
 * <p>历史表归属以「运行日」为准：1~10 日写 {@code _H1}，11~20 日写 {@code _H2}，
 * 21 日至月末写 {@code _H3}。指标计算传入的是数据日（T-1），因此数据日路由必须
 * 先加一天还原归档运行日。
 */
public final class StatShowArchiveDates {

    private static final DateTimeFormatter DT = DateTimeFormatter.ISO_LOCAL_DATE;

    private StatShowArchiveDates() {
    }

    /** 按归档运行日选择历史表后缀。 */
    public static String histSuffixForRunDate(LocalDate runDate) {
        int day = runDate.getDayOfMonth();
        if (day <= 10) {
            return "_H1";
        }
        if (day <= 20) {
            return "_H2";
        }
        return "_H3";
    }

    /**
     * 按指标数据日选择历史表后缀。
     *
     * <p>归档任务在运行日写入前一日数据，所以 dataDate+1 才是旬归属运行日。
     */
    public static String histSuffixForDataDate(LocalDate dataDate) {
        return histSuffixForRunDate(dataDate.plusDays(1));
    }

    /** 兼容旧调用名：参数语义为运行日。 */
    public static String histSuffix(LocalDate runDate) {
        return histSuffixForRunDate(runDate);
    }

    /** 运行日是否为旬切换日。 */
    public static boolean isBoundaryRunDate(LocalDate runDate) {
        int day = runDate.getDayOfMonth();
        return day == 1 || day == 11 || day == 21;
    }

    /** yyyy-MM-dd。 */
    public static String fmt(LocalDate date) {
        return date.format(DT);
    }
}
