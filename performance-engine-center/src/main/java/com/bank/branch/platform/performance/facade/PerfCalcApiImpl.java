package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.PerfCalcApi;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.facade.assembler.RunTaskAssembler;
import com.bank.branch.platform.performance.service.HistoryRecalcService;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 绩效计算触发对外 API 实现.
 *
 * <p>V1.0/V1.1 契约 (spec §5.2.5):
 * <ul>
 *   <li>{@link #getRunTask(String)} V1.0 实现: 读 perf_run_task 并装配 DTO</li>
 *   <li>{@link #triggerMetricCalc(String, LocalDate, String)} V1.1 P3.3 实现:
 *       委托 {@link MetricCalcService#calcMetric} 完成单指标计算</li>
 *   <li>{@link #triggerRecalc(String, LocalDate, LocalDate, String, String)} V1.1 P7.2 实现:
 *       委托 {@link HistoryRecalcService#recalc} 历史回算（5/7 参数重载同源）</li>
 *   <li>{@link #triggerKpiCalc(LocalDate)} V1.1 P4 UOE 占位（实际 P4 已在 KpiApi 交付）</li>
 * </ul>
 *
 * <p>缓存策略: {@link #getRunTask} 不缓存, 直接穿透 Service。
 * 原因: 任务状态是业务数据 (PENDING/RUNNING/SUCCESS/FAILED/...),
 * 在计算过程中频繁变动 (秒级), 缓存收益低且一致性成本高; 与
 * {@link TargetApiImpl#getTargetValue} 的业务数据不缓存策略保持一致。
 *
 * <p>UOE 消息: "V1.1 delivered", 与 {@link MetricApiImpl} / {@link KpiApiImpl} 一致,
 * 消费方可通过消息串统一识别 "V1.1 才交付" 的占位方法。
 *
 * <p>消费方 (V1.0): portal-content-center (任务进度查看), 运维后台 (任务审计)。
 * <p>消费方 (V1.1): 定时任务 / external 上报 / 运维补跑 (triggerMetricCalc)。
 */
@Service
@RequiredArgsConstructor
public class PerfCalcApiImpl implements PerfCalcApi {

    private final PerfRunTaskService perfRunTaskService;
    private final MetricCalcService metricCalcService;
    private final HistoryRecalcService historyRecalcService;

    /**
     * 触发某日 KPI 计算 (V1.1 P4 交付).
     *
     * @param dataDate 数据日期
     * @return 任务 ID
     * @throws UnsupportedOperationException V1.1 P3 未实现
     */
    @Override
    public String triggerKpiCalc(LocalDate dataDate) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    /**
     * 触发单个指标的计算 (V1.1 P3.3 实现).
     *
     * <p>委托 {@link MetricCalcService#calcMetric}（SQL/Groovy 路由 + 宽表写入
     * + run_task 状态机）。入参做非空校验兜底，具体业务异常由 Service 层统一抛出。
     *
     * @param metricCode 指标编码（不能为 null）
     * @param dataDate   数据日期（不能为 null）
     * @param version    数据版本（不能为 null）
     * @return run_task 主键 ID
     * @throws IllegalArgumentException 任一入参为 null
     */
    @Override
    public String triggerMetricCalc(String metricCode, LocalDate dataDate, String version) {
        if (metricCode == null) {
            throw new IllegalArgumentException("metricCode 不能为空");
        }
        if (dataDate == null) {
            throw new IllegalArgumentException("dataDate 不能为空");
        }
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        return metricCalcService.calcMetric(metricCode, dataDate, version);
    }

    /**
     * 触发历史回算 (V1.1 P7.2 实现，5 参数签名).
     *
     * <p>等价于调用 7 参数版本 {@link #triggerRecalc(String, LocalDate, LocalDate, List, String, String, String)}
     * 传入 {@code metricCodes=null}（所有 ACTIVE 指标）且 {@code version=""}（交由 Service 兜底校验）。
     *
     * <p>保留本签名以兼容 03/04 文档历史契约；新接入方推荐使用 7 参数版本以显式指定
     * 指标清单与数据版本。
     *
     * @param cycleType 周期类型（仅作审计字段传递，实际按日切分）
     * @param from      起始日期（含）
     * @param to        截止日期（含）
     * @param reason    回算原因（@AuditLog reasonRequired=true）
     * @param operator  发起人 emp_id
     * @return 父级 run_task 主键
     */
    @Override
    public String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator) {
        return triggerRecalc(cycleType, from, to, null, "", reason, operator);
    }

    /**
     * 触发历史回算 (V1.1 P7.2 实现，7 参数完整签名).
     *
     * <p>委托 {@link HistoryRecalcService#recalc}：按日期范围 × 指标码批量调
     * {@code MetricCalcService.calcMetric}，并维护一个父级 {@code perf_run_task}
     * （{@code taskType=RECALC}）聚合状态与失败明细。
     *
     * @param cycleType   周期类型（审计用途；V1.1 实际按日切分）
     * @param from        起始日期（含）
     * @param to          截止日期（含）
     * @param metricCodes 指标编码列表（null/空 → 所有 ACTIVE 指标）
     * @param version     数据版本（必填）
     * @param reason      回算原因（@AuditLog reasonRequired=true）
     * @param operator    发起人 emp_id
     * @return 父级 run_task 主键
     */
    @Override
    public String triggerRecalc(String cycleType, LocalDate from, LocalDate to,
                                List<String> metricCodes, String version,
                                String reason, String operator) {
        // cycleType 当前仅留作审计/日志字段；V1.1 实际按日切分，由 Service 处理
        return historyRecalcService.recalc(from, to, metricCodes, version, reason, operator);
    }

    /**
     * 按任务 ID 查询任务状态 (不缓存).
     *
     * @param taskId 任务 ID (varchar 32)
     * @return Optional 包装的任务 DTO, 未找到返回 {@link Optional#empty()}
     */
    @Override
    public Optional<PerfRunTaskDTO> getRunTask(String taskId) {
        return perfRunTaskService.getById(taskId).map(RunTaskAssembler::toDto);
    }
}
