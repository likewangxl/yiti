package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.CalendarApi;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历服务 Facade 实现
 * <p>
 * 实现 CalendarApi 接口，委托给 CalendarService 完成业务逻辑。
 * 所有方法均为同步调用，缓存由 CalendarService 内部管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarFacade implements CalendarApi {

    private final CalendarService calendarService;

    /**
     * 判断指定日期是否工作日
     *
     * @param date 日期
     * @return true=工作日, false=休息日
     */
    @Override
    public boolean isWorkingDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date 不能为 null");
        }
        return calendarService.isWorkingDay(date);
    }

    /**
     * 计算两个日期之间的工作日天数
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 工作日天数
     */
    @Override
    public int countWorkingDays(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from 和 to 不能为 null");
        }
        if (!from.isBefore(to)) {
            return 0;
        }
        return calendarService.countWorkingDays(from, to);
    }

    /**
     * 获取某年全部日历数据
     *
     * @param year 年份
     * @return 日历天 DTO 列表
     */
    @Override
    public List<CalendarDayDTO> getWorkingDays(int year) {
        return calendarService.getWorkingDays(year);
    }

    /**
     * 从指定日期开始，推算 N 个工作日后的日期
     *
     * @param from        起始日期
     * @param workingDays 要推算的工作日天数
     * @return 推算后的日期
     */
    @Override
    public LocalDate addWorkingDays(LocalDate from, int workingDays) {
        if (from == null) {
            throw new IllegalArgumentException("from 不能为 null");
        }
        if (workingDays == 0) {
            throw new IllegalArgumentException("workingDays 不能为 0");
        }
        return calendarService.addWorkingDays(from, workingDays);
    }
}
