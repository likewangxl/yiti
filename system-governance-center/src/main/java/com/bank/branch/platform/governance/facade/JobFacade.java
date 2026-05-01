package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.governance.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 任务调度 Facade 实现（V1.6 quartz 整合后精简）.
 *
 * <p>实现 {@link JobApi}，仅委托 {@link JobService#getJobConf} 这一只读查询方法。
 * Service 层找不到时抛异常，Facade 层捕获后返回 {@link Optional#empty()}，
 * 符合 API 契约中"不存在时返回 Optional.empty()"的约定。
 *
 * <p><strong>历史</strong>：V1.0-V1.5 曾持有 {@code startJobRun} / {@code completeJobRun} /
 * {@code failJobRun} 三个 facade 方法。V1.6 quartz 整合后，写日志由
 * {@code JobExecutionLogger} 直接调用 {@code JobConfMapper} / {@code JobRunLogMapper}
 * 完成，不再走 facade。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobFacade implements JobApi {

    private final JobService jobService;

    /**
     * 获取任务配置.
     * Service 层找不到时抛异常，Facade 层捕获后返回 {@link Optional#empty()}.
     *
     * @param jobKey 任务唯一标识
     * @return 任务配置 Optional
     */
    @Override
    public Optional<JobConfDTO> getJobConf(String jobKey) {
        try {
            return Optional.of(jobService.getJobConf(jobKey));
        } catch (Exception e) {
            log.debug("[JobFacade.getJobConf] 任务配置不存在 jobKey={}", jobKey);
            return Optional.empty();
        }
    }

    /**
     * 注册（或覆盖）一个调度任务（V1.7 占位，Task 7 替换为真实实现）.
     *
     * @param cmd 注册参数
     * @return sys_job_conf 主键 id
     */
    @Override
    public String registerJob(RegisterJobCmd cmd) {
        throw new UnsupportedOperationException("V1.7 Task 7 实现");
    }

    /**
     * 注销一个调度任务（幂等）（V1.7 占位，Task 7 替换为真实实现）.
     *
     * @param jobKey 任务唯一标识
     */
    @Override
    public void unregisterJob(String jobKey) {
        throw new UnsupportedOperationException("V1.7 Task 7 实现");
    }
}
