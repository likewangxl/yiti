package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.JobConfDTO;

import java.util.Optional;

/**
 * 任务调度对外API
 * 供业务模块的定时任务在执行前后上报状态
 */
public interface JobApi {

    /**
     * 获取任务配置
     *
     * @param jobKey 任务唯一标识（如 PERF_DAILY_CALC）
     * @return 任务配置，不存在时返回 Optional.empty()
     */
    Optional<JobConfDTO> getJobConf(String jobKey);

    /**
     * 记录任务执行开始
     * 创建一条 RUNNING 状态的执行日志
     *
     * @param jobId          任务ID
     * @param triggerType    触发类型（SCHEDULED / MANUAL）
     * @param operatorEmpId  触发人工号（SCHEDULED 时传 "SYSTEM"）
     * @return 执行日志ID（后续用于 complete/fail 回调）
     */
    String startJobRun(String jobId, String triggerType, String operatorEmpId);

    /**
     * 记录任务执行结束（成功）
     * 更新执行日志状态为 SUCCESS，设置 end_time
     *
     * @param runLogId 执行日志ID（来自 startJobRun 返回值）
     */
    void completeJobRun(String runLogId);

    /**
     * 记录任务执行结束（失败）
     * 更新执行日志状态为 FAILED，设置 end_time 和 error_msg
     *
     * @param runLogId 执行日志ID（来自 startJobRun 返回值）
     * @param errorMsg 错误信息
     */
    void failJobRun(String runLogId, String errorMsg);
}
