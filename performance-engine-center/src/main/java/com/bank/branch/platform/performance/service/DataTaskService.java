package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.enums.RunTaskStatusEnum;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * 外部数据任务上报服务（V1.1 Task P6.2 + P6.3 幂等）.
 *
 * <p>职责：
 * <ul>
 *   <li>以 {@code cmd.taskId} 为幂等键落库 {@code perf_run_task}（task_type=EXT_DATA）</li>
 *   <li>SUCCESS 上报 → run_task 打标 SUCCESS（后续下游编排由独立任务承担）</li>
 *   <li>FAILED 上报 → run_task 打标 FAILED + error_msg 回填，不触发任何后续计算</li>
 * </ul>
 *
 * <p><strong>幂等策略（P6.3 + V1.3 R0.2）</strong>：
 * <ol>
 *   <li>DB 先查（便宜）：taskId 已存在则直接幂等返回 accepted=false，不进入写路径</li>
 *   <li>Redis SETNX 互斥：{@code perf:data_task:{taskId}} 锁 TTL 30 秒，
 *       防止两个线程 DB 查都为空时并发双写。获锁成功的线程进入写路径；
 *       获锁失败 → 轮询等待（最多 3s），等胜出方释放锁后 DB 一定可查到行，
 *       回落为 accepted=false 幂等返回</li>
 *   <li>持锁写入 + finally 释放锁（Lua 比较 token 原子删）</li>
 *   <li><strong>V1.3 R0.2 DB 兜底：</strong> perf_run_task 加 uk_task_key 唯一键后，
 *       Redis 宕机或并发穿透场景下 insert 若抛 DuplicateKeyException，
 *       即"DB 层阻止了同 task_key 双写"，此时回查既有行返回 accepted=false</li>
 * </ol>
 *
 * <p><strong>事务</strong>：{@link #report} 不持事务（锁需在事务外，同 {@code SysControlFacade} 模式）；
 * 单条 run_task insert 走 MyBatis/Druid 默认 auto-commit，不需显式事务包装（原子性由单条 SQL 保证）。
 *
 * <p><strong>参数校验</strong>：
 * <ul>
 *   <li>cmd / taskId / dataDate / status / dataType / version 空 → PERF-42200 VALIDATION_FAILED</li>
 *   <li>status 非 SUCCESS/FAILED → PERF-42200</li>
 *   <li>dataType 非法 → PERF-40002 BIZ_KIND_INVALID</li>
 * </ul>
 */
@Slf4j
@Service
public class DataTaskService {

    /** run_task.task_type 固定值. */
    static final String TASK_TYPE = "EXT_DATA";

    /** 幂等锁 key 前缀（与 IT 清理逻辑保持一致）. */
    static final String IDEMPOTENT_LOCK_KEY_PREFIX = "perf:data_task:";

    /** 锁 TTL（30 秒，覆盖单条 insert + 次级查询最坏耗时）. */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** 获锁失败时的等待循环上限（3 秒）. */
    private static final long WAIT_MAX_MILLIS = 3_000L;

    /** 每次轮询间隔（毫秒）. */
    private static final long WAIT_SLEEP_MILLIS = 50L;

    /** 合法 dataType 集合（与 03 §G.1 对齐）. */
    private static final java.util.Set<String> VALID_DATA_TYPES = java.util.Set.of(
            "ALLOC_RELATION", "EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT");

    /** 合法 status 集合（与 03 §G.1 对齐）. */
    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            RunTaskStatusEnum.SUCCESS.name(), RunTaskStatusEnum.FAILED.name());

    /** Lua 脚本：仅当 token 匹配时删锁（防止误删）. */
    private static final RedisScript<Long> COMPARE_AND_DEL = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1])==ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final PerfRunTaskMapper perfRunTaskMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public DataTaskService(PerfRunTaskMapper perfRunTaskMapper,
                           RedisTemplate<String, Object> redisTemplate) {
        this.perfRunTaskMapper = perfRunTaskMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * 上报入口：参数校验 + Redis SETNX 原子幂等 + 落库 + 按状态归档.
     *
     * @param cmd 上报命令（非空）
     * @return 受理结果；{@code accepted=false} 表示幂等命中既有 run_task
     */
    public DataTaskReportResultDTO report(DataTaskStatusCmd cmd) {
        validate(cmd);

        // 1. 便宜 DB 查重（未进入加锁路径，优化正常重复上报场景）
        PerfRunTask existing = perfRunTaskMapper.selectByTaskNo(cmd.getTaskId());
        if (existing != null) {
            log.info("[DataTaskService.report] 幂等命中（快查）taskId={} 既有 runTaskId={}",
                    cmd.getTaskId(), existing.getId());
            return idempotentResult(cmd.getTaskId(), existing.getId());
        }

        // 2. 加 Redis 锁（原子互斥）。获锁失败 → 等待 + 重新 DB 查
        String lockKey = IDEMPOTENT_LOCK_KEY_PREFIX + cmd.getTaskId();
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("[DataTaskService.report] 获锁失败 taskId={}，等待既有写入完成", cmd.getTaskId());
            return waitAndReturnExisting(cmd.getTaskId());
        }

        try {
            // 3. 持锁双检查：获锁期间可能已有线程先写完释放；再查一次避免重复写
            PerfRunTask rechecked = perfRunTaskMapper.selectByTaskNo(cmd.getTaskId());
            if (rechecked != null) {
                log.info("[DataTaskService.report] 幂等命中（持锁双检查）taskId={} runTaskId={}",
                        cmd.getTaskId(), rechecked.getId());
                return idempotentResult(cmd.getTaskId(), rechecked.getId());
            }

            // 4. 插入新 run_task（单条 SQL 自然原子，MyBatis/Druid 默认 auto-commit）
            PerfRunTask task = buildRunTask(cmd);
            try {
                perfRunTaskMapper.insert(task);
            } catch (DuplicateKeyException dup) {
                // V1.3 R0.2：Redis 宕机或并发穿透下，DB uk_task_key 兜底阻止同 task_key 双写
                // 回查既有行返回 accepted=false 的幂等结果，不抛异常
                PerfRunTask dbExisting = perfRunTaskMapper.selectByTaskNo(cmd.getTaskId());
                if (dbExisting == null) {
                    // 理论不应到达：唯一键冲突说明行已存在；若仍查不到，判定为异常状态
                    log.error("[DataTaskService.report] uk_task_key 冲突但回查不到既有行 taskId={} err={}",
                            cmd.getTaskId(), dup.getMessage());
                    throw new PerfException(PerfErrorCode.CALC_JOB_FAILED,
                            "task_key=" + cmd.getTaskId() + " DB 唯一键冲突但查不到既有行");
                }
                log.info("[DataTaskService.report] DB uk_task_key 兜底命中 taskId={} 既有 runTaskId={}",
                        cmd.getTaskId(), dbExisting.getId());
                return idempotentResult(cmd.getTaskId(), dbExisting.getId());
            }
            log.info("[DataTaskService.report] 新建 run_task id={} taskId={} status={} dataType={} dataDate={}",
                    task.getId(), cmd.getTaskId(), cmd.getStatus(), cmd.getDataType(), cmd.getDataDate());

            return DataTaskReportResultDTO.builder()
                    .taskId(cmd.getTaskId())
                    .accepted(true)
                    .perfRunTaskId(task.getId())
                    .build();
        } finally {
            // 5. 释放锁（Lua compare-and-del 避免误删）
            try {
                redisTemplate.execute(COMPARE_AND_DEL, Collections.singletonList(lockKey), token);
            } catch (Exception e) {
                // 释放失败只记日志，TTL 30s 后自动过期
                log.warn("[DataTaskService.report] 释放锁失败, 依赖 TTL 自动释放. lockKey={}, err={}",
                        lockKey, e.getMessage());
            }
        }
    }

    /**
     * 等待其他线程完成写入，然后再次查 DB 返回既有 run_task.
     *
     * <p>场景：并发 T1/T2 同 taskId 到达时，T1 拿到锁开始写入，T2 获锁失败进入本方法；
     * T2 循环短睡眠直到锁释放或超时，然后 DB 查重应能查到 T1 写入的记录。
     *
     * <p>极端超时场景（T1 挂起超过 {@link #WAIT_MAX_MILLIS}）：
     * V1.3 R3.1 起抛 PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT（原 PERF-50007 语义不清），
     * 避免并发死循环。
     *
     * @param taskId 幂等键
     * @return 幂等结果（accepted=false）
     */
    private DataTaskReportResultDTO waitAndReturnExisting(String taskId) {
        long deadline = System.currentTimeMillis() + WAIT_MAX_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            PerfRunTask existing = perfRunTaskMapper.selectByTaskNo(taskId);
            if (existing != null) {
                return idempotentResult(taskId, existing.getId());
            }
            try {
                Thread.sleep(WAIT_SLEEP_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 等待超时：既未拿到锁也未见 DB 记录，判定为异常状态
        // V1.3 R3.1：从 CALC_JOB_FAILED 改为 IDEMPOTENCY_WAIT_TIMEOUT（语义专属：幂等协商超时）
        log.error("[DataTaskService.report] 幂等等待超时 taskId={}", taskId);
        throw new PerfException(PerfErrorCode.IDEMPOTENCY_WAIT_TIMEOUT,
                "taskId=" + taskId + " 等待 " + WAIT_MAX_MILLIS + "ms 后仍未见 DB 记录");
    }

    /**
     * 构造幂等（accepted=false）返回值.
     */
    private DataTaskReportResultDTO idempotentResult(String taskId, String runTaskId) {
        return DataTaskReportResultDTO.builder()
                .taskId(taskId)
                .accepted(false)
                .perfRunTaskId(runTaskId)
                .build();
    }

    /**
     * 参数校验，不合规直接抛 PerfException.
     *
     * @param cmd 命令
     */
    private void validate(DataTaskStatusCmd cmd) {
        if (cmd == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cmd 不能为空");
        }
        if (cmd.getTaskId() == null || cmd.getTaskId().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "taskId 不能为空");
        }
        if (cmd.getDataDate() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "dataDate 不能为空");
        }
        if (cmd.getStatus() == null || !VALID_STATUSES.contains(cmd.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "status 仅允许 SUCCESS/FAILED, 当前=" + cmd.getStatus());
        }
        if (cmd.getVersion() == null || cmd.getVersion().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "version 不能为空");
        }
        if (cmd.getDataType() == null || !VALID_DATA_TYPES.contains(cmd.getDataType())) {
            // dataType 非法使用 BIZ_KIND_INVALID，与 PerfImport 的 importType 非法保持一致
            throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, cmd.getDataType());
        }
    }

    /**
     * 构造 run_task 实体.
     *
     * <p>字段映射：
     * <ul>
     *   <li>id = 新 UUID（32 位去横线）</li>
     *   <li>task_type = "EXT_DATA"</li>
     *   <li>task_key = cmd.taskId（既作关键键也作 "任务编号"）</li>
     *   <li>data_date = cmd.dataDate</li>
     *   <li>data_version = cmd.version</li>
     *   <li>status = cmd.status（SUCCESS/FAILED 透传）</li>
     *   <li>started_by = cmd.sourceSystem 或 "EXT_SYSTEM"</li>
     *   <li>start_time = now()</li>
     *   <li>end_time = now()（SUCCESS/FAILED 终态直接闭环）</li>
     *   <li>error_msg = cmd.errorMsg（FAILED 时）</li>
     *   <li>params_json = cmd 的 JSON 序列化（用于审计 / 运维排查）</li>
     * </ul>
     *
     * @param cmd 命令
     * @return 实体
     */
    private PerfRunTask buildRunTask(DataTaskStatusCmd cmd) {
        LocalDateTime now = LocalDateTime.now();
        PerfRunTask t = new PerfRunTask();
        t.setId(newId());
        t.setTaskType(TASK_TYPE);
        t.setTaskKey(cmd.getTaskId());
        t.setDataDate(cmd.getDataDate());
        t.setDataVersion(cmd.getVersion());
        t.setStatus(cmd.getStatus());
        String startedBy = cmd.getSourceSystem();
        if (startedBy == null || startedBy.isBlank()) {
            startedBy = "EXT_SYSTEM";
        }
        t.setStartedBy(startedBy);
        t.setStartTime(now);
        t.setEndTime(now);
        if (RunTaskStatusEnum.FAILED.name().equals(cmd.getStatus())) {
            t.setErrorMsg(cmd.getErrorMsg());
        }
        t.setParamsJson(serializeParams(cmd));
        return t;
    }

    /**
     * 生成 32 位 run_task ID（UUID 去横线），与其他 Service 保持一致.
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 序列化 cmd 为 JSON；失败时退化为 toString，避免阻断主流程.
     *
     * @param cmd 命令
     * @return JSON 字符串
     */
    private String serializeParams(DataTaskStatusCmd cmd) {
        try {
            return objectMapper.writeValueAsString(cmd);
        } catch (JsonProcessingException e) {
            log.warn("[DataTaskService] params_json 序列化失败: {}", e.getMessage());
            return String.valueOf(cmd);
        }
    }
}
