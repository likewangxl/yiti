package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;

import java.util.Optional;

/**
 * 任务调度对外 API（V1.6 quartz 整合后精简）.
 *
 * <p>除任务配置查询、注册/注销外，提供受限的按固定 {@code jobKey} 触发能力。业务模块必须
 * 在自己的服务层完成 jobKey 白名单映射，不能把前端传入的任意任务标识直接透传到本 API。
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

    /**
     * 按已由业务模块白名单校验的 jobKey 触发 Quartz JobDetail。
     *
     * <p>治理中心负责再次校验任务存在、{@code allow_manual_trigger=1} 和 Scheduler 可用，
     * 并把 triggerType/operatorEmpId/reason 等参数交给全局 {@code JobExecutionLogger} 审计。
     * triggerType 仅允许 {@code MANUAL} 或 {@code AUTO}；AUTO 协调触发可以不带操作人工号。</p>
     *
     * @param jobKey 任务唯一标识，由业务模块固定映射
     * @param triggerType 触发类型 MANUAL/AUTO
     * @param reason 触发原因，手动触发时由上层校验必填
     * @param dataDate 数据日期，可空
     * @param allocDate 业绩分配日期，可空
     * @param operatorEmpId 操作人工号，AUTO 可空
     * @return Quartz 触发响应
     */
    JobTriggerRespDTO triggerJobByKey(String jobKey, String triggerType, String reason,
                                      String dataDate, String allocDate, String operatorEmpId);

    /**
     * 查询任务是否已有 RUNNING 执行日志，供业务协调器避免重复排队。
     *
     * @param jobKey 任务唯一标识
     * @return 任务存在且有 RUNNING 日志时返回 true
     */
    boolean isJobRunning(String jobKey);
}
