package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.DataTaskApi;
import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import org.springframework.stereotype.Service;

/**
 * 外部数据任务状态上报对外 API 实现.
 *
 * <p>V1.1 状态:
 * <ul>
 *   <li>{@link #reportDataTaskStatus(DataTaskStatusCmd)} Task P6.2 交付真实实现（幂等落库 +
 *       触发后续计算管线）; Task P6.1 先保留 UOE 占位，Controller 通过 {@code @MockBean}
 *       隔离 IT 覆盖路由 / DTO 校验 / 注解约束.</li>
 * </ul>
 *
 * <p>UOE 消息: "V1.1 delivered", 与 {@link PerfCalcApiImpl} / {@link MetricApiImpl}
 * / {@link KpiApiImpl} 保持一致, 消费方可通过消息串统一识别"V1.1 才交付"的占位方法。
 */
@Service
public class DataTaskApiImpl implements DataTaskApi {

    /**
     * 接收外部数据同步任务的完成状态 (Task P6.2 真实实现占位).
     *
     * @param cmd 上报命令
     * @return 受理结果
     * @throws UnsupportedOperationException Task P6.2 前暂未实现
     */
    @Override
    public DataTaskReportResultDTO reportDataTaskStatus(DataTaskStatusCmd cmd) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }
}
