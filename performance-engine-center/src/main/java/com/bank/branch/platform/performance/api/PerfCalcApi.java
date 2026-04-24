package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 绩效计算触发对外 API (仅允许运维/定时任务模块调用).
 *
 * <p>V1.0/V1.1/V1.2/V1.3 实现状态:
 * <ul>
 *   <li>✅ V1.0 实现: getRunTask (任务查询, 读 perf_run_task)</li>
 *   <li>✅ V1.1 P3.3 实现: triggerMetricCalc (单指标计算触发, 委托 MetricCalcService)</li>
 *   <li>✅ V1.1 P7.2 实现: triggerRecalc (历史回算触发, 委托 HistoryRecalcService)</li>
 *   <li>✅ V1.3 R2.1 实现: triggerKpiCalc (按方案批量计算, 委托 KpiCalcService.calcScheme)</li>
 * </ul>
 */
public interface PerfCalcApi {

    /**
     * 触发某方案的 KPI 批量计算（V1.3 Task R2.1 交付）.
     *
     * <p>委托 {@link com.bank.branch.platform.performance.service.KpiCalcService#calcScheme}，
     * 对方案内所有员工计算 KPI 并写入 {@code kpi_result}。
     *
     * <p>V1.0/V1.1/V1.2 曾因"入口唯一"保留 UOE 占位，V1.3 决定统一由 PerfCalcApi 暴露。
     * 与 {@code KpiApi.triggerKpiCalc} 语义相同，双入口均可使用。
     *
     * @param schemeCode KPI 方案编码
     * @param cycleType  周期类型（MONTHLY / QUARTERLY / YEARLY）
     * @param cycleDate  周期对应日期（如月末）
     * @param asOfDate   计算基准日（与宽表 data_date 对齐）
     * @param version    数据版本（与宽表 version 对齐）
     * @return 本次批量计算成功的员工数
     */
    int triggerKpiCalc(String schemeCode, String cycleType,
                       LocalDate cycleDate, LocalDate asOfDate, String version);

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
     * 触发历史回算（V1.1 Task P7.2 交付，5 参数简化签名）.
     *
     * <p>等价于 {@link #triggerRecalc(String, LocalDate, LocalDate, java.util.List, String, String, String)}
     * 传入 {@code metricCodes=null}（所有 ACTIVE 指标）且 {@code version=null}（Facade 兜底按空串透传）。
     *
     * <p>保留此 5 参数签名以兼容 03/04 文档旧契约；新接入方推荐使用 7 参数版本。
     *
     * @param cycleType 周期类型（MONTHLY / QUARTERLY / YEARLY，V1.1 未强制按 cycle 切分，由 Service 按日切分）
     * @param from      起始日期（含）
     * @param to        截止日期（含，>= from）
     * @param reason    回算原因（必填，@AuditLog reasonRequired=true）
     * @param operator  发起人 emp_id（必填）
     * @return 父级 run_task 主键（taskType=RECALC）
     */
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator);

    /**
     * 触发历史回算（V1.1 Task P7.2 完整签名）.
     *
     * <p>Facade 委托 {@link com.bank.branch.platform.performance.service.HistoryRecalcService#recalc}，
     * 按日期范围 × 指标码批量调用 {@code MetricCalcService.calcMetric}。
     *
     * @param cycleType   周期类型（用于审计日志；V1.1 实际按日切分，不按 cycle）
     * @param from        起始日期（含）
     * @param to          截止日期（含）
     * @param metricCodes 指标编码列表（null 或空 → 所有 ACTIVE 指标）
     * @param version     数据版本（必填）
     * @param reason      回算原因（@AuditLog reasonRequired=true）
     * @param operator    发起人 emp_id
     * @return 父级 run_task 主键
     */
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to,
                         List<String> metricCodes, String version,
                         String reason, String operator);

    /**
     * 查询运行任务状态.
     *
     * @param taskId 任务 ID
     * @return 任务详情, 不存在返回 Optional.empty()
     */
    Optional<PerfRunTaskDTO> getRunTask(String taskId);
}
