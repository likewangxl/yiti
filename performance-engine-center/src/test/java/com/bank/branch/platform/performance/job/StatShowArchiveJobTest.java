package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StatShowArchiveJob 编排单测：tmp 单日归档、边界 truncate、1 号主表幂等写入。 */
@ExtendWith(MockitoExtension.class)
class StatShowArchiveJobTest {

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";

    @Mock
    private StatShowArchiveMapper mapper;
    @InjectMocks
    private StatShowArchiveJob job;

    @Test
    void ordinaryDay_deletesTargetDateThenCopiesTMinusOneFromTmp() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(2);

        int inserted = job.run(LocalDate.of(2026, 8, 15)); // dataDate=08-14, run day 15 -> H2

        assertThat(inserted).isEqualTo(4);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteByTableDate(CUST + "_H2", "2026-08-14");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_tmp", "2026-08-14");
        order.verify(mapper).deleteByTableDate(EMP + "_H2", "2026-08-14");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H2", EMP + "_tmp", "2026-08-14");
        verify(mapper, never()).truncateTable(anyString());
        verify(mapper, never()).deleteByTableDate(eq(CUST), anyString());
        verify(mapper, never()).deleteByTableDate(eq(EMP), anyString());
    }

    @Test
    void boundaryEleventh_truncatesCurrentHThenCopiesOnlyTMinusOne() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(3);

        job.run(LocalDate.of(2026, 8, 11)); // dataDate=08-10, run day 11 -> H2

        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H2");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H2", CUST + "_tmp", "2026-08-10");
        order.verify(mapper).truncateTable(EMP + "_H2");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H2", EMP + "_tmp", "2026-08-10");
        verify(mapper, never()).deleteByTableDate(eq(CUST + "_H2"), anyString());
        verify(mapper, never()).deleteByTableDate(eq(EMP + "_H2"), anyString());
    }

    @Test
    void boundaryTwentyFirst_truncatesH3() {
        job.run(LocalDate.of(2026, 8, 21)); // dataDate=08-20, run day 21 -> H3

        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H3");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H3", CUST + "_tmp", "2026-08-20");
        order.verify(mapper).truncateTable(EMP + "_H3");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H3", EMP + "_tmp", "2026-08-20");
    }

    @Test
    void firstDay_truncatesH1AndUpsertsPreviousMonthEndIntoMain() {
        when(mapper.insertFromTmpByDate(anyString(), anyString(), anyString())).thenReturn(4);

        int inserted = job.run(LocalDate.of(2026, 8, 1)); // dataDate=07-31, run day 1 -> H1

        assertThat(inserted).isEqualTo(16);
        InOrder order = inOrder(mapper);
        order.verify(mapper).truncateTable(CUST + "_H1");
        order.verify(mapper).insertFromTmpByDate(CUST + "_H1", CUST + "_tmp", "2026-07-31");
        order.verify(mapper).deleteByTableDate(CUST, "2026-07-31");
        order.verify(mapper).insertFromTmpByDate(CUST, CUST + "_tmp", "2026-07-31");
        order.verify(mapper).truncateTable(EMP + "_H1");
        order.verify(mapper).insertFromTmpByDate(EMP + "_H1", EMP + "_tmp", "2026-07-31");
        order.verify(mapper).deleteByTableDate(EMP, "2026-07-31");
        order.verify(mapper).insertFromTmpByDate(EMP, EMP + "_tmp", "2026-07-31");
    }
}
