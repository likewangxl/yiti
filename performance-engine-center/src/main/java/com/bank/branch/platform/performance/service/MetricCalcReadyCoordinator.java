package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.job.StatShowArchiveDates;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * M98 统计展示数据就绪后的指标计算协调器。
 *
 * <p>协调器只读比较外部系统落地的 TMP/H 行数，并通过治理中心排队任务；它不直接写
 * 展示表，也不在当前线程执行指标或 KPI 计算。每次轮询最多提交一个下游任务，依靠
 * 下一轮轮询推进 L1 → L2 → L3 → KPI 的顺序。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricCalcReadyCoordinator {

    /** Quartz / 治理任务键。 */
    public static final String COORDINATOR_JOB_KEY = "METRIC_CALC_READY_COORDINATOR";
    public static final String STAT_SHOW_ARCHIVE_JOB_KEY = "STAT_SHOW_ARCHIVE";
    public static final String KPI_SCORE_CALC_JOB_KEY = "KPI_SCORE_CALC";

    /** 外部 M98 统计展示表固定白名单。 */
    public static final String CUST_STAT_SHOW_TABLE = "XAN_M98_CUST_STAT_SHOW3";
    public static final String EMP_STAT_SHOW_TABLE = "XAN_M98_EMP_STAT_SHOW3";
    public static final String TMP_SUFFIX = "_TMP";

    private static final String KPI_TASK_TYPE = "KPI_SCORE_CALC";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int AUTO_CHAIN_START_HOUR = 6;

    private final StatShowArchiveMapper archiveMapper;
    private final PerfMetricCalcTaskMapper taskMapper;
    private final JobApi jobApi;
    private final MetricLevelTriggerService metricLevelTriggerService;

    /** 以 Asia/Shanghai 当前日期协调昨天的数据。 */
    public void run() {
        run(LocalDate.now(SHANGHAI));
    }

    /**
     * 按指定运行日协调前一日数据，供 Quartz 入口和确定性单测使用。
     *
     * @param today 运行日（数据日为 today - 1）
     */
    public void run(LocalDate today) {
        Objects.requireNonNull(today, "today");
        LocalDate dataDate = today.minusDays(1);
        String dataDateText = StatShowArchiveDates.fmt(dataDate);
        String histSuffix = StatShowArchiveDates.histSuffixForDataDate(dataDate);

        Counts counts = readCounts(dataDateText, histSuffix);
        if (counts.tmpTotal() == 0) {
            log.debug("[MetricCalcReadyCoordinator] TMP 无数据，跳过 dataDate={}", dataDate);
            return;
        }

        if (!counts.matches()) {
            if (jobApi.isJobRunning(STAT_SHOW_ARCHIVE_JOB_KEY)) {
                log.info("[MetricCalcReadyCoordinator] 归档任务运行中，等待下一轮 dataDate={}", dataDate);
                return;
            }
            String reason = "数据就绪同步：" + dataDateText + " 临时表与历史表计数不一致";
            jobApi.triggerJobByKey(STAT_SHOW_ARCHIVE_JOB_KEY, "AUTO", reason,
                    dataDateText, null, null);
            log.info("[MetricCalcReadyCoordinator] 已触发归档同步，dataDate={}", dataDate);
            return;
        }

        LocalDateTime chainStart = today.atTime(AUTO_CHAIN_START_HOUR, 0);
        for (int level = 1; level <= 3; level++) {
            String jobKey = levelJobKey(level);
            TaskState state = stateOfLevel(dataDate, level, jobKey, chainStart);
            if (state == TaskState.RUNNING || state == TaskState.FAILED) {
                log.info("[MetricCalcReadyCoordinator] L{} 状态={}，本轮停止 dataDate={}",
                        level, state, dataDate);
                return;
            }
            if (state == TaskState.NOT_STARTED) {
                metricLevelTriggerService.trigger(level, dataDate, null, null, "AUTO", null);
                log.info("[MetricCalcReadyCoordinator] 已触发 L{}，dataDate={}", level, dataDate);
                return;
            }
        }

        TaskState kpiState = stateOfKpi(dataDate, chainStart);
        if (kpiState == TaskState.RUNNING || kpiState == TaskState.FAILED) {
            log.info("[MetricCalcReadyCoordinator] KPI 状态={}，本轮停止 dataDate={}", kpiState, dataDate);
            return;
        }
        if (kpiState == TaskState.NOT_STARTED) {
            jobApi.triggerJobByKey(KPI_SCORE_CALC_JOB_KEY, "AUTO", null,
                    dataDateText, null, null);
            log.info("[MetricCalcReadyCoordinator] 已触发 KPI，dataDate={}", dataDate);
        }
    }

    private Counts readCounts(String dataDate, String histSuffix) {
        String custHist = CUST_STAT_SHOW_TABLE + histSuffix;
        String empHist = EMP_STAT_SHOW_TABLE + histSuffix;
        long custTmpCount = archiveMapper.countByTableDate(CUST_STAT_SHOW_TABLE + TMP_SUFFIX, dataDate);
        long custHistCount = archiveMapper.countByTableDate(custHist, dataDate);
        long empTmpCount = archiveMapper.countByTableDate(EMP_STAT_SHOW_TABLE + TMP_SUFFIX, dataDate);
        long empHistCount = archiveMapper.countByTableDate(empHist, dataDate);
        return new Counts(custTmpCount, custHistCount, empTmpCount, empHistCount);
    }

    private TaskState stateOfLevel(LocalDate dataDate, int level, String jobKey, LocalDateTime chainStart) {
        if (jobApi.isJobRunning(jobKey)) {
            return TaskState.RUNNING;
        }
        List<PerfMetricCalcTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PerfMetricCalcTask>()
                .eq(PerfMetricCalcTask::getDataDate, dataDate)
                .eq(PerfMetricCalcTask::getMetricLevel, level)
                .eq(PerfMetricCalcTask::getTaskType, "METRIC_CALC_L" + level));
        return resolveState(tasks, chainStart);
    }

    private TaskState stateOfKpi(LocalDate dataDate, LocalDateTime chainStart) {
        if (jobApi.isJobRunning(KPI_SCORE_CALC_JOB_KEY)) {
            return TaskState.RUNNING;
        }
        List<PerfMetricCalcTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PerfMetricCalcTask>()
                .eq(PerfMetricCalcTask::getDataDate, dataDate)
                .eq(PerfMetricCalcTask::getTaskType, KPI_TASK_TYPE));
        return resolveState(tasks, chainStart);
    }

    private TaskState resolveState(List<PerfMetricCalcTask> tasks, LocalDateTime chainStart) {
        if (tasks == null || tasks.isEmpty()) {
            return TaskState.NOT_STARTED;
        }
        for (PerfMetricCalcTask task : tasks) {
            if (isStatus(task, "RUNNING")) {
                return TaskState.RUNNING;
            }
        }
        PerfMetricCalcTask latestTerminal = tasks.stream()
                .filter(task -> task != null && task.getStartTime() != null)
                .filter(task -> !task.getStartTime().isBefore(chainStart))
                .filter(task -> isStatus(task, "SUCCESS") || isStatus(task, "FAILED"))
                .max(Comparator.comparing(PerfMetricCalcTask::getStartTime))
                .orElse(null);
        if (latestTerminal == null) {
            return TaskState.NOT_STARTED;
        }
        return isStatus(latestTerminal, "SUCCESS") ? TaskState.SUCCESS : TaskState.FAILED;
    }

    private boolean isStatus(PerfMetricCalcTask task, String status) {
        return task != null && task.getStatus() != null
                && status.equals(task.getStatus().trim().toUpperCase(Locale.ROOT));
    }

    private String levelJobKey(int level) {
        return "LEVEL" + level + "_METRIC_CALC";
    }

    private enum TaskState {
        NOT_STARTED,
        RUNNING,
        SUCCESS,
        FAILED
    }

    private record Counts(long custTmp, long custHist, long empTmp, long empHist) {

        private long tmpTotal() {
            return custTmp + empTmp;
        }

        private boolean matches() {
            return custTmp == custHist && empTmp == empHist;
        }
    }
}
