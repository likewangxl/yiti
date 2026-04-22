package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效计算触发对外 API (仅允许运维/定时任务模块调用).
 *
 * <p>V1.0/V1.1 实现状态:
 * <ul>
 *   <li>✅ V1.0 实现: getRunTask (任务查询, 读 perf_run_task)</li>
 *   <li>✅ V1.1 P3.3 实现: triggerMetricCalc (单指标计算触发, 委托 MetricCalcService)</li>
 *   <li>⏳ V1.1 UOE 占位: triggerKpiCalc (P4 交付) / triggerRecalc (P7 交付)</li>
 * </ul>
 */
public interface PerfCalcApi {

    /**
     * 触发某日的 KPI 计算.
     * <p>V1.0/V1.1 P3 抛 UnsupportedOperationException; V1.1 P4 实现.
     *
     * @return 任务 ID
     */
    String triggerKpiCalc(LocalDate dataDate);

    /**
     * 触发单个指标的计算（V1.1 Task P3.3 交付）.
     *
     * <p>Facade 对外暴露的"触发计算"能力，内部委托
     * {@link com.bank.branch.platform.performance.service.MetricCalcService#calcMetric}
     * 完成 SQL/Groovy 路由、宽表写入、run_task 状态机。
     *
     * <p>消费方：定时任务（如每日指标计算 job）、运维后台补跑、external 上报触发。
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（必填）
     * @param version    数据版本（必填，通常取自 sys_control.current_version）
     * @return run_task 主键 ID
     * @throws IllegalArgumentException 任一参数为 null
     */
    String triggerMetricCalc(String metricCode, LocalDate dataDate, String version);

    /**
     * 触发历史回算.
     * <p>V1.0/V1.1 P3 抛 UnsupportedOperationException; V1.1 P7 实现.
     *
     * @return 任务 ID
     */
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator);

    /**
     * 查询运行任务状态.
     *
     * @param taskId 任务 ID
     * @return 任务详情, 不存在返回 Optional.empty()
     */
    Optional<PerfRunTaskDTO> getRunTask(String taskId);
}
