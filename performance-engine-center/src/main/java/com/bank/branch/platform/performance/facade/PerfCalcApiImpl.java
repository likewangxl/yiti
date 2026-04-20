package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.PerfCalcApi;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.facade.assembler.RunTaskAssembler;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效计算触发对外 API 实现.
 *
 * <p>V1.0 契约 (spec §5.2.5):
 * <ul>
 *   <li>{@link #getRunTask(String)} V1.0 实现: 读 perf_run_task 并装配 DTO</li>
 *   <li>{@link #triggerKpiCalc(LocalDate)} V1.1 UOE 占位</li>
 *   <li>{@link #triggerRecalc(String, LocalDate, LocalDate, String, String)} V1.1 UOE 占位</li>
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
 */
@Service
@RequiredArgsConstructor
public class PerfCalcApiImpl implements PerfCalcApi {

    private final PerfRunTaskService perfRunTaskService;

    /**
     * 触发某日 KPI 计算 (V1.1 交付).
     *
     * @param dataDate 数据日期
     * @return 任务 ID
     * @throws UnsupportedOperationException V1.0 未实现
     */
    @Override
    public String triggerKpiCalc(LocalDate dataDate) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    /**
     * 触发历史回算 (V1.1 交付).
     *
     * @return 任务 ID
     * @throws UnsupportedOperationException V1.0 未实现
     */
    @Override
    public String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator) {
        throw new UnsupportedOperationException("V1.1 delivered");
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
