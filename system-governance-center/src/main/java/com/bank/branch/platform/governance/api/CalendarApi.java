package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历对外API
 * <p>
 * 提供工作日判断与工作日计算能力，供其他业务模块通过 Spring Bean 注入调用。
 * 高频调用，内部启用 Redis 缓存（TTL 24小时）。
 * </p>
 */
public interface CalendarApi {

    /**
     * 判断指定日期是否工作日
     *
     * @param date 日期
     * @return true=工作日, false=休息日
     * @throws IllegalArgumentException date 为 null 时
     */
    boolean isWorkingDay(LocalDate date);

    /**
     * 计算两个日期之间的工作日天数
     * 用于工作流红绿灯超时计算
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 工作日天数，from >= to 时返回 0
     * @throws IllegalArgumentException from 或 to 为 null 时
     */
    int countWorkingDays(LocalDate from, LocalDate to);

    /**
     * 获取某年全部日历数据
     * 用于绩效计算窗口
     *
     * @param year 年份（如 2026）
     * @return 该年所有日历天的 DTO 列表
     */
    List<CalendarDayDTO> getWorkingDays(int year);

    /**
     * 从指定日期开始，推算 N 个工作日后的日期
     * 用于工作流截止日期计算
     *
     * @param from        起始日期
     * @param workingDays 要推算的工作日天数（正数向后）
     * @return 推算后的日期
     * @throws IllegalArgumentException from 为 null 或 workingDays 为 0 时
     */
    LocalDate addWorkingDays(LocalDate from, int workingDays);
}
