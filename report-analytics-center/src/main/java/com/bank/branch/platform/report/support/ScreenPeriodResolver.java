package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 大屏预设周期模板解析器.
 *
 * <p>把 period（LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE）解析为日期范围：
 * latestOnly=true 时宽表/KPI 走"最新一条"查询形态（from/to 仍填 today，供自定义 SQL 占位取值）；
 * eomOnly=true 时宽表追加"仅月末时点"过滤。
 */
public final class ScreenPeriodResolver {

    private ScreenPeriodResolver() {
    }

    /** 解析结果 */
    public record ResolvedPeriod(LocalDate from, LocalDate to, boolean latestOnly, boolean eomOnly) {
    }

    public static ResolvedPeriod resolve(String period, String dateFrom, String dateTo, LocalDate today) {
        String p = (period == null || period.isBlank()) ? "LATEST" : period;
        switch (p) {
            case "LATEST":
                return new ResolvedPeriod(today, today, true, false);
            case "LAST_10D":
                return new ResolvedPeriod(today.minusDays(9), today, false, false);
            case "LAST_1M":
                return new ResolvedPeriod(today.minusMonths(1), today, false, false);
            case "LAST_6M_EOM":
                return new ResolvedPeriod(today.minusMonths(6).withDayOfMonth(1), today, false, true);
            case "RANGE":
                // 周期参数非法均归入 43011（入参校验失败），与 SQL 执行失败 43008 语义分离
                if (dateFrom == null || dateTo == null) {
                    throw new RptException(RptErrorCode.SCREEN_PERIOD_INVALID);
                }
                LocalDate f;
                LocalDate t;
                try {
                    f = LocalDate.parse(dateFrom);
                    t = LocalDate.parse(dateTo);
                } catch (DateTimeParseException e) {
                    throw new RptException(RptErrorCode.SCREEN_PERIOD_INVALID, e);
                }
                if (f.isAfter(t)) {
                    throw new RptException(RptErrorCode.SCREEN_PERIOD_INVALID);
                }
                return new ResolvedPeriod(f, t, false, false);
            default:
                throw new RptException(RptErrorCode.SCREEN_PERIOD_INVALID);
        }
    }
}
