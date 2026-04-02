package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.entity.SysCalendarDay;
import com.bank.branch.platform.governance.mapper.CalendarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 日历服务单元测试
 * TDD RED 阶段：先编写测试，验证编译失败后再实现业务代码
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;
    @Mock
    ValueOperations<String, Object> valueOperations;
    @Mock
    CalendarMapper calendarMapper;
    @InjectMocks
    CalendarService calendarService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── 1. isWorkingDay ──────────────────────────────────────────

    /**
     * 测试工作日判断：mapper 返回 isWorkday=1 时应返回 true
     */
    @Test
    void isWorkingDay_returnsTrue_forWorkday() {
        LocalDate date = LocalDate.of(2026, 4, 6); // 周一
        SysCalendarDay day = makeCalendarDay(date, 1, null);
        when(calendarMapper.selectByDay(date)).thenReturn(day);

        boolean result = calendarService.isWorkingDay(date);

        assertThat(result).isTrue();
        verify(calendarMapper).selectByDay(date);
    }

    /**
     * 测试休息日判断：mapper 返回 isWorkday=0 时应返回 false
     */
    @Test
    void isWorkingDay_returnsFalse_forHoliday() {
        LocalDate date = LocalDate.of(2026, 5, 1); // 劳动节
        SysCalendarDay day = makeCalendarDay(date, 0, "劳动节");
        when(calendarMapper.selectByDay(date)).thenReturn(day);

        boolean result = calendarService.isWorkingDay(date);

        assertThat(result).isFalse();
    }

    // ── 2. countWorkingDays ──────────────────────────────────────

    /**
     * 测试工作日计数：验证委托给 mapper 的 countWorkingDays 方法
     */
    @Test
    void countWorkingDays_delegatesToMapper() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);
        when(calendarMapper.countWorkingDays(from, to)).thenReturn(22);

        int result = calendarService.countWorkingDays(from, to);

        assertThat(result).isEqualTo(22);
        verify(calendarMapper).countWorkingDays(from, to);
    }

    // ── 3. addWorkingDays ────────────────────────────────────────

    /**
     * 测试工作日推算：跳过非工作日
     * 场景：从周一(4/6)开始加 5 个工作日（从起始日的下一天开始计数），
     * 若周三(4/8)是假日，则需要多跳一天，结果应为下周二(4/14)
     * 计数序列: 4/7(1), 4/9(2), 4/10(3), 4/13(4), 4/14(5)
     */
    @Test
    void addWorkingDays_skipsNonWorkingDays() {
        // 2026-04-06 周一(起始), 04-07 周二, 04-08 周三(假日), 04-09 周四, 04-10 周五
        // 04-11 周六, 04-12 周日, 04-13 周一, 04-14 周二
        LocalDate start = LocalDate.of(2026, 4, 6);
        List<SysCalendarDay> rangeData = List.of(
                makeCalendarDay(LocalDate.of(2026, 4, 6), 1, null),
                makeCalendarDay(LocalDate.of(2026, 4, 7), 1, null),
                makeCalendarDay(LocalDate.of(2026, 4, 8), 0, "假日"),
                makeCalendarDay(LocalDate.of(2026, 4, 9), 1, null),
                makeCalendarDay(LocalDate.of(2026, 4, 10), 1, null),
                makeCalendarDay(LocalDate.of(2026, 4, 11), 0, null),
                makeCalendarDay(LocalDate.of(2026, 4, 12), 0, null),
                makeCalendarDay(LocalDate.of(2026, 4, 13), 1, null),
                makeCalendarDay(LocalDate.of(2026, 4, 14), 1, null)
        );
        when(calendarMapper.selectByDateRange(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rangeData);

        LocalDate result = calendarService.addWorkingDays(start, 5);

        assertThat(result).isEqualTo(LocalDate.of(2026, 4, 14));
    }

    // ── 4. toggleWorkday ─────────────────────────────────────────

    /**
     * 测试切换工作日状态：过去日期应抛出 GOV-40301
     */
    @Test
    void toggleWorkday_rejectsPastDate_throwsGov40301() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);

        assertThatThrownBy(() -> calendarService.toggleWorkday(pastDate))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40301"));
    }

    /**
     * 测试切换工作日状态：将 isWorkday=1 翻转为 0，并验证 updateById 被调用
     */
    @Test
    void toggleWorkday_flipsWorkdayFlag() {
        // 使用一个足够远的未来日期，确保不会因为"过去日期"被拒绝
        LocalDate futureDate = LocalDate.now().plusDays(30);
        SysCalendarDay existing = makeCalendarDay(futureDate, 1, null);
        when(calendarMapper.selectByDay(futureDate)).thenReturn(existing);
        when(calendarMapper.updateById(any())).thenReturn(1);

        calendarService.toggleWorkday(futureDate);

        verify(calendarMapper).updateById(argThat(day -> day.getIsWorkday() == 0));
        // 验证缓存被清除
        verify(redisTemplate).delete("gov:calendar:" + futureDate.getYear());
    }

    // ── 5. initYear ──────────────────────────────────────────────

    /**
     * 测试年初初始化：幂等性——已存在的日期不重复插入
     */
    @Test
    void initYear_idempotent_skipsExistingDates() {
        int year = 2026;
        // 模拟：1月1日已存在，1月2日不存在
        when(calendarMapper.existsByDay(LocalDate.of(2026, 1, 1))).thenReturn(true);
        when(calendarMapper.existsByDay(argThat(d ->
                d != null && !d.equals(LocalDate.of(2026, 1, 1))
        ))).thenReturn(false);
        when(calendarMapper.insert(any())).thenReturn(1);

        calendarService.initYear(year);

        // 1月1日不应被插入（因为已存在）
        verify(calendarMapper, never()).insert(argThat(day ->
                day.getDay().equals(LocalDate.of(2026, 1, 1))
        ));
        // 1月2日应被插入
        verify(calendarMapper).insert(argThat(day ->
                day.getDay().equals(LocalDate.of(2026, 1, 2))
        ));
        // 验证缓存被清除
        verify(redisTemplate).delete("gov:calendar:" + year);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    private SysCalendarDay makeCalendarDay(LocalDate day, int isWorkday, String remark) {
        SysCalendarDay calendarDay = new SysCalendarDay();
        calendarDay.setDay(day);
        calendarDay.setIsWorkday(isWorkday);
        calendarDay.setRemark(remark);
        calendarDay.setCreatedTime(LocalDateTime.now());
        calendarDay.setUpdatedTime(LocalDateTime.now());
        return calendarDay;
    }
}
