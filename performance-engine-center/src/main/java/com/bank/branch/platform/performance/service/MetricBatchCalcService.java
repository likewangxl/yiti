package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricCalcLog;
import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcLogMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指标批量计算基础服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricBatchCalcService {

    private static final int MAX_CONCURRENCY = 5;

    private final PerfMetricCalcTaskMapper taskMapper;
    private final PerfMetricCalcLogMapper logMapper;
    private final PerfMetricDefMapper metricDefMapper;
    private final MetricCalcService metricCalcService;
    private final com.bank.branch.platform.performance.mapper.PerfRunTaskMapper perfRunTaskMapper;

    /**
     * 执行指定级别的指标批量计算（旧入口：自动生成 task id）.
     */
    public void execute(int metricLevel, LocalDate dataDate) {
        execute(metricLevel, dataDate, null, null);
    }

    /**
     * 执行指定级别的指标批量计算（不带业绩分配日期；2/3 级与历史调用方走此入口）.
     *
     * @param metricLevel 指标级别 1/2/3
     * @param dataDate    数据日期
     * @param runLogId    本次调度的 {@code SYS_JOB_RUN_LOG.id}；非空则用作
     *                    {@code PERF_METRIC_CALC_TASK.id}，便于两表关联；为空（手动直调等）回退 UUID
     */
    public void execute(int metricLevel, LocalDate dataDate, String runLogId) {
        execute(metricLevel, dataDate, null, runLogId);
    }

    /**
     * 执行指定级别的指标批量计算（带业绩分配日期 :allocDate 入参）.
     *
     * <p>allocDate 仅 1 级指标批量计算（{@code Level1MetricCalcJob}）手动触发时由页面传入，
     * 为 null 时透传到 {@link MetricCalcService#calcMetricWithStats} 由计算引擎兜底为 dataDate。
     *
     * @param metricLevel 指标级别 1/2/3
     * @param dataDate    数据日期
     * @param allocDate   业绩分配日期（可为 null，兜底 dataDate）
     * @param runLogId    本次调度的 {@code SYS_JOB_RUN_LOG.id}；非空则用作
     *                    {@code PERF_METRIC_CALC_TASK.id}，便于两表关联；为空（手动直调等）回退 UUID
     */
    public void execute(int metricLevel, LocalDate dataDate, LocalDate allocDate, String runLogId) {
        if (metricLevel < 1 || metricLevel > 3) {
            throw new IllegalArgumentException("指标级别必须为 1/2/3，当前值: " + metricLevel);
        }
        // task.id 复用本次运行日志 id（SYS_JOB_RUN_LOG.id），便于与执行日志关联；为空回退 UUID
        String taskId = (runLogId != null && !runLogId.isBlank())
                ? runLogId.trim()
                : UUID.randomUUID().toString().replace("-", "");
        String taskName = metricLevel + "级指标批量计算";

        log.info("========== 【{}级指标批量计算】开始 ==========", metricLevel);
        log.info("【{}级指标批量计算】任务ID={}, 数据日期={}", metricLevel, taskId, dataDate);

        // 1. 登记任务开始
        PerfMetricCalcTask task = new PerfMetricCalcTask();
        task.setId(taskId);
        task.setTaskName(taskName);
        task.setTaskType("METRIC_CALC_L" + metricLevel);
        task.setMetricLevel(metricLevel);
        task.setDataDate(dataDate);
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setSkipCount(0);
        taskMapper.insert(task);
        log.info("【{}级指标批量计算】任务流水已登记，状态=开始", metricLevel);

        try {
            // 2. 级别依赖检查
            if (metricLevel >= 2) {
                int preLevel = metricLevel - 1;
                log.info("【{}级指标批量计算】正在检查前置依赖：{}级指标是否已完成...", metricLevel, preLevel);
                boolean preDone = taskMapper.selectCount(new LambdaQueryWrapper<PerfMetricCalcTask>()
                        .eq(PerfMetricCalcTask::getDataDate, dataDate)
                        .eq(PerfMetricCalcTask::getMetricLevel, preLevel)
                        .eq(PerfMetricCalcTask::getStatus, "SUCCESS")) > 0;
                if (!preDone) {
                    String reason = "前置依赖检查失败：" + preLevel + "级指标在数据日期 " + dataDate + " 尚未完成计算（无SUCCESS记录），" + metricLevel + "级指标计算中止";
                    log.error("【{}级指标批量计算】{}", metricLevel, reason);
                    finishTask(task, "FAILED", reason);
                    log.info("========== 【{}级指标批量计算】异常结束 ==========", metricLevel);
                    return;
                }
                log.info("【{}级指标批量计算】前置依赖检查通过，{}级指标已完成", metricLevel, preLevel);
            }

            // 3. 查找所有已发布+自动+对应级别的指标
            //    「已发布」= status ∈ {ACTIVE, PUBLISHED}（生产 DDL 默认值 ACTIVE 与显式 PUBLISHED 等价），
            //    排除草稿(DRAFT)/已停用(DISABLED)，只计算已发布指标。
            List<PerfMetricDef> metrics = metricDefMapper.selectList(
                    new LambdaQueryWrapper<PerfMetricDef>()
                            .in(PerfMetricDef::getStatus, "ACTIVE", "PUBLISHED")
                            .eq(PerfMetricDef::getCalcMode, "AUTO")
                            .eq(PerfMetricDef::getMetricLevel, metricLevel)
                            .eq(PerfMetricDef::getDeleted, 0));

            task.setTotalCount(metrics.size());
            taskMapper.updateById(task);
            log.info("【{}级指标批量计算】查询到 {} 个待计算指标（状态=已发布, 计算方式=自动, 级别={}）", metricLevel, metrics.size(), metricLevel);

            if (metrics.isEmpty()) {
                log.info("【{}级指标批量计算】无需计算的指标，任务直接完成", metricLevel);
                finishTask(task, "SUCCESS", null);
                log.info("========== 【{}级指标批量计算】正常结束（无指标） ==========", metricLevel);
                return;
            }

            // 4. 分两阶段执行：先算 员工/客户/维度无关 指标，全部完成后再算 机构 指标。
            //    机构(ORG)维度的指标其 SQL 会汇总员工/客户的当日结果（读 EMP/CUST 宽表），
            //    若与 EMP/CUST 并发抢跑，机构指标可能读到旧值或空值，故必须等前者跑完再启动。
            //    阶段内部仍并发（互不依赖）；阶段之间加屏障。
            List<PerfMetricDef> firstStage = new ArrayList<>();   // EMP / CUST / 维度无关(null)
            List<PerfMetricDef> orgStage = new ArrayList<>();     // ORG，最后算
            for (PerfMetricDef def : metrics) {
                if ("ORG".equalsIgnoreCase(def.getBaseDim())) {
                    orgStage.add(def);
                } else {
                    firstStage.add(def);
                }
            }
            log.info("【{}级指标批量计算】分两阶段：第一阶段(员工/客户/维度无关) {} 个，第二阶段(机构) {} 个",
                    metricLevel, firstStage.size(), orgStage.size());

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicInteger skipCount = new AtomicInteger(0);
            List<String> failedMetrics = new CopyOnWriteArrayList<>();

            runStageConcurrently(firstStage, "第一阶段(员工/客户)", taskId, metricLevel, dataDate, allocDate,
                    successCount, failCount, skipCount, failedMetrics);
            runStageConcurrently(orgStage, "第二阶段(机构)", taskId, metricLevel, dataDate, allocDate,
                    successCount, failCount, skipCount, failedMetrics);

            // 5. 更新任务完成信息
            task.setSuccessCount(successCount.get());
            task.setFailCount(failCount.get());
            task.setSkipCount(skipCount.get());
            String finalStatus = failCount.get() > 0 ? "FAILED" : "SUCCESS";
            String errorMsg = failedMetrics.isEmpty() ? null : "失败指标: " + String.join(", ", failedMetrics);
            finishTask(task, finalStatus, errorMsg);

            log.info("【{}级指标批量计算】执行结果汇总：总数={}, 成功={}, 失败={}, 跳过={}",
                    metricLevel, metrics.size(), successCount.get(), failCount.get(), skipCount.get());
            if (!failedMetrics.isEmpty()) {
                log.warn("【{}级指标批量计算】失败指标列表: {}", metricLevel, failedMetrics);
            }
            log.info("========== 【{}级指标批量计算】正常结束 ==========", metricLevel);

        } catch (Exception e) {
            log.error("【{}级指标批量计算】任务执行异常: {}", metricLevel, e.getMessage(), e);
            finishTask(task, "FAILED", e.getMessage());
            log.info("========== 【{}级指标批量计算】异常结束 ==========", metricLevel);
        }
    }

    /**
     * 并发执行一个阶段的全部指标，并阻塞等待该阶段全部完成（阶段屏障）。
     *
     * <p>阶段内部指标互不依赖，用固定大小线程池并发；方法返回即代表本阶段所有指标已算完，
     * 从而保证「上一阶段全部完成后，下一阶段才开始」——机构指标依赖员工/客户结果，靠此顺序成立。
     * 空阶段直接返回，不建线程池。
     */
    private void runStageConcurrently(List<PerfMetricDef> stageMetrics, String stageName,
                                      String taskId, int metricLevel, LocalDate dataDate, LocalDate allocDate,
                                      AtomicInteger successCount, AtomicInteger failCount,
                                      AtomicInteger skipCount, List<String> failedMetrics) {
        if (stageMetrics.isEmpty()) {
            return;
        }
        int poolSize = Math.min(MAX_CONCURRENCY, stageMetrics.size());
        log.info("【{}级指标批量计算】{} 启动线程池，指标数={}，并发数={}",
                metricLevel, stageName, stageMetrics.size(), poolSize);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize,
                r -> { Thread t = new Thread(r, "metric-batch-L" + metricLevel); t.setDaemon(true); return t; });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PerfMetricDef def : stageMetrics) {
                futures.add(executor.submit(() -> calcSingleMetric(taskId, metricLevel, def, dataDate, allocDate,
                        successCount, failCount, skipCount, failedMetrics)));
            }
            for (Future<?> f : futures) {
                try { f.get(300, TimeUnit.SECONDS); } catch (Exception e) {
                    log.warn("【{}级指标批量计算】{} 等待指标计算超时或异常: {}", metricLevel, stageName, e.getMessage());
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 计算单个指标
     */
    private void calcSingleMetric(String taskId, int metricLevel, PerfMetricDef def, LocalDate dataDate,
                                   LocalDate allocDate, AtomicInteger successCount, AtomicInteger failCount,
                                   AtomicInteger skipCount, List<String> failedMetrics) {
        String logId = UUID.randomUUID().toString().replace("-", "");
        PerfMetricCalcLog calcLog = new PerfMetricCalcLog();
        calcLog.setId(logId);
        calcLog.setTaskId(taskId);
        calcLog.setMetricCode(def.getMetricCode());
        calcLog.setMetricName(def.getMetricName());
        calcLog.setDataDate(dataDate);
        calcLog.setStartTime(LocalDateTime.now());

        try {
            boolean hasExpr = false;
            if ("SQL".equals(def.getCalcLogicType()) && def.getSqlText() != null && !def.getSqlText().isBlank()) {
                hasExpr = true;
            } else if ("EXPR".equals(def.getCalcLogicType()) && def.getExprText() != null && !def.getExprText().isBlank()) {
                hasExpr = true;
            }

            if (!hasExpr) {
                log.info("【{}级指标计算】跳过指标 {}（{}），原因：{}表达式为空",
                        metricLevel, def.getMetricCode(), def.getMetricName(), def.getCalcLogicType());
                calcLog.setStatus("SKIPPED");
                calcLog.setEndTime(LocalDateTime.now());
                calcLog.setErrorMsg("表达式为空，跳过");
                logMapper.insert(calcLog);
                skipCount.incrementAndGet();
                return;
            }

            log.info("【{}级指标计算】开始计算指标 {}（{}），计算逻辑={}, 数据日期={}",
                    metricLevel, def.getMetricCode(), def.getMetricName(), def.getCalcLogicType(), dataDate);

            // V1.13+：calcMetricWithStats 直接返回 SubjectStats，success 即实际写入宽表的主体数
            // allocDate 透传为 :allocDate 入参（null 时计算引擎兜底为 dataDate）
            MetricCalcResult result = metricCalcService.calcMetricWithStats(
                    def.getMetricCode(), dataDate, "V1", "BATCH", allocDate);
            int rowCount = result.success();

            calcLog.setStatus("SUCCESS");
            calcLog.setEndTime(LocalDateTime.now());
            calcLog.setRowCount(rowCount);
            logMapper.insert(calcLog);
            successCount.incrementAndGet();
            log.info("【{}级指标计算】指标 {}（{}）计算成功，结果行数={}（total={}, failed={}）",
                    metricLevel, def.getMetricCode(), def.getMetricName(), rowCount, result.total(), result.failed());

        } catch (Exception e) {
            calcLog.setStatus("FAILED");
            calcLog.setEndTime(LocalDateTime.now());
            String msg = e.getMessage();
            calcLog.setErrorMsg(msg != null && msg.length() > 2000 ? msg.substring(0, 2000) : msg);
            logMapper.insert(calcLog);
            failCount.incrementAndGet();
            failedMetrics.add(def.getMetricCode());
            log.error("【{}级指标计算】指标 {}（{}）计算失败: {}", metricLevel, def.getMetricCode(), def.getMetricName(), msg);
        }
    }

    private void finishTask(PerfMetricCalcTask task, String status, String errorMsg) {
        task.setStatus(status);
        task.setEndTime(LocalDateTime.now());
        if (errorMsg != null) {
            task.setErrorMsg(errorMsg.length() > 5000 ? errorMsg.substring(0, 5000) : errorMsg);
        }
        taskMapper.updateById(task);
    }
}
