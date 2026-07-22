package com.bank.branch.platform.performance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 指标手动执行的后台执行体（2026-07-22）。
 *
 * <p>指标执行原为全同步：HTTP 请求一直阻塞到 SQL 跑完、级联下游也刷完才返回，重指标或大批量
 * 会撞网关读超时，前端表现为「网络异常或后端未启动」。改为提交即返回后，请求线程只负责
 * 校验 + 预建 PENDING 任务行，真正的计算由本类在 {@code metricExecuteExecutor} 线程池里跑。
 *
 * <p>放在独立 bean 而非 Facade 自身方法上，是因为 {@code @Async} 走 Spring 代理，
 * 同类自调用不会生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricAsyncRunner {

    private final MetricCalcService metricCalcService;
    private final CascadeRefresher cascadeRefresher;

    /**
     * 后台执行一次指标计算，复用请求线程预建的 run_task 行。
     *
     * <p>异常一律吞掉：后台线程没有调用方能接住它，抛出去只会打到线程池默认异常处理器刷栈。
     * 任务终态（FAILED + 错误信息）已由 {@link MetricCalcService} 内部落库，前端轮询可见。
     *
     * @param metricCode   指标编码
     * @param dataDate     数据日期
     * @param version      数据版本（请求线程内已从 SYS_CONTROL 解析好，避免异步线程重复解析产生漂移）
     * @param cascade      是否级联刷新下游
     * @param allocDate    业绩分配日期（可为 null，绑定阶段兜底为 dataDate）
     * @param presetTaskId 请求线程预建的 run_task 主键
     */
    @Async("metricExecuteExecutor")
    public void runAsync(String metricCode, LocalDate dataDate, String version,
                         boolean cascade, LocalDate allocDate, String presetTaskId) {
        long start = System.currentTimeMillis();
        try {
            if (cascade) {
                cascadeRefresher.refreshCascade(metricCode, dataDate, version, allocDate, presetTaskId);
            } else {
                metricCalcService.calcMetricWithStats(metricCode, dataDate, version, "MANUAL", allocDate, presetTaskId);
            }
            log.info("[MetricAsyncRunner] 指标 {} 后台执行完成 taskId={}, cascade={}, 耗时 {}ms",
                    metricCode, presetTaskId, cascade, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            // 终态已由 calcMetric 落库，这里只记日志，保证线程池线程存活
            log.warn("[MetricAsyncRunner] 指标 {} 后台执行失败 taskId={}, 耗时 {}ms, 原因 {}",
                    metricCode, presetTaskId, System.currentTimeMillis() - start, ex.getMessage(), ex);
        }
    }
}
