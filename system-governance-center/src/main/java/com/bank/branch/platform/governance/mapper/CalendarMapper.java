package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.SysCalendarDay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历 Mapper 接口，操作 sys_calendar_day 表。
 * <p>
 * 日历表以 day（DATE）为自然主键，存储每天的工作日/休息日状态。
 * </p>
 * <p>
 * insert / updateById 由 MyBatis-Plus BaseMapper 提供。
 * selectByDay 与 BaseMapper.selectById(Serializable) 签名不同（参数类型 LocalDate），保留自定义实现。
 * </p>
 */
@Mapper
public interface CalendarMapper extends BaseMapper<SysCalendarDay> {

    /**
     * 根据日期查询单条日历记录。
     *
     * @param day 日期
     * @return 日历实体，不存在时返回 null
     */
    SysCalendarDay selectByDay(LocalDate day);

    /**
     * 查询指定日期范围内的日历记录，按日期升序排列。
     *
     * @param from 起始日期（包含）
     * @param to   结束日期（包含）
     * @return 日历记录列表
     */
    List<SysCalendarDay> selectByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 查询指定年份的所有日历记录，按日期升序排列。
     *
     * @param year 年份
     * @return 日历记录列表
     */
    List<SysCalendarDay> selectByYear(@Param("year") int year);

    /**
     * 统计指定日期范围内的工作日天数（is_workday=1）。
     *
     * @param from 起始日期（包含）
     * @param to   结束日期（包含）
     * @return 工作日天数
     */
    int countWorkingDays(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 判断指定日期是否已存在日历记录。
     *
     * @param day 日期
     * @return 存在返回 true，否则返回 false
     */
    boolean existsByDay(LocalDate day);

    /** 按日期范围删除（年初初始化重置用） */
    int deleteByRange(@org.apache.ibatis.annotations.Param("start") LocalDate start,
                      @org.apache.ibatis.annotations.Param("end") LocalDate end);
}
