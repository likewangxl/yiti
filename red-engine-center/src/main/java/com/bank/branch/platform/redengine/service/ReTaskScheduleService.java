package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.redengine.api.dto.ReTaskCycleType;
import com.bank.branch.platform.redengine.api.dto.ReTaskScheduleWindowDTO;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

/**
 * 红色引擎任务周期领域服务。
 * <p>周期边界固定按北京时间自然日处理：周期开始和结束日期均包含；周期末从末日向前
 * 倒推，周期初从首日向后顺推。该类只负责纯日期规则，不负责 Quartz、数据库或任务状态
 * 写入，便于在任务生成和漏跑补偿中复用并独立测试。</p>
 */
@Service
public class ReTaskScheduleService {

    /**
     * 计算给定自然日所在周期的有效窗口。
     *
     * @param cycleType 周期类型，不能为 null 或 NONE
     * @param periodDate 周期定位日期
     * @param durationDays 窗口持续天数，按自然日首尾包含计算
     * @return 周期窗口
     * @throws IllegalArgumentException 周期、日期或持续时间不合法
     */
    public ReTaskScheduleWindowDTO calculateWindow(ReTaskCycleType cycleType,
                                                    LocalDate periodDate,
                                                    int durationDays) {
        validateArguments(cycleType, periodDate, durationDays);

        LocalDate periodStart = periodStart(cycleType, periodDate);
        LocalDate periodEnd = periodEnd(cycleType, periodDate);
        long periodLength = periodEnd.toEpochDay() - periodStart.toEpochDay() + 1;
        if (durationDays > periodLength) {
            throw new IllegalArgumentException("任务持续时间不能超过周期长度");
        }

        LocalDate windowStart;
        LocalDate windowEnd;
        if (cycleType.isStartCycle()) {
            windowStart = periodStart;
            windowEnd = periodStart.plusDays(durationDays - 1L);
        } else {
            windowStart = periodEnd.minusDays(durationDays - 1L);
            windowEnd = periodEnd;
        }

        ReTaskScheduleWindowDTO result = new ReTaskScheduleWindowDTO();
        result.setCycleType(cycleType);
        result.setPeriodKey(periodKey(cycleType, periodDate));
        result.setStartDate(windowStart);
        result.setEndDate(windowEnd);
        return result;
    }

    /**
     * 判断日期是否属于当前有效窗口。
     * <p>Quartz 漏跑补偿只能在该方法返回 true 时生成当前实例，避免补发历史窗口。</p>
     *
     * @param window 已计算的周期窗口
     * @param date 待判断日期
     * @return 在窗口内返回 true
     */
    public boolean isCurrentWindow(ReTaskScheduleWindowDTO window, LocalDate date) {
        return window != null && window.contains(date);
    }

    private void validateArguments(ReTaskCycleType cycleType, LocalDate periodDate, int durationDays) {
        if (cycleType == null || cycleType == ReTaskCycleType.NONE) {
            throw new IllegalArgumentException("定时任务必须配置有效周期");
        }
        if (periodDate == null) {
            throw new IllegalArgumentException("周期定位日期不能为空");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("任务持续时间必须大于0");
        }
    }

    private LocalDate periodStart(ReTaskCycleType cycleType, LocalDate date) {
        return switch (cycleType) {
            case WEEK_START, WEEK_END -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH_START, MONTH_END -> date.withDayOfMonth(1);
            case QUARTER_START, QUARTER_END -> quarterStart(date);
            case NONE -> throw new IllegalArgumentException("非周期任务没有周期窗口");
        };
    }

    private LocalDate periodEnd(ReTaskCycleType cycleType, LocalDate date) {
        return switch (cycleType) {
            case WEEK_START, WEEK_END -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTH_START, MONTH_END -> YearMonth.from(date).atEndOfMonth();
            case QUARTER_START, QUARTER_END -> quarterEnd(date);
            case NONE -> throw new IllegalArgumentException("非周期任务没有周期窗口");
        };
    }

    private LocalDate quarterStart(LocalDate date) {
        int firstMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), firstMonth, 1);
    }

    private LocalDate quarterEnd(LocalDate date) {
        return quarterStart(date).plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
    }

    private String periodKey(ReTaskCycleType cycleType, LocalDate date) {
        if (cycleType == ReTaskCycleType.WEEK_START || cycleType == ReTaskCycleType.WEEK_END) {
            LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int weekBasedYear = weekStart.get(WeekFields.ISO.weekBasedYear());
            int weekOfYear = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
            return String.format("%d-W%02d", weekBasedYear, weekOfYear);
        }
        if (cycleType == ReTaskCycleType.MONTH_START || cycleType == ReTaskCycleType.MONTH_END) {
            return String.format("%d-%02d", date.getYear(), date.getMonthValue());
        }
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return String.format("%d-Q%d", date.getYear(), quarter);
    }
}
