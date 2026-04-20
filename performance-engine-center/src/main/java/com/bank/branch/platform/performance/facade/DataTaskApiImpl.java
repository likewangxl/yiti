package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.DataTaskApi;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import org.springframework.stereotype.Service;

/**
 * 外部数据任务状态上报对外 API 实现 (V1.0 占位).
 *
 * <p>V1.0 契约 (spec §5.2.6):
 * <ul>
 *   <li>{@link #reportDataTaskStatus(DataTaskStatusCmd)} V1.1 交付, V1.0 抛
 *       {@link UnsupportedOperationException} ("V1.1 delivered") 占位</li>
 * </ul>
 *
 * <p>消费方 (V1.1): 独立的数据同步系统经 REST 层 (DataTaskController) 触发, V1.0 暂不接入。
 *
 * <p>UOE 消息: "V1.1 delivered", 与 {@link PerfCalcApiImpl} / {@link MetricApiImpl}
 * / {@link KpiApiImpl} 保持一致, 消费方可通过消息串统一识别"V1.1 才交付"的占位方法。
 */
@Service
public class DataTaskApiImpl implements DataTaskApi {

    /**
     * 接收外部数据同步任务的完成状态 (V1.1 交付).
     *
     * @param cmd 上报命令
     * @throws UnsupportedOperationException V1.0 未实现
     */
    @Override
    public void reportDataTaskStatus(DataTaskStatusCmd cmd) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }
}
