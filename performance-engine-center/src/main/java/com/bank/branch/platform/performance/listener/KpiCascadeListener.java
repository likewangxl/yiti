package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.event.MetricCalcCompletedEvent;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * KPI 联动监听器（V1.7）.
 *
 * <p>监听 {@link MetricCalcCompletedEvent}，反查 perf_kpi_item 找出依赖该指标的 ACTIVE KPI 方案，
 * 按 cycleType 推导 cycleDate 后，经 Redis SETNX 30s 防重，触发
 * {@link KpiCalcService#calcScheme} 完成 KPI 方案重算.
 *
 * <p>异步执行（{@code @Async("kpiCascadeExecutor")}），不阻塞指标计算主线程.
 * 单方案计算失败只打 warn 日志，不影响其他方案的触发（异常隔离）.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiCascadeListener {

    private final PerfKpiItemMapper kpiItemMapper;
    private final KpiSchemeService kpiSchemeService;
    private final KpiCalcService kpiCalcService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 处理指标计算完成事件.
     *
     * <p>FAILED 状态直接忽略，不触发 KPI 重算（避免基于错误数据驱动下游计算）.
     * {@code fallbackExecution = true}：无活动事务时（如单元测试直接 publish）也执行.
     *
     * @param event 指标计算完成事件
     */
    @Async("kpiCascadeExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMetricCompleted(MetricCalcCompletedEvent event) {
        if ("FAILED".equals(event.runStatus())) {
            log.info("[KpiCascade] metric={} 状态 FAILED，不触发 KPI", event.metricCode());
            return;
        }
        List<String> schemeIds = kpiItemMapper.selectActiveSchemeIdsByMetric(event.metricCode());
        if (schemeIds == null || schemeIds.isEmpty()) {
            return;
        }

        for (String schemeId : schemeIds) {
            try {
                triggerScheme(schemeId, event);
            } catch (Exception e) {
                log.warn("[KpiCascade] schemeId={} metric={} 触发失败",
                    schemeId, event.metricCode(), e);
            }
        }
    }

    /**
     * 对单个 KPI 方案触发重算.
     *
     * <p>流程：加载方案 → 推导 cycleDate → Redis SETNX 30s 防重 → 调用 calcScheme.
     *
     * @param schemeId 方案 ID
     * @param event    触发事件
     */
    private void triggerScheme(String schemeId, MetricCalcCompletedEvent event) {
        PerfKpiScheme scheme;
        try {
            scheme = kpiSchemeService.getById(schemeId);
        } catch (PerfException e) {
            // 方案已被删除或不存在（查询结果与 ACTIVE 状态不一致的边界情况），跳过
            log.warn("[KpiCascade] schemeId={} 方案不存在，跳过: {}", schemeId, e.getMessage());
            return;
        }
        if (scheme == null || !"ACTIVE".equalsIgnoreCase(scheme.getStatus())) {
            return;
        }

        LocalDate cycleDate = resolveCycleDate(scheme.getCycleType(), event.dataDate());
        // Redis SETNX 30s 防重：同一方案+周期+版本在 30s 内只触发一次
        String lockKey = String.format("kpi:cascade:%s:%s:%s",
            scheme.getSchemeCode(), cycleDate, event.version());
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
        } catch (Exception redisEx) {
            // Redis 不可用时退化为不防重（宁可重算也不丢计算，保证最终一致）
            log.warn("[KpiCascade] Redis 不可用，退化为不防重: {}", redisEx.getMessage());
            acquired = true;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[KpiCascade] 30s 内已触发 lockKey={}，跳过", lockKey);
            return;
        }

        int success = kpiCalcService.calcScheme(scheme.getSchemeCode(), scheme.getCycleType(),
            cycleDate, event.dataDate(), event.version());
        log.info("[KpiCascade] schemeCode={} cycleType={} cycleDate={} success={}",
            scheme.getSchemeCode(), scheme.getCycleType(), cycleDate, success);
    }

    /**
     * 按 cycleType 将数据日期推导为周期起始日（cycleDate）.
     *
     * <p>周期起始日语义：
     * <ul>
     *   <li>MONTHLY   → 当月第一天</li>
     *   <li>QUARTERLY → 当季度第一天</li>
     *   <li>YEARLY    → 当年第一天</li>
     *   <li>WEEKLY    → 当周周一（ISO 8601）</li>
     * </ul>
     *
     * @param cycleType 周期类型
     * @param dataDate  数据日期
     * @return 周期起始日
     * @throws PerfException cycleType 无法识别时抛出
     */
    LocalDate resolveCycleDate(String cycleType, LocalDate dataDate) {
        return switch (cycleType == null ? "" : cycleType.toUpperCase()) {
            case "MONTHLY"   -> dataDate.withDayOfMonth(1);
            case "QUARTERLY" -> dataDate.with(IsoFields.DAY_OF_QUARTER, 1L);
            case "YEARLY"    -> dataDate.withDayOfYear(1);
            case "WEEKLY"    -> dataDate.with(DayOfWeek.MONDAY);
            default -> throw new PerfException(PerfErrorCode.KPI_CYCLE_TYPE_INVALID, cycleType);
        };
    }
}
