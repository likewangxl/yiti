package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatShowArchiveJob 编排单测（Mockito）：日增量幂等、旬边界按天清理、1 号按天瘦身、两表处理。
 */
@ExtendWith(MockitoExtension.class)
class StatShowArchiveJobTest {

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";

    @Mock
    private StatShowArchiveMapper mapper;
    @InjectMocks
    private StatShowArchiveJob job;

    // 昨天=2026-08-14（属 11~20 旬→_H3），旬首=08-11；应逐日 sync 08-11..08-14
    @Test
    void daily_gapFillsSliceStartToYesterday_intoRoutedHist() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(100L); // main
        when(mapper.countByTableDate(eq(CUST + "_H3"), anyString())).thenReturn(0L);
        when(mapper.countByTableDate(eq(EMP + "_H3"), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15)); // 昨天 08-14

        for (String main : new String[]{CUST, EMP}) {
            String h3 = main + "_H3";
            for (String d : new String[]{"2026-08-11", "2026-08-12", "2026-08-13", "2026-08-14"}) {
                verify(mapper).deleteHistByDate(h3, d);
                verify(mapper).insertHistByDate(h3, main, d);
            }
        }
    }

    // 源未就绪：main count=0 → 跳过，不删不插
    @Test
    void daily_whenMainEmpty_skips() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).insertHistByDate(anyString(), anyString(), anyString());
        verify(mapper, never()).deleteHistByDate(anyString(), anyString());
    }

    // 已同步：hist count == main count → 跳过插入
    @Test
    void daily_whenAlreadySynced_skipsInsert() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(100L); // main 与 hist 都 100

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).insertHistByDate(anyString(), anyString(), anyString());
    }

    // 边界 11 号：按天分批清 _H2 的上月 1~10
    @Test
    void boundary_on11_deletesPrevMonth1To10_dayByDay() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L); // 让日增量跳过，聚焦清理
        job.run(LocalDate.of(2026, 8, 11));

        for (String main : new String[]{CUST, EMP}) {
            String h2 = main + "_H2";
            verify(mapper).deleteHistByDate(h2, "2026-07-01");
            verify(mapper).deleteHistByDate(h2, "2026-07-05");
            verify(mapper).deleteHistByDate(h2, "2026-07-10");
        }
    }

    // 边界 1 号：清 _H1 上上月 21~末（按天） + 主表瘦身
    @Test
    void boundary_on1_cleansH1TwoMonthsAgo_andPrunesMain() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);
        when(mapper.selectMaxStatisDt(anyString(), eq("2026-07-01"), eq("2026-07-31"))).thenReturn("2026-07-31");

        job.run(LocalDate.of(2026, 8, 1));

        for (String main : new String[]{CUST, EMP}) {
            verify(mapper).deleteHistByDate(main + "_H1", "2026-06-21");
            verify(mapper).deleteHistByDate(main + "_H1", "2026-06-30");
            verify(mapper).selectMaxStatisDt(main, "2026-07-01", "2026-07-31");
            verify(mapper).deleteMainByDate(main, "2026-07-01");
            verify(mapper).deleteMainByDate(main, "2026-07-30");
            verify(mapper, never()).deleteMainByDate(main, "2026-07-31"); // 保留月末
        }
    }

    // 瘦身保护：上月无数据(MAX null) → 不删主表
    @Test
    void prune_whenNoMax_skips() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);
        lenient().when(mapper.selectMaxStatisDt(anyString(), anyString(), anyString())).thenReturn(null);

        job.run(LocalDate.of(2026, 8, 1));

        verify(mapper, never()).deleteMainByDate(anyString(), anyString());
    }

    // 非边界日：不清理、不瘦身
    @Test
    void nonBoundaryDay_noCleanupNoPrune() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).deleteMainByDate(anyString(), anyString());
        verify(mapper, never()).selectMaxStatisDt(anyString(), anyString(), anyString());
    }
}
