package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.redengine.api.dto.ReTaskCycleType;
import com.bank.branch.platform.redengine.api.dto.ReTaskScheduleWindowDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReTaskScheduleServiceTest {

    private final ReTaskScheduleService service = new ReTaskScheduleService();

    @Test
    void monthEndWindowCountsBackwardsWithBothEndpoints() {
        ReTaskScheduleWindowDTO window = service.calculateWindow(
                ReTaskCycleType.MONTH_END,
                LocalDate.of(2024, 2, 10),
                5);

        assertEquals(LocalDate.of(2024, 2, 25), window.getStartDate());
        assertEquals(LocalDate.of(2024, 2, 29), window.getEndDate());
        assertEquals("2024-02", window.getPeriodKey());
    }

    @Test
    void monthStartWindowCountsForwardWithBothEndpoints() {
        ReTaskScheduleWindowDTO window = service.calculateWindow(
                ReTaskCycleType.MONTH_START,
                LocalDate.of(2024, 2, 20),
                5);

        assertEquals(LocalDate.of(2024, 2, 1), window.getStartDate());
        assertEquals(LocalDate.of(2024, 2, 5), window.getEndDate());
    }

    @Test
    void quarterEndWindowCountsBackwards() {
        ReTaskScheduleWindowDTO window = service.calculateWindow(
                ReTaskCycleType.QUARTER_END,
                LocalDate.of(2024, 5, 10),
                5);

        assertEquals(LocalDate.of(2024, 6, 26), window.getStartDate());
        assertEquals(LocalDate.of(2024, 6, 30), window.getEndDate());
        assertEquals("2024-Q2", window.getPeriodKey());
    }

    @Test
    void quarterStartWindowCountsForward() {
        ReTaskScheduleWindowDTO window = service.calculateWindow(
                ReTaskCycleType.QUARTER_START,
                LocalDate.of(2024, 6, 30),
                5);

        assertEquals(LocalDate.of(2024, 4, 1), window.getStartDate());
        assertEquals(LocalDate.of(2024, 4, 5), window.getEndDate());
    }

    @Test
    void weekWindowsUseMondayAndSundayAsNaturalBoundaries() {
        ReTaskScheduleWindowDTO start = service.calculateWindow(
                ReTaskCycleType.WEEK_START,
                LocalDate.of(2024, 1, 3),
                3);
        ReTaskScheduleWindowDTO end = service.calculateWindow(
                ReTaskCycleType.WEEK_END,
                LocalDate.of(2024, 1, 3),
                3);

        assertEquals(LocalDate.of(2024, 1, 1), start.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 3), start.getEndDate());
        assertEquals(LocalDate.of(2024, 1, 5), end.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 7), end.getEndDate());
    }

    @Test
    void durationCannotExceedTheCycleLength() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateWindow(
                ReTaskCycleType.MONTH_START,
                LocalDate.of(2024, 2, 20),
                30));
        assertThrows(IllegalArgumentException.class, () -> service.calculateWindow(
                ReTaskCycleType.QUARTER_END,
                LocalDate.of(2024, 5, 20),
                0));
    }

    @Test
    void nonPeriodicCycleAndMissingDateAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateWindow(
                ReTaskCycleType.NONE,
                LocalDate.of(2024, 2, 20),
                1));
        assertThrows(IllegalArgumentException.class, () -> service.calculateWindow(
                ReTaskCycleType.MONTH_START,
                null,
                1));
    }

    @Test
    void currentWindowCheckIsInclusiveAndDoesNotBackfillOutsideWindow() {
        ReTaskScheduleWindowDTO window = service.calculateWindow(
                ReTaskCycleType.MONTH_END,
                LocalDate.of(2024, 2, 20),
                5);

        assertTrue(service.isCurrentWindow(window, LocalDate.of(2024, 2, 25)));
        assertTrue(service.isCurrentWindow(window, LocalDate.of(2024, 2, 29)));
        assertFalse(service.isCurrentWindow(window, LocalDate.of(2024, 2, 24)));
        assertFalse(service.isCurrentWindow(window, LocalDate.of(2024, 3, 1)));
    }
}
