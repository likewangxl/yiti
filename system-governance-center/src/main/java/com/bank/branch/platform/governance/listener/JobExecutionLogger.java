package com.bank.branch.platform.governance.listener;

import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Quartz Job 执行日志监听器（V1.6 quartz 整合引入）.
 *
 * <p>注册为全局 JobListener（QuartzConfig.setGlobalJobListeners）.
 *
 * <p><strong>异常隔离</strong>：所有内部异常 try-catch + log，不向 Quartz 抛出，
 * 防止治理日志失败影响业务调度（与 GovAuditLogHandler "审计写入失败不阻塞主业务" 模式一致）。
 *
 * <p><strong>触发类型识别</strong>：
 * <ul>
 *   <li>JobDataMap 含 "triggerType"="MANUAL" → MANUAL（手动触发，operatorEmpId 取自 dataMap）</li>
 *   <li>否则 → SCHEDULED（自动调度，created_by="SYSTEM"）</li>
 * </ul>
 *
 * <p><strong>错误信息长度</strong>：errorMsg 截断到 4000 字符，
 * 防止 Stack 巨大撑爆 sys_job_run_log.error_msg TEXT 列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionLogger implements JobListener {

    /** errorMsg 最大长度（字符），超出截断 */
    private static final int ERROR_MSG_MAX_LEN = 4000;

    /** 默认系统触发人标识，用于 SCHEDULED 类型 */
    private static final String SYSTEM_TRIGGER = "SYSTEM";

    private final JobConfMapper jobConfMapper;
    private final JobRunLogMapper runLogMapper;

    @Override
    public String getName() {
        return "JobExecutionLogger";
    }

    /**
     * Job 即将执行回调：写入 RUNNING 状态的执行日志，并把 runLogId 放入 context 供后续阶段使用。
     *
     * <p>异常隔离：内部捕获并记录所有异常，不向 Quartz 抛出，确保业务执行不受治理日志失败影响。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        try {
            String jobKey = context.getJobDetail().getKey().getName();
            SysJobConf jobConf = jobConfMapper.selectByJobKey(jobKey);
            if (jobConf == null) {
                log.warn("[JobExecutionLogger] jobKey={} 在 sys_job_conf 表中不存在，跳过日志记录", jobKey);
                return;
            }

            String triggerType = (String) context.getMergedJobDataMap()
                    .getOrDefault("triggerType", "SCHEDULED");
            String operatorEmpId = (String) context.getMergedJobDataMap()
                    .getOrDefault("operatorEmpId", SYSTEM_TRIGGER);
            String triggerReason = (String) context.getMergedJobDataMap().get("triggerReason");

            SysJobRunLog runLog = new SysJobRunLog();
            runLog.setId(UUID.randomUUID().toString().replace("-", ""));
            runLog.setJobId(jobConf.getId());
            runLog.setTriggerType(triggerType);
            runLog.setReason(triggerReason);
            runLog.setStatus("RUNNING");
            LocalDateTime now = LocalDateTime.now();
            runLog.setStartTime(now);
            runLog.setCreatedBy(operatorEmpId);
            runLog.setCreatedTime(now);
            // scheduled_fire_time 由 Quartz context 提供（用于 misfire 排查）
            Date scheduledFireTime = context.getScheduledFireTime();
            if (scheduledFireTime != null) {
                runLog.setScheduledFireTime(
                        LocalDateTime.ofInstant(scheduledFireTime.toInstant(), ZoneId.systemDefault()));
            }

            runLogMapper.insert(runLog);
            context.put("runLogId", runLog.getId());

        } catch (Exception e) {
            // 治理日志失败不影响业务调度（与 GovAuditLogHandler 模式一致）
            log.error("[JobExecutionLogger] 写入 RUNNING 日志失败，jobKey={}, 业务执行不受影响",
                    context.getJobDetail().getKey(), e);
        }
    }

    /**
     * Job 执行完成回调：根据是否抛出异常更新日志状态为 SUCCESS / FAILED，并同步更新 sys_job_conf.last_run_time。
     *
     * <p>异常隔离：内部捕获并记录所有异常，确保业务执行结果不受治理日志失败影响。
     *
     * @param context      Quartz 执行上下文
     * @param jobException Job 执行抛出的异常，null 表示成功
     */
    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        try {
            String runLogId = (String) context.get("runLogId");
            if (runLogId == null) {
                log.warn("[JobExecutionLogger] context 中无 runLogId，跳过结束日志更新");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (jobException == null) {
                runLogMapper.updateSuccess(runLogId, now);
            } else {
                String errorMsg = ExceptionUtils.getStackTrace(jobException);
                if (errorMsg.length() > ERROR_MSG_MAX_LEN) {
                    errorMsg = errorMsg.substring(0, ERROR_MSG_MAX_LEN);
                }
                runLogMapper.updateFailed(runLogId, now, errorMsg);
            }
            // 同步更新 sys_job_conf.last_run_time（最近一次实际触发时间）
            String jobKey = context.getJobDetail().getKey().getName();
            jobConfMapper.updateLastRunTime(jobKey, now);

        } catch (Exception e) {
            log.error("[JobExecutionLogger] 更新结果日志失败，runLogId={}, 业务执行结果未持久化",
                    context.get("runLogId"), e);
        }
    }

    /**
     * Job 被 TriggerListener veto 回调：本项目暂不使用 TriggerListener veto，no-op。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // no-op：本项目不使用 TriggerListener veto
    }
}
