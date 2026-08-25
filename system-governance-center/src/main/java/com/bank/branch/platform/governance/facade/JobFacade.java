package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.governance.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 任务调度 Facade 实现（V1.6 quartz 整合后精简）.
 *
 * <p>实现 {@link JobApi}：查询、注册/注销、受限按 jobKey 触发和运行中查询均委托给
 * {@link JobService}。Service 层找不到任务时，只有查询方法捕获异常并返回
 * {@link Optional#empty()}；触发与协调器查询保持治理异常语义。
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
     * 注册（或覆盖）一个调度任务（V1.7）.
     *
     * @param cmd 注册参数
     * @return sys_job_conf 主键 id
     */
    @Override
    public String registerJob(RegisterJobCmd cmd) {
        return jobService.registerJob(cmd);
    }

    /**
     * 注销一个调度任务（幂等）（V1.7）.
     *
     * @param jobKey 任务唯一标识
     */
    @Override
    public void unregisterJob(String jobKey) {
        jobService.unregisterJob(jobKey);
    }

    /**
     * 按业务层白名单 jobKey 触发 Quartz 任务。
     */
    @Override
    public JobTriggerRespDTO triggerJobByKey(String jobKey, String triggerType, String reason,
                                             String dataDate, String allocDate, String operatorEmpId) {
        return jobService.triggerJobByKey(jobKey, triggerType, reason, dataDate, allocDate, operatorEmpId);
    }

    /**
     * 查询任务是否已有运行中的执行日志。
     */
    @Override
    public boolean isJobRunning(String jobKey) {
        return jobService.isJobRunning(jobKey);
    }
}
