package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
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
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * auth 用户 Api（按工号解析触发人姓名）。
     * <p>{@code required=false}：与 {@link #scheduler} 同理，测试上下文可能无此 bean，
     * 缺失时执行日志仅展示工号、不解析姓名（{@link #enrichOperatorNames}）。</p>
     */
    @Autowired(required = false)
    private UserApi userApi;

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
    /**
     * V1.6：注册 JobDetail + CronTrigger（无 JobDataMap）.
     * V1.7 改为委托 scheduleQuartzJobWithData(conf, null) 单一来源，消除重复代码.
     */
    private void scheduleQuartzJob(SysJobConf job) throws SchedulerException, ClassNotFoundException {
        scheduleQuartzJobWithData(job, null);
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
        enrichOperatorNames(dtos);
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 回填触发人姓名：createdBy 为工号(PT_USER.username)，按工号批量查 auth 用户取姓名.
     *
     * <p>系统/自动触发(createdBy 为空或非真实工号)解析不到时仅展示工号，不阻塞列表；
     * userApi 缺失（测试上下文）时整体跳过。</p>
     */
    private void enrichOperatorNames(List<JobRunLogDTO> dtos) {
        if (userApi == null || dtos == null || dtos.isEmpty()) {
            return;
        }
        List<String> empIds = dtos.stream()
                .map(JobRunLogDTO::getCreatedBy)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (empIds.isEmpty()) {
            return;
        }
        Map<String, String> nameByEmpId = new HashMap<>();
        try {
            List<UserDTO> users = userApi.getUsersByUsernames(empIds);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
                        nameByEmpId.put(u.getUsername(), u.getDisplayName());
                    }
                }
            }
        } catch (Exception ignore) {
            // 按工号解析失败：继续尝试 user_id 兜底
        }
        // created_by 历史上可能存 user_id(如 E40001) 而非工号(username)；按工号未命中的再按 user_id 兜底，
        // 命中后用真实工号(username)覆盖展示值 createdBy（仅改响应、不改落库）并回填姓名，保证前端展示「工号 + 姓名」
        Map<String, UserDTO> userById = new HashMap<>();
        List<String> unresolved = empIds.stream()
                .filter(k -> !nameByEmpId.containsKey(k))
                .collect(Collectors.toList());
        if (!unresolved.isEmpty()) {
            try {
                List<UserDTO> byId = userApi.getUserByEmpIds(unresolved);
                if (byId != null) {
                    for (UserDTO u : byId) {
                        if (u != null && u.getEmpId() != null && !u.getEmpId().isBlank()) {
                            userById.put(u.getEmpId(), u);
                        }
                    }
                }
            } catch (Exception ignore) {
                // user_id 兜底失败：仅展示工号
            }
        }
        for (JobRunLogDTO d : dtos) {
            String cb = d.getCreatedBy();
            if (cb == null || cb.isBlank()) {
                continue;
            }
            if (nameByEmpId.containsKey(cb)) {
                d.setOperatorName(nameByEmpId.get(cb));
            } else if (userById.containsKey(cb)) {
                UserDTO u = userById.get(cb);
                if (u.getUsername() != null && !u.getUsername().isBlank()) {
                    d.setCreatedBy(u.getUsername()); // 用真实工号覆盖展示（落库不变）
                }
                d.setOperatorName(u.getDisplayName());
            }
        }
    }

    /**
     * 暂停任务（V1.6 P3.3：联动 Quartz Scheduler）.
     *
     * <p>核心流程：
     * <ol>
     *   <li>校验 sys_job_conf 存在（不存在抛 GOV-40004）</li>
     *   <li>scheduler null 守护：不可用时显式抛 GOV-50005（区别于 P3.1 启动同步可静默跳过）</li>
     *   <li>jobConfMapper.updateStatus(jobId, "PAUSED") 持久化数据库状态</li>
     *   <li>scheduler.pauseJob(JobKey) 触发 Quartz 暂停；失败时抛 GOV-50005，
     *       由于 @Transactional 的存在，事务回滚保证库状态与 Quartz 一致</li>
     * </ol>
     *
     * <p>事务语义：mapper.updateStatus 与 scheduler.pauseJob 在 {@code @Transactional}
     * 作用域内，scheduler.pauseJob 抛异常会触发 BizException → 事务回滚，库状态不会
     * 残留 PAUSED。</p>
     *
     * @param jobId 任务ID
     * @throws BizException GOV-40004 任务不存在
     * @throws BizException GOV-50005 Scheduler 不可用 / SchedulerException
     */
    @Transactional
    public void pauseJob(String jobId) {
        log.info("[JobService.pauseJob] jobId={}", jobId);
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }
        if (scheduler == null) {
            throw new BizException(GovErrorCode.JOB_PAUSE_FAILED.getCode(),
                    "Scheduler 未启用，无法暂停任务");
        }
        jobConfMapper.updateStatus(jobId, JobStatus.PAUSED.getCode());
        try {
            scheduler.pauseJob(JobKey.jobKey(conf.getJobKey(), "DEFAULT"));
        } catch (SchedulerException e) {
            log.error("[JobService.pauseJob] scheduler.pauseJob 失败 jobKey={}", conf.getJobKey(), e);
            throw new BizException(GovErrorCode.JOB_PAUSE_FAILED.getCode(),
                    "暂停失败: " + e.getMessage());
        }
        log.info("[JobService.pauseJob] 任务已暂停 jobId={}", jobId);
    }

    /**
     * 恢复任务（V1.6 P3.3：联动 Quartz Scheduler）.
     *
     * <p>核心流程参见 {@link #pauseJob(String)}，对称地调用
     * {@code mapper.updateStatus(jobId, "ACTIVE")} + {@code scheduler.resumeJob}.</p>
     *
     * @param jobId 任务ID
     * @throws BizException GOV-40004 任务不存在
     * @throws BizException GOV-50006 Scheduler 不可用 / SchedulerException
     */
    @Transactional
    public void resumeJob(String jobId) {
        log.info("[JobService.resumeJob] jobId={}", jobId);
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }
        if (scheduler == null) {
            throw new BizException(GovErrorCode.JOB_RESUME_FAILED.getCode(),
                    "Scheduler 未启用，无法恢复任务");
        }
        jobConfMapper.updateStatus(jobId, JobStatus.ACTIVE.getCode());
        try {
            scheduler.resumeJob(JobKey.jobKey(conf.getJobKey(), "DEFAULT"));
        } catch (SchedulerException e) {
            log.error("[JobService.resumeJob] scheduler.resumeJob 失败 jobKey={}", conf.getJobKey(), e);
            throw new BizException(GovErrorCode.JOB_RESUME_FAILED.getCode(),
                    "恢复失败: " + e.getMessage());
        }
        log.info("[JobService.resumeJob] 任务已恢复 jobId={}", jobId);
    }

    /**
     * 手动触发任务（V1.6 P3.2：走 Quartz Scheduler 路径）.
     *
     * <p>核心流程：
     * <ol>
     *   <li>校验 sys_job_conf 中存在</li>
     *   <li>校验 allow_manual_trigger=1（不允许手动触发的任务直接拒绝）</li>
     *   <li>scheduler null 守护：用户主动触发场景下 scheduler 不可用必须显式抛
     *       {@link GovErrorCode#JOB_TRIGGER_FAILED}（区别于 P3.1 启动同步可静默跳过）</li>
     *   <li>构造 JobDataMap：triggerType=MANUAL + operatorEmpId + 可选 triggerReason，
     *       键名与 {@link com.bank.branch.platform.governance.listener.JobExecutionLogger#jobToBeExecuted}
     *       读取的 key 严格一致</li>
     *   <li>scheduler.triggerJob(JobKey, JobDataMap) 立即触发；JobListener 异步写入 RUNNING 日志</li>
     * </ol>
     *
     * @param jobId         任务ID
     * @param reason        触发原因（透传到 dataMap.triggerReason）
     * @param operatorEmpId 操作人工号（来自 SecurityContext）
     * @return 触发响应DTO（jobId / triggerType=MANUAL / triggerTime）
     * @throws BizException GOV-40004 任务不存在
     * @throws BizException GOV-40302 任务不允许手动触发（P3.3 修复 P3.2 错误码语义错配，原误用 GOV-40903）
     * @throws BizException GOV-50004 Scheduler 不可用 / SchedulerException
     */
    public JobTriggerRespDTO triggerJob(String jobId, String reason, String dataDate, String operatorEmpId) {
        log.info("[JobService.triggerJob] jobId={}, reason={}, dataDate={}, operatorEmpId={}",
                jobId, reason, dataDate, operatorEmpId);

        // 1. 校验任务配置存在
        SysJobConf conf = jobConfMapper.selectById(jobId);
        if (conf == null) {
            throw new BizException(GovErrorCode.TASK_NOT_FOUND.getCode(),
                    GovErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 2. 校验是否允许手动触发（P3.3 修复 P3.2 错误码语义错配：
        //    原误用 TASK_ALREADY_RUNNING (GOV-40903)，与抛出消息严重不符；
        //    改为新增的 JOB_MANUAL_NOT_ALLOWED (GOV-40302)，403 Forbidden 语义）
        if (conf.getAllowManualTrigger() == null || conf.getAllowManualTrigger() != 1) {
            throw new BizException(GovErrorCode.JOB_MANUAL_NOT_ALLOWED.getCode(),
                    GovErrorCode.JOB_MANUAL_NOT_ALLOWED.getMessage());
        }

        // 3. scheduler null 守护：用户主动触发必须显式失败（不像 P3.1 启动同步可静默跳过）
        if (scheduler == null) {
            throw new BizException(GovErrorCode.JOB_TRIGGER_FAILED.getCode(),
                    "Scheduler 未启用，无法手动触发任务");
        }

        // 4. 构造 JobDataMap：键名与 JobExecutionLogger 读取保持一致
        JobDataMap data = new JobDataMap();
        data.put("triggerType", "MANUAL");
        data.put("operatorEmpId", operatorEmpId);
        if (reason != null && !reason.isBlank()) {
            // reason 透传到 dataMap，JobListener 可写入 sys_job_run_log.reason
            data.put("triggerReason", reason);
        }
        if (dataDate != null && !dataDate.isBlank()) {
            // dataDate 透传到 dataMap：计算类 Quartz Job 读取后按指定数据日期启动计算
            data.put("dataDate", dataDate.trim());
        }

        // 5. 立即触发（JobKey 组与 P3.1 syncJobsOnStartup 一致：DEFAULT）
        try {
            scheduler.triggerJob(JobKey.jobKey(conf.getJobKey(), "DEFAULT"), data);
        } catch (SchedulerException e) {
            log.error("[JobService.triggerJob] scheduler.triggerJob 失败 jobKey={}", conf.getJobKey(), e);
            throw new BizException(GovErrorCode.JOB_TRIGGER_FAILED.getCode(),
                    "触发失败: " + e.getMessage());
        }

        return JobTriggerRespDTO.builder()
                .jobId(jobId)
                .triggerType("MANUAL")
                .jobKey(conf.getJobKey())
                .triggerTime(LocalDateTime.now().format(ISO_FORMATTER))
                .build();
    }

    // ── V1.7 新增：声明式注册 / 注销 ─────────────────────────────────

    /**
     * V1.7 新增：注册（或覆盖）调度任务.
     *
     * <p>原子写入 sys_job_conf 一行 + Quartz Scheduler 注入 JobDetail/CronTrigger。
     * 若 jobKey 已存在则覆盖（cron 变更场景）。
     * 若 Scheduler 不可用（测试上下文）则仅写 sys_job_conf，不抛异常。
     *
     * @param cmd 注册参数
     * @return 写入后 sys_job_conf 主键 id
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50010 cron 非法
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50011 quartz_job_class 反射失败
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50012 Scheduler 注册失败
     */
    @Transactional
    public String registerJob(RegisterJobCmd cmd) {
        log.info("[JobService.registerJob] jobKey={} cronExpr={}", cmd.getJobKey(), cmd.getCronExpr());

        // 1. 校验 cron 表达式
        if (!CronExpression.isValidExpression(cmd.getCronExpr())) {
            throw new BizException(
                GovErrorCode.JOB_CRON_INVALID.getCode(),
                GovErrorCode.JOB_CRON_INVALID.getMessage());
        }

        // 2. 反射校验 quartzJobClass 存在且是 Job 子类
        Class<?> jobClass;
        try {
            jobClass = Class.forName(cmd.getQuartzJobClass());
            if (!Job.class.isAssignableFrom(jobClass)) {
                throw new BizException(
                    GovErrorCode.JOB_CLASS_NOT_FOUND.getCode(),
                    GovErrorCode.JOB_CLASS_NOT_FOUND.getMessage() + ": 不是 Job 子类");
            }
        } catch (ClassNotFoundException | LinkageError e) {
            throw new BizException(
                GovErrorCode.JOB_CLASS_NOT_FOUND.getCode(),
                GovErrorCode.JOB_CLASS_NOT_FOUND.getMessage() + ": " + e.getMessage());
        }

        // 3. upsert sys_job_conf
        SysJobConf conf = jobConfMapper.selectByJobKey(cmd.getJobKey());
        boolean isInsert = (conf == null);
        if (isInsert) {
            conf = new SysJobConf();
            conf.setId(UUID.randomUUID().toString().replace("-", ""));
            conf.setCreatedBy("SYSTEM");
            conf.setCreatedTime(LocalDateTime.now());
        }
        conf.setJobKey(cmd.getJobKey());
        conf.setJobName(cmd.getJobName());
        conf.setCronExpr(cmd.getCronExpr());
        conf.setQuartzJobClass(cmd.getQuartzJobClass());
        conf.setMisfirePolicy(cmd.getMisfirePolicy());
        conf.setStatus(JobStatus.ACTIVE.getCode());
        conf.setAllowManualTrigger(cmd.isAllowManualTrigger() ? 1 : 0);
        conf.setRemark(cmd.getRemark());
        conf.setUpdatedBy("SYSTEM");
        conf.setUpdatedTime(LocalDateTime.now());
        if (isInsert) {
            jobConfMapper.insert(conf);
        } else {
            jobConfMapper.updateById(conf);
        }

        // 4. 注册到 Quartz Scheduler（测试上下文 scheduler=null 时跳过）
        if (scheduler != null) {
            try {
                scheduleQuartzJobWithData(conf, cmd.getJobData());
            } catch (Exception e) {
                throw new BizException(
                    GovErrorCode.JOB_REGISTER_FAILED.getCode(),
                    "Scheduler 注册失败: " + e.getMessage());
            }
        }
        return conf.getId();
    }

    /**
     * V1.7 新增：注销调度任务（幂等）.
     *
     * <p>group 通过 {@link #resolveGroup(String)} 推断，不存在时静默返回（Quartz deleteJob 行为）。
     * scheduler 异常时记 warn 后继续删库，确保幂等。
     *
     * @param jobKey 任务唯一标识
     */
    @Transactional
    public void unregisterJob(String jobKey) {
        log.info("[JobService.unregisterJob] jobKey={}", jobKey);
        if (scheduler != null) {
            try {
                scheduler.deleteJob(JobKey.jobKey(jobKey, resolveGroup(jobKey)));
            } catch (SchedulerException e) {
                log.warn("[JobService.unregisterJob] scheduler.deleteJob 失败 jobKey={}", jobKey, e);
            }
        }
        jobConfMapper.deleteByJobKey(jobKey);
    }

    /**
     * V1.7：根据 jobKey 前缀推断 Quartz JobGroup / TriggerGroup.
     *
     * <p>唯一来源，避免 register 与 unregister 双方对 group 的隐式假设不一致.
     *
     * @param jobKey 任务唯一标识
     * @return Quartz group 名称
     */
    private String resolveGroup(String jobKey) {
        return jobKey != null && jobKey.startsWith("PERF_METRIC_") ? "PERF_METRIC" : "DEFAULT";
    }

    /**
     * V1.7 新增：注册 JobDetail + CronTrigger（带 JobDataMap）.
     *
     * <p>与 V1.6 的 {@link #scheduleQuartzJob(SysJobConf)} 区别：支持传入 jobData，
     * 且 group 按 jobKey 前缀自动判断（PERF_METRIC_ 前缀用 PERF_METRIC group，其余用 DEFAULT）。
     *
     * @param conf    任务配置实体
     * @param jobData 透传到 JobDataMap 的额外参数（可为 null）
     */
    @SuppressWarnings("unchecked")
    private void scheduleQuartzJobWithData(SysJobConf conf, Map<String, String> jobData)
            throws SchedulerException, ClassNotFoundException {
        Class<? extends Job> clazz;
        try {
            clazz = (Class<? extends Job>) Class.forName(conf.getQuartzJobClass());
        } catch (LinkageError e) {
            throw new ClassNotFoundException("LinkageError loading " + conf.getQuartzJobClass(), e);
        }
        String group = resolveGroup(conf.getJobKey());
        JobDataMap dataMap = new JobDataMap();
        if (jobData != null) {
            dataMap.putAll(jobData);
        }
        JobDetail detail = JobBuilder.newJob(clazz)
            .withIdentity(conf.getJobKey(), group)
            .usingJobData(dataMap)
            .storeDurably()
            .build();
        CronScheduleBuilder cron = applyMisfirePolicy(
            CronScheduleBuilder.cronSchedule(conf.getCronExpr()), conf.getMisfirePolicy());
        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(conf.getJobKey() + "_TRIGGER", group)
            .withSchedule(cron)
            .forJob(detail)
            .build();
        // V1.13 # 1i（2026-05-02）：scheduler.scheduleJob 不带覆盖语义，
        // 已存在 JobKey 会抛 ObjectAlreadyExistsException → GOV-50012。
        // V1.7 spec 要求"jobKey 已存在则覆盖（cron 变更场景）"，业务模块（如 perf 指标 update）
        // 也是同 jobKey 重复 register 的场景。这里先 deleteJob 做 idempotent 前置，
        // 让 register 真正满足"覆盖"语义（同等价于先 unregister 再 register）。
        scheduler.deleteJob(JobKey.jobKey(conf.getJobKey(), group));
        scheduler.scheduleJob(detail, trigger);
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
        // 处理状态 / 错误原因 来自左连接的 PERF_METRIC_CALC_TASK（kpi_scheme_code / error_msg），
        // 关联不到时为 null，前端渲染为 '—'
        dto.setProcessStatus(entity.getProcessStatus());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }
}
