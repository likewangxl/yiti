package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;

/**
 * 外部数据任务状态上报 API.
 *
 * <p>V1.0 抛 UnsupportedOperationException; V1.1 交付真实实现.
 *
 * <p>调用方: 内部 DataTaskController (REST 层), 最终 REST 接口由独立数据同步系统调用.
 */
public interface DataTaskApi {

    /**
     * 接收外部数据同步任务的完成状态.
     * <p>幂等, 同一 taskId 重复上报返回相同结果.
     * <p>V1.0 抛 UnsupportedOperationException.
     *
     * @param cmd 上报命令
     */
    void reportDataTaskStatus(DataTaskStatusCmd cmd);
}
