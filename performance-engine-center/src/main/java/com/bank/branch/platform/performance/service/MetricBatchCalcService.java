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
     * 执行指定级别的指标批量计算
     */
    public void execute(int metricLevel, LocalDate dataDate) {
        if (metricLevel < 1 || metricLevel > 3) {
            throw new IllegalArgumentException("指标级别必须为 1/2/3，当前值: " + metricLevel);
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
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
            List<PerfMetricDef> metrics = metricDefMapper.selectList(
                    new LambdaQueryWrapper<PerfMetricDef>()
                            .eq(PerfMetricDef::getStatus, "ACTIVE")
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

            // 4. 并发执行，控制并发数
            int poolSize = Math.min(MAX_CONCURRENCY, metrics.size());
            log.info("【{}级指标批量计算】启动线程池，并发数={}", metricLevel, poolSize);
            ExecutorService executor = Executors.newFixedThreadPool(poolSize,
                    r -> { Thread t = new Thread(r, "metric-batch-L" + metricLevel); t.setDaemon(true); return t; });

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicInteger skipCount = new AtomicInteger(0);
            List<String> failedMetrics = new CopyOnWriteArrayList<>();

            List<Future<?>> futures = new ArrayList<>();
            for (PerfMetricDef def : metrics) {
                futures.add(executor.submit(() -> calcSingleMetric(taskId, metricLevel, def, dataDate, successCount, failCount, skipCount, failedMetrics)));
            }

            for (Future<?> f : futures) {
                try { f.get(300, TimeUnit.SECONDS); } catch (Exception e) {
                    log.warn("【{}级指标批量计算】等待指标计算超时或异常: {}", metricLevel, e.getMessage());
                }
            }
            executor.shutdown();

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
     * 计算单个指标
     */
    private void calcSingleMetric(String taskId, int metricLevel, PerfMetricDef def, LocalDate dataDate,
                                   AtomicInteger successCount, AtomicInteger failCount,
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
            MetricCalcResult result = metricCalcService.calcMetricWithStats(
                    def.getMetricCode(), dataDate, "V1", "BATCH");
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
