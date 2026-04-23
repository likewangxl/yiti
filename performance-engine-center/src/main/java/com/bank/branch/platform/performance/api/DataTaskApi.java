package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;

/**
 * 外部数据任务状态上报 API.
 *
 * <p>V1.0 抛 UnsupportedOperationException; V1.1 Task P6.2 交付真实实现.
 *
 * <p>调用方: 内部 DataTaskController (REST 层), 最终 REST 接口由独立数据同步系统调用.
 */
public interface DataTaskApi {

    /**
     * 接收外部数据同步任务的完成状态.
     *
     * <p>幂等：以 {@code cmd.taskId} 为幂等键，同一 taskId 重复上报返回相同结果，
     * 不重复触发下游计算（Task P6.3 交付幂等实现）。
     *
     * <p>V1.1 Task P6.1（本方法签名从 {@code void} 扩展为返回 {@link DataTaskReportResultDTO}，
     * 以便 Controller 装配响应体；V1.0 DataTaskApiImpl 仍抛 UOE 占位保留）。
     *
     * @param cmd 上报命令
     * @return 受理结果（含 taskId / accepted / perfRunTaskId）
     */
    DataTaskReportResultDTO reportDataTaskStatus(DataTaskStatusCmd cmd);
}
