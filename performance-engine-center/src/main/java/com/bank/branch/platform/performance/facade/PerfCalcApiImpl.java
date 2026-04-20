package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.PerfCalcApi;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效计算触发对外 API 实现 (骨架, 实现在绿阶段).
 *
 * <p>V1.0 契约 (spec §5.2.5):
 * <ul>
 *   <li>{@link #getRunTask(String)} V1.0 实现: 读 perf_run_task, 不缓存 (任务状态属业务数据, 非配置)</li>
 *   <li>{@link #triggerKpiCalc(LocalDate)} V1.1 UOE 占位</li>
 *   <li>{@link #triggerRecalc(String, LocalDate, LocalDate, String, String)} V1.1 UOE 占位</li>
 * </ul>
 *
 * <p>缓存策略: getRunTask 不缓存, 直接穿透 Service
 * (任务状态是业务数据, 频繁变动, 缓存收益低且一致性成本高)。
 *
 * <p>消费方 (V1.0): portal-content-center (任务进度查看), 运维后台 (任务审计)。
 */
@Service
@RequiredArgsConstructor
public class PerfCalcApiImpl implements PerfCalcApi {

    private final PerfRunTaskService perfRunTaskService;

    @Override
    public String triggerKpiCalc(LocalDate dataDate) {
        // 红阶段骨架: 绿阶段补 UOE
        return null;
    }

    @Override
    public String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator) {
        // 红阶段骨架: 绿阶段补 UOE
        return null;
    }

    @Override
    public Optional<PerfRunTaskDTO> getRunTask(String taskId) {
        // 红阶段骨架: 绿阶段补实现
        return Optional.empty();
    }
}
