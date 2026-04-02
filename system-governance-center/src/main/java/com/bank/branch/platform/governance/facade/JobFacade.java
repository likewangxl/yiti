package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 任务调度 Facade 实现
 * <p>
 * 实现 JobApi 接口，委托给 JobService 处理业务逻辑。
 * getJobConf 方法将 Service 层的异常转换为 Optional.empty()，
 * 符合 API 契约中"不存在时返回 Optional.empty()"的约定。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobFacade implements JobApi {

    private final JobService jobService;

    /**
     * 获取任务配置
     * Service 层找不到时抛异常，Facade 层捕获后返回 Optional.empty()
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
     * 记录任务执行开始
     *
     * @param jobId         任务ID
     * @param triggerType   触发类型
     * @param operatorEmpId 触发人工号
     * @return 执行日志ID
     */
    @Override
    public String startJobRun(String jobId, String triggerType, String operatorEmpId) {
        return jobService.startJobRun(jobId, triggerType, operatorEmpId);
    }

    /**
     * 记录任务执行结束（成功）
     *
     * @param runLogId 执行日志ID
     */
    @Override
    public void completeJobRun(String runLogId) {
        jobService.completeJobRun(runLogId);
    }

    /**
     * 记录任务执行结束（失败）
     *
     * @param runLogId 执行日志ID
     * @param errorMsg 错误信息
     */
    @Override
    public void failJobRun(String runLogId, String errorMsg) {
        jobService.failJobRun(runLogId, errorMsg);
    }
}
