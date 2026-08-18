package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 数据就绪协调器的状态机单测。 */
@ExtendWith(MockitoExtension.class)
class MetricCalcReadyCoordinatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);
    private static final LocalDate DATA_DATE = TODAY.minusDays(1);
    private static final String DT = "2026-08-17";

    @Mock
    private StatShowArchiveMapper archiveMapper;
    @Mock
    private PerfMetricCalcTaskMapper taskMapper;
    @Mock
    private JobApi jobApi;
    @Mock
    private MetricLevelTriggerService levelTriggerService;

    private MetricCalcReadyCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new MetricCalcReadyCoordinator(archiveMapper, taskMapper, jobApi,
                levelTriggerService);
        stubCounts(3, 3, 3, 3);
    }

    @Test
    void allTmpEmpty_doesNotTriggerAnyJob() {
        stubCounts(0, 0, 0, 0);

        coordinator.run(TODAY);

        verify(jobApi, never()).triggerJobByKey(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verifyNoTaskLookup();
    }

    @Test
    void countMismatch_triggersArchiveAndWaitsForNextRound() {
        stubCounts(5, 3, 5, 5);

        coordinator.run(TODAY);

        verify(jobApi).triggerJobByKey(eq("STAT_SHOW_ARCHIVE"), eq("AUTO"),
                eq("数据就绪同步：2026-08-17 临时表与历史表计数不一致"), eq(DT), isNull(), isNull());
        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verifyNoTaskLookup();
    }

    @Test
    void countMismatch_whileArchiveRunning_doesNotEnqueueAnotherArchive() {
        stubCounts(5, 3, 5, 5);
        when(jobApi.isJobRunning("STAT_SHOW_ARCHIVE")).thenReturn(true);

        coordinator.run(TODAY);

        verify(jobApi, never()).triggerJobByKey(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verifyNoTaskLookup();
    }

    @Test
    void countsEqual_triggersAtMostOneDownstreamLevelPerRound() {
        coordinator.run(TODAY);

        verify(levelTriggerService).trigger(1, DATA_DATE, null, null, "AUTO", null);
        verify(levelTriggerService, never()).trigger(eq(2), any(LocalDate.class), anyString(),
                any(), anyString(), anyString());
        verify(levelTriggerService, never()).trigger(eq(3), any(LocalDate.class), anyString(),
                any(), anyString(), anyString());
        verify(jobApi, never()).triggerJobByKey(eq("KPI_SCORE_CALC"), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void currentLevelRunning_returnsWithoutSubmittingNextLevel() {
        when(jobApi.isJobRunning("LEVEL1_METRIC_CALC")).thenReturn(true);

        coordinator.run(TODAY);

        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verify(jobApi, never()).triggerJobByKey(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void successfulLevels_areAdvancedInOrder_andKpiIsSubmittedOnlyAfterLevel3() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "SUCCESS", TODAY.atTime(6, 1))),
                List.of(task(2, "SUCCESS", TODAY.atTime(6, 2))),
                List.of(task(3, "SUCCESS", TODAY.atTime(6, 3))),
                List.of());

        coordinator.run(TODAY);

        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verify(jobApi).triggerJobByKey("KPI_SCORE_CALC", "AUTO", null, DT, null, null);
    }

    @Test
    void completedLevelOne_submitsOnlyLevelTwoThisRound() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "SUCCESS", TODAY.atTime(6, 1))),
                List.of());

        coordinator.run(TODAY);

        verify(levelTriggerService).trigger(2, DATA_DATE, null, null, "AUTO", null);
        verify(levelTriggerService, never()).trigger(eq(1), eq(DATA_DATE), isNull(), isNull(),
                eq("AUTO"), isNull());
        verify(levelTriggerService, never()).trigger(eq(3), eq(DATA_DATE), isNull(), isNull(),
                eq("AUTO"), isNull());
        verify(jobApi, never()).triggerJobByKey(eq("KPI_SCORE_CALC"), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void completedLevelTwo_submitsOnlyLevelThreeThisRound() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "SUCCESS", TODAY.atTime(6, 1))),
                List.of(task(2, "SUCCESS", TODAY.atTime(6, 2))),
                List.of());

        coordinator.run(TODAY);

        verify(levelTriggerService).trigger(3, DATA_DATE, null, null, "AUTO", null);
        verify(levelTriggerService, never()).trigger(eq(1), eq(DATA_DATE), isNull(), isNull(),
                eq("AUTO"), isNull());
        verify(levelTriggerService, never()).trigger(eq(2), eq(DATA_DATE), isNull(), isNull(),
                eq("AUTO"), isNull());
        verify(jobApi, never()).triggerJobByKey(eq("KPI_SCORE_CALC"), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void failedCurrentLevel_isNotRetriedAutomatically() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "FAILED", TODAY.atTime(6, 10))));

        coordinator.run(TODAY);

        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verify(jobApi, never()).triggerJobByKey(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void midnightSuccessFromPreviousAutomaticChain_doesNotBlockSixOClockRun() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "SUCCESS", TODAY.atStartOfDay())));

        coordinator.run(TODAY);

        verify(levelTriggerService).trigger(1, DATA_DATE, null, null, "AUTO", null);
    }

    @Test
    void runningTaskForSameDataDate_blocksEvenWhenGovernanceLogIsNotVisibleYet() {
        when(taskMapper.selectList(any())).thenReturn(
                List.of(task(1, "RUNNING", TODAY.atTime(6, 5))));

        coordinator.run(TODAY);

        verify(levelTriggerService, never()).trigger(any(Integer.class), any(LocalDate.class),
                anyString(), any(), anyString(), anyString());
        verify(jobApi, never()).triggerJobByKey(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    private void stubCounts(long custTmp, long custHist, long empTmp, long empHist) {
        when(archiveMapper.countByTableDate("XAN_M98_CUST_STAT_SHOW3_TMP", DT)).thenReturn(custTmp);
        when(archiveMapper.countByTableDate("XAN_M98_CUST_STAT_SHOW3_H2", DT)).thenReturn(custHist);
        when(archiveMapper.countByTableDate("XAN_M98_EMP_STAT_SHOW3_TMP", DT)).thenReturn(empTmp);
        when(archiveMapper.countByTableDate("XAN_M98_EMP_STAT_SHOW3_H2", DT)).thenReturn(empHist);
    }

    private PerfMetricCalcTask task(int level, String status, LocalDateTime startTime) {
        PerfMetricCalcTask task = new PerfMetricCalcTask();
        task.setMetricLevel(level);
        task.setTaskType("METRIC_CALC_L" + level);
        task.setDataDate(DATA_DATE);
        task.setStatus(status);
        task.setStartTime(startTime);
        return task;
    }

    private void verifyNoTaskLookup() {
        verify(taskMapper, never()).selectList(any());
    }
}
