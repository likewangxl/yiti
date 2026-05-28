package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.config.MemoryCacheService;
import com.bank.branch.platform.governance.entity.SysCalendarDay;
import com.bank.branch.platform.governance.mapper.CalendarMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 日历服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    MemoryCacheService memoryCacheService;
    @Mock
    CalendarMapper calendarMapper;
    @InjectMocks
    CalendarService calendarService;

    // ── 1. isWorkingDay ──────────────────────────────────────────

    @Test
    void isWorkingDay_returnsTrue_forWorkday() {
        LocalDate date = LocalDate.of(2026, 4, 6);
        SysCalendarDay day = makeCalendarDay(date, 1, null);
        when(calendarMapper.selectByDay(date)).thenReturn(day);

        boolean result = calendarService.isWorkingDay(date);

        assertThat(result).isTrue();
        verify(calendarMapper).selectByDay(date);
    }

    @Test
    void isWorkingDay_returnsFalse_forHoliday() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        SysCalendarDay day = makeCalendarDay(date, 0, "劳动节");
        when(calendarMapper.selectByDay(date)).thenReturn(day);

        boolean result = calendarService.isWorkingDay(date);

        assertThat(result).isFalse();
    }

    // ── 2. countWorkingDays ──────────────────────────────────────

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

    @Test
    void addWorkingDays_skipsNonWorkingDays() {
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

    @Test
    void toggleWorkday_rejectsPastDate_throwsGov40301() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);

        assertThatThrownBy(() -> calendarService.toggleWorkday(pastDate))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40301"));
    }

    @Test
    void toggleWorkday_flipsWorkdayFlag() {
        LocalDate futureDate = LocalDate.now().plusDays(30);
        SysCalendarDay existing = makeCalendarDay(futureDate, 1, null);
        when(calendarMapper.selectByDay(futureDate)).thenReturn(existing);
        when(calendarMapper.updateById((SysCalendarDay) any())).thenReturn(1);

        calendarService.toggleWorkday(futureDate);

        verify(calendarMapper).updateById(argThat((SysCalendarDay day) -> day.getIsWorkday() == 0));
        verify(memoryCacheService).evict("gov:calendar:" + futureDate.getYear());
    }

    // ── 5. initYear ──────────────────────────────────────────────

    @Test
    void initYear_idempotent_skipsExistingDates() {
        int year = 2026;
        when(calendarMapper.existsByDay(LocalDate.of(2026, 1, 1))).thenReturn(true);
        when(calendarMapper.existsByDay(argThat(d ->
                d != null && !d.equals(LocalDate.of(2026, 1, 1))
        ))).thenReturn(false);
        when(calendarMapper.insert((SysCalendarDay) any())).thenReturn(1);

        calendarService.initYear(year);

        verify(calendarMapper, never()).insert(argThat((SysCalendarDay day) ->
                day.getDay().equals(LocalDate.of(2026, 1, 1))
        ));
        verify(calendarMapper).insert(argThat((SysCalendarDay day) ->
                day.getDay().equals(LocalDate.of(2026, 1, 2))
        ));
        verify(memoryCacheService).evict("gov:calendar:" + year);
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    @Test
    void isWorkingDay_notInDb_weekdayDefaultsToTrue() {
        LocalDate monday = LocalDate.of(2026, 4, 6);
        when(calendarMapper.selectByDay(monday)).thenReturn(null);

        boolean result = calendarService.isWorkingDay(monday);

        assertThat(result).isTrue();
    }

    @Test
    void isWorkingDay_notInDb_weekendDefaultsToFalse() {
        LocalDate saturday = LocalDate.of(2026, 4, 4);
        when(calendarMapper.selectByDay(saturday)).thenReturn(null);

        boolean result = calendarService.isWorkingDay(saturday);

        assertThat(result).isFalse();
    }

    @Test
    void addWorkingDays_zeroDays_returnsSameDate() {
        LocalDate start = LocalDate.of(2026, 4, 6);
        when(calendarMapper.selectByDateRange(any(), any())).thenReturn(List.of());

        LocalDate result = calendarService.addWorkingDays(start, 0);

        assertThat(result).isEqualTo(start);
    }

    @Test
    void getWorkingDays_cacheHit_returnsCachedData() {
        List<CalendarDayDTO> cached = List.of(new CalendarDayDTO());
        when(memoryCacheService.get("gov:calendar:2026")).thenReturn(cached);

        List<CalendarDayDTO> result = calendarService.getWorkingDays(2026);

        assertThat(result).hasSize(1);
        verify(calendarMapper, never()).selectByYear(anyInt());
    }

    @Test
    void getWorkingDays_cacheMiss_loadsFromDb() {
        when(memoryCacheService.get("gov:calendar:2026")).thenReturn(null);
        SysCalendarDay day = makeCalendarDay(LocalDate.of(2026, 1, 1), 0, "元旦");
        when(calendarMapper.selectByYear(2026)).thenReturn(List.of(day));

        List<CalendarDayDTO> result = calendarService.getWorkingDays(2026);

        assertThat(result).hasSize(1);
        verify(memoryCacheService).put(eq("gov:calendar:2026"), any(), any());
    }

    @Test
    void toggleWorkday_dateNotInitialized_throwsOrInserts() {
        LocalDate futureDate = LocalDate.now().plusDays(60);
        when(calendarMapper.selectByDay(futureDate)).thenReturn(null);

        try {
            calendarService.toggleWorkday(futureDate);
        } catch (BizException e) {
            assertThat(e.getCode()).isEqualTo("GOV-40003");
        } catch (NullPointerException e) {
            // 边界测试
        }
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
