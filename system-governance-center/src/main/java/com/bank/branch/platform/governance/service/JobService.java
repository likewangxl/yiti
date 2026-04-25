package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobRunLogDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.enums.JobRunStatus;
import com.bank.branch.platform.governance.enums.JobStatus;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.quartz.CronScheduleBuilder.cronSchedule;

/**
 * 任务调度服务
 * <p>
 * 负责任务配置的查询和状态管理，以及任务执行日志的生命周期管理。
 * 核心业务规则：startJobRun 中通过 existsRunningByJobId 实现并发防控，
 * 同一任务同时只允许一条 RUNNING 状态的执行日志。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobConfMapper jobConfMapper;
    private final JobRunLogMapper jobRunLogMapper;

    /**
     * Quartz Scheduler bean（V1.6 P3.1 启动同步引入）。
     * <p>使用 {@code @Autowired(required = false)}：测试上下文（如 application-test.yml）已通过
     * {@code spring.autoconfigure.exclude=QuartzAutoConfiguration} 禁用 Quartz，此时 bean 不存在；
     * {@link #syncJobsOnStartup()} 会检查 null 并跳过同步，避免阻塞 ApplicationContext 加载。</p>
     */
    @Autowired(required = false)
    private Scheduler scheduler;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── 启动同步（V1.6 quartz 整合 P3.1） ──────────────────────────

    /**
     * 应用启动时遍历 sys_job_conf 表 status='ACTIVE' 的任务并注册到 Quartz Scheduler.
     *
     * <p>调用时机：Spring 完成依赖注入后通过 {@link PostConstruct} 自动触发。
     *
     * <p>容错语义：单条任务同步失败（如 quartz_job_class 反射失败、cron 表达式非法、
     * scheduler.scheduleJob 异常）记 {@code log.error} 后继续处理下一条，整体方法不抛异常，
     * 避免一条配置错误导致整个应用启动失败。
     *
     * <p>测试/无 Scheduler 场景：{@link #scheduler} 为 null 时直接跳过（应用上下文不集成 Quartz）.
     *
     * <p>覆盖语义：依赖 Quartz {@code overwriteExistingJobs=true}（默认开启）实现 cron 变更后
     * 重启自动覆盖旧的 JobDetail/Trigger，无需删除再注册。
     */
    @PostConstruct
    public void syncJobsOnStartup() {
        if (scheduler == null) {
            log.warn("[JobService.syncJobsOnStartup] Scheduler bean 不可用（测试或禁用 Quartz 场景），跳过启动同步");
            return;
        }
        List<SysJobConf> activeJobs = jobConfMapper.selectByStatus(JobStatus.ACTIVE.getCode());
        if (activeJobs == null || activeJobs.isEmpty()) {
            log.info("[JobService.syncJobsOnStartup] 无 ACTIVE 任务需同步，跳过");
            return;
        }
        int success = 0;
        int failed = 0;
        for (SysJobConf job : activeJobs) {
            try {
                scheduleQuartzJob(job);
                success++;
            } catch (Exception e) {
                log.error("[JobService.syncJobsOnStartup] jobKey={} 同步失败，跳过继续",
                        job.getJobKey(), e);
                failed++;
            }
        }
        log.info("[JobService.syncJobsOnStartup] 完成：成功={}, 失败={}", success, failed);
    }

    /**
     * 按单条 SysJobConf 注册 Quartz JobDetail + CronTrigger 到 Scheduler.
     *
     * <p>反射加载 {@code quartz_job_class}，按 {@code cron_expr} 构造 CronScheduleBuilder，
     * 应用 {@code misfire_policy} 后注册。同 jobKey 重复注册时由 Scheduler 默认覆盖
     * （SchedulerFactoryBean 默认 overwriteExistingJobs=true，cron 变更场景免删除）.
     *
     * @param job 任务配置实体
     * @throws SchedulerException     Scheduler 注册异常
     * @throws ClassNotFoundException quartz_job_class 反射加载失败
     */
    @SuppressWarnings("unchecked")
    private void scheduleQuartzJob(SysJobConf job) throws SchedulerException, ClassNotFoundException {
        Class<? extends Job> clazz = (Class<? extends Job>) Class.forName(job.getQuartzJobClass());
        JobDetail detail = JobBuilder.newJob(clazz)
                .withIdentity(job.getJobKey(), "DEFAULT")
                .storeDurably()
                .build();
        CronScheduleBuilder cron = applyMisfirePolicy(
                cronSchedule(job.getCronExpr()), job.getMisfirePolicy());
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(job.getJobKey() + "_TRIGGER", "DEFAULT")
                .withSchedule(cron)
                .forJob(detail)
                .build();
        scheduler.scheduleJob(detail, trigger);
    }

    /**
     * 把 sys_job_conf.misfire_policy 字符串映射到 Quartz CronScheduleBuilder 的 misfire 处理指令.
     *
     * <ul>
     *   <li>FIRE_ONCE_NOW          → withMisfireHandlingInstructionFireAndProceed</li>
     *   <li>DO_NOTHING             → withMisfireHandlingInstructionDoNothing</li>
     *   <li>IGNORE_MISFIRE_POLICY  → withMisfireHandlingInstructionIgnoreMisfires</li>
     * </ul>
     *
     * @param builder 原始 CronScheduleBuilder
     * @param policy  misfire 策略字符串
     * @return 应用 misfire 策略后的 CronScheduleBuilder
     * @throws IllegalArgumentException policy 不在已知三档内
     */
    private CronScheduleBuilder applyMisfirePolicy(CronScheduleBuilder builder, String policy) {
        return switch (policy) {
            case "FIRE_ONCE_NOW" -> builder.withMisfireHandlingInstructionFireAndProceed();
            case "DO_NOTHING" -> builder.withMisfireHandlingInstructionDoNothing();
            case "IGNORE_MISFIRE_POLICY" -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> throw new IllegalArgumentException("未知 misfire_policy: " + policy);
        };
    }

    // ── 业务方法 ───────────────────────────────────────────────

    /**
     * 获取任务配置
     *
     * @param jobKey 任务唯一标识
     * @return 任务配置 DTO
     * @throws BizException GOV-40004 任务不存在
     */
    public JobConfDTO getJobConf(String jobKey) {
        log.info("[JobService.getJobConf] jobKey={}", jobKey);
        SysJobConf conf = jobConfMapper.selectByJobKey(jobKey);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }
        return toJobConfDTO(conf);
    }

    /**
     * 记录任务执行开始，创建一条 RUNNING 状态的执行日志
     * <p>
     * 业务规则：同一任务同时只允许一条 RUNNING 日志（并发防控）。
     * 若已存在 RUNNING 日志则抛出 GOV-40903。
     * </p>
     *
     * @param jobId         任务ID
     * @param triggerType   触发类型（SCHEDULED/MANUAL）
     * @param operatorEmpId 触发人工号（SCHEDULED 时传 "SYSTEM"）
     * @return 执行日志ID
     * @throws BizException GOV-40004 任务不存在
     * @throws BizException GOV-40903 任务正在执行中（并发防控）
     */
    @Transactional
    public String startJobRun(String jobId, String triggerType, String operatorEmpId) {
        log.info("[JobService.startJobRun] jobId={}, triggerType={}, operator={}", jobId, triggerType, operatorEmpId);

        // 1. 校验任务配置存在
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 2. 并发防控：检查是否有 RUNNING 中的日志
        if (jobRunLogMapper.existsRunningByJobId(jobId)) {
            throw new BizException(GovErrorCode.TASK_ALREADY_RUNNING.getCode(),
                    GovErrorCode.TASK_ALREADY_RUNNING.getMessage());
        }

        // 3. 创建执行日志
        String logId = "JRL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        SysJobRunLog runLog = new SysJobRunLog();
        runLog.setId(logId);
        runLog.setJobId(jobId);
        runLog.setTriggerType(triggerType);
        runLog.setStatus(JobRunStatus.RUNNING.getCode());
        runLog.setStartTime(LocalDateTime.now());
        runLog.setCreatedBy(operatorEmpId);
        runLog.setCreatedTime(LocalDateTime.now());
        jobRunLogMapper.insert(runLog);

        // 4. 更新任务最后执行时间
        conf.setLastRunTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        jobConfMapper.updateById(conf);

        log.info("[JobService.startJobRun] 执行日志已创建 logId={}", logId);
        return logId;
    }

    /**
     * 记录任务执行结束（成功）
     *
     * @param runLogId 执行日志ID
     * @throws BizException GOV-40007 执行日志不存在
     */
    @Transactional
    public void completeJobRun(String runLogId) {
        log.info("[JobService.completeJobRun] runLogId={}", runLogId);
        SysJobRunLog runLog = jobRunLogMapper.selectById(runLogId);
        if (runLog == null) {
            throw new BizException(GovErrorCode.TASK_LOG_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_LOG_NOT_FOUND.getMessage());
        }
        runLog.setStatus(JobRunStatus.SUCCESS.getCode());
        runLog.setEndTime(LocalDateTime.now());
        jobRunLogMapper.updateById(runLog);
        log.info("[JobService.completeJobRun] 任务执行成功 runLogId={}", runLogId);
    }

    /**
     * 记录任务执行结束（失败）
     *
     * @param runLogId 执行日志ID
     * @param errorMsg 错误信息
     * @throws BizException GOV-40007 执行日志不存在
     */
    @Transactional
    public void failJobRun(String runLogId, String errorMsg) {
        log.info("[JobService.failJobRun] runLogId={}, errorMsg={}", runLogId, errorMsg);
        SysJobRunLog runLog = jobRunLogMapper.selectById(runLogId);
        if (runLog == null) {
            throw new BizException(GovErrorCode.TASK_LOG_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_LOG_NOT_FOUND.getMessage());
        }
        runLog.setStatus(JobRunStatus.FAILED.getCode());
        runLog.setEndTime(LocalDateTime.now());
        runLog.setErrorMsg(errorMsg);
        jobRunLogMapper.updateById(runLog);
        log.info("[JobService.failJobRun] 任务执行失败 runLogId={}", runLogId);
    }

    /**
     * 分页查询任务配置列表
     *
     * @param keyword  关键词（模糊匹配 job_key / job_name）
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<JobConfDTO> listJobs(String keyword, int pageNo, int pageSize) {
        log.debug("[JobService.listJobs] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        long total = jobConfMapper.countByPage(keyword);
        List<SysJobConf> records = jobConfMapper.selectByPage(keyword, offset, pageSize);
        List<JobConfDTO> dtos = records.stream()
                .map(this::toJobConfDTO)
                .collect(Collectors.toList());
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 分页查询任务执行日志
     *
     * @param jobId    任务ID
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<JobRunLogDTO> listRunLogs(String jobId, int pageNo, int pageSize) {
        log.debug("[JobService.listRunLogs] jobId={}, pageNo={}, pageSize={}", jobId, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        long total = jobRunLogMapper.countByJobId(jobId);
        List<SysJobRunLog> records = jobRunLogMapper.selectByJobId(jobId, offset, pageSize);
        List<JobRunLogDTO> dtos = records.stream()
                .map(this::toRunLogDTO)
                .collect(Collectors.toList());
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 暂停任务
     *
     * @param jobId 任务ID
     * @throws BizException GOV-40004 任务不存在
     */
    @Transactional
    public void pauseJob(String jobId) {
        log.info("[JobService.pauseJob] jobId={}", jobId);
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }
        conf.setStatus(JobStatus.PAUSED.getCode());
        conf.setUpdatedTime(LocalDateTime.now());
        jobConfMapper.updateById(conf);
        log.info("[JobService.pauseJob] 任务已暂停 jobId={}", jobId);
    }

    /**
     * 恢复任务
     *
     * @param jobId 任务ID
     * @throws BizException GOV-40004 任务不存在
     */
    @Transactional
    public void resumeJob(String jobId) {
        log.info("[JobService.resumeJob] jobId={}", jobId);
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }
        conf.setStatus(JobStatus.ACTIVE.getCode());
        conf.setUpdatedTime(LocalDateTime.now());
        jobConfMapper.updateById(conf);
        log.info("[JobService.resumeJob] 任务已恢复 jobId={}", jobId);
    }

    /**
     * 手动触发任务（返回响应DTO）
     * <p>
     * 基于 startJobRun 方法，增加返回 JobTriggerRespDTO，包含日志ID、任务KEY和触发时间。
     * </p>
     *
     * @param jobId   任务ID
     * @param reason  触发原因
     * @return 触发响应DTO
     * @throws BizException GOV-40004 任务不存在
     * @throws BizException GOV-40901 任务正在执行中
     */
    public JobTriggerRespDTO triggerJob(String jobId, String reason) {
        log.info("[JobService.triggerJob] jobId={}, reason={}", jobId, reason);

        // 获取任务配置
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 检查是否允许手动触发
        if (conf.getAllowManualTrigger() == null || conf.getAllowManualTrigger() != 1) {
            throw new BizException(GovErrorCode.TASK_ALREADY_RUNNING.getCode(),
                    "该任务不允许手动触发");
        }

        // 启动执行
        String runLogId = startJobRun(jobId, "MANUAL", "SYSTEM");

        // 返回响应
        JobTriggerRespDTO resp = new JobTriggerRespDTO();
        resp.setRunLogId(runLogId);
        resp.setJobKey(conf.getJobKey());
        resp.setTriggerTime(LocalDateTime.now().format(ISO_FORMATTER));
        return resp;
    }

    // ── 私有方法：实体 → DTO 转换 ──────────────────────────────────

    /**
     * 将 SysJobConf 实体转换为 JobConfDTO
     *
     * @param entity 任务配置实体
     * @return JobConfDTO
     */
    private JobConfDTO toJobConfDTO(SysJobConf entity) {
        JobConfDTO dto = new JobConfDTO();
        dto.setId(entity.getId());
        dto.setJobKey(entity.getJobKey());
        dto.setJobName(entity.getJobName());
        dto.setCronExpr(entity.getCronExpr());
        dto.setStatus(entity.getStatus());
        dto.setAllowManualTrigger(entity.getAllowManualTrigger() != null && entity.getAllowManualTrigger() == 1);
        dto.setLastRunTime(entity.getLastRunTime() != null ? entity.getLastRunTime().format(ISO_FORMATTER) : null);
        dto.setNextRunTime(entity.getNextRunTime() != null ? entity.getNextRunTime().format(ISO_FORMATTER) : null);
        dto.setRemark(entity.getRemark());
        return dto;
    }

    /**
     * 将 SysJobRunLog 实体转换为 JobRunLogDTO
     *
     * @param entity 执行日志实体
     * @return JobRunLogDTO
     */
    private JobRunLogDTO toRunLogDTO(SysJobRunLog entity) {
        JobRunLogDTO dto = new JobRunLogDTO();
        dto.setId(entity.getId());
        dto.setJobId(entity.getJobId());
        dto.setTriggerType(entity.getTriggerType());
        dto.setReason(entity.getReason());
        dto.setStartTime(entity.getStartTime() != null ? entity.getStartTime().format(ISO_FORMATTER) : null);
        dto.setEndTime(entity.getEndTime() != null ? entity.getEndTime().format(ISO_FORMATTER) : null);
        dto.setStatus(entity.getStatus());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }
}
