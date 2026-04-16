package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效计算触发对外 API (仅允许运维/定时任务模块调用).
 *
 * <p>V1.0 实现状态:
 * <ul>
 *   <li>✅ V1.0 实现: getRunTask (任务查询, 读 perf_run_task)</li>
 *   <li>⏳ V1.1 UOE 占位: triggerKpiCalc / triggerRecalc (计算触发, 需要计算能力)</li>
 * </ul>
 */
public interface PerfCalcApi {

    /**
     * 触发某日的 KPI 计算.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     *
     * @return 任务 ID
     */
    String triggerKpiCalc(LocalDate dataDate);

    /**
     * 触发历史回算.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
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
