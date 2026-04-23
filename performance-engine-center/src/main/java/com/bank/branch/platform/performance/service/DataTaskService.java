package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import org.springframework.stereotype.Service;

/**
 * 外部数据任务上报服务（V1.1 Task P6.2 骨架 / P6.2 Green 实现 / P6.3 幂等增强）.
 *
 * <p>职责：
 * <ul>
 *   <li>P6.2：以 {@code cmd.taskId} 为幂等键落库 {@code perf_run_task}（task_type=EXT_DATA）
 *       SUCCESS 触发后续管线，FAILED 仅记录</li>
 *   <li>P6.3：加固并发幂等（Redis SETNX or DB UK），保证同 taskId 的并发上报只有一次落库胜出</li>
 * </ul>
 *
 * <p>P6.2 Red 阶段本类为骨架，Green 阶段补全实现。
 */
@Service
public class DataTaskService {

    /**
     * 上报入口：幂等落库 + 按状态触发（或记录失败）.
     *
     * @param cmd 上报命令
     * @return 受理结果
     */
    public DataTaskReportResultDTO report(DataTaskStatusCmd cmd) {
        // Task P6.2 Green 阶段实现
        throw new UnsupportedOperationException("V1.1 P6.2 delivered");
    }
}
