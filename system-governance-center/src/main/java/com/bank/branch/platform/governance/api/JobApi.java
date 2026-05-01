package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;

import java.util.Optional;

/**
 * 任务调度对外 API（V1.6 quartz 整合后精简）.
 *
 * <p><strong>仅保留 {@link #getJobConf} 一个只读查询方法</strong>，供业务模块在需要时
 * 查询 {@code sys_job_conf} 中的任务配置。
 *
 * <p><strong>历史</strong>：V1.0-V1.5 曾持有 {@code startJobRun} / {@code completeJobRun} /
 * {@code failJobRun} 三个写日志方法，由各业务模块的 {@code @Scheduled} 任务在执行前后
 * 显式调用。V1.6 quartz 整合后，写日志改由 {@code JobExecutionLogger}（全局 Quartz
 * {@code JobListener}）在 Job 生命周期回调中统一处理，业务模块不再需要这 3 个方法
 * （spec 决策 #5=B）。
 */
public interface JobApi {

    /**
     * 获取任务配置.
     *
     * @param jobKey 任务唯一标识（如 PERF_DAILY_CALC）
     * @return 任务配置，不存在时返回 {@link Optional#empty()}
     */
    Optional<JobConfDTO> getJobConf(String jobKey);

    /**
     * V1.7 新增：注册（或覆盖）一个调度任务.
     *
     * <p>原子写入 sys_job_conf 一行 + Quartz Scheduler 注入 JobDetail/CronTrigger.
     * 若 jobKey 已存在则覆盖（cron 变更场景）.
     * 若 Scheduler 不可用（测试上下文）则仅写 sys_job_conf 不抛异常.
     *
     * @param cmd 注册参数
     * @return 写入后 sys_job_conf 主键 id
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50010 cron 非法
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50011 quartz_job_class 反射失败
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50012 Scheduler 注册失败
     */
    String registerJob(RegisterJobCmd cmd);

    /**
     * V1.7 新增：注销一个调度任务（幂等）.
     *
     * @param jobKey 任务唯一标识
     */
    void unregisterJob(String jobKey);
}
