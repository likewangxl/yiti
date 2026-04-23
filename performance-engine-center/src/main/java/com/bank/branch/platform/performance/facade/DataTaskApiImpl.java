package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.DataTaskApi;
import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.service.DataTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部数据任务状态上报对外 API 实现（V1.1 Task P6.2）.
 *
 * <p>V1.1 交付状态:
 * <ul>
 *   <li>{@link #reportDataTaskStatus(DataTaskStatusCmd)} Task P6.2 实现：委托
 *       {@link DataTaskService#report(DataTaskStatusCmd)} 完成幂等落库 + 触发管线</li>
 * </ul>
 *
 * <p>Facade 职责（与 {@link PerfCalcApiImpl} 一致的风格）：
 * <ol>
 *   <li>入参 null 兜底（直接 IllegalArgumentException，不走 Service 层异常）</li>
 *   <li>委托 Service 完成核心业务逻辑</li>
 *   <li>原样透传 Service 结果（accepted 语义由 Service 负责）</li>
 * </ol>
 *
 * <p>消费方：内部 REST 层（{@link com.bank.branch.platform.performance.controller.DataTaskController}），
 * 端点 {@code POST /api/data-task/status}，外部独立数据同步系统经 API Token 调用。
 */
@Service
@RequiredArgsConstructor
public class DataTaskApiImpl implements DataTaskApi {

    private final DataTaskService dataTaskService;

    /**
     * 接收外部数据同步任务的完成状态.
     *
     * <p>委托 {@link DataTaskService#report(DataTaskStatusCmd)} 完成：
     * <ul>
     *   <li>以 taskId 幂等查重（既有则 accepted=false 透传）</li>
     *   <li>新建 {@code perf_run_task}（task_type=EXT_DATA）</li>
     *   <li>SUCCESS 场景打标终态 SUCCESS，FAILED 场景打标 FAILED + 回填 errorMsg</li>
     * </ul>
     *
     * @param cmd 上报命令（非空）
     * @return 受理结果
     * @throws IllegalArgumentException cmd 为 null
     */
    @Override
    public DataTaskReportResultDTO reportDataTaskStatus(DataTaskStatusCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("cmd 不能为空");
        }
        return dataTaskService.report(cmd);
    }
}
