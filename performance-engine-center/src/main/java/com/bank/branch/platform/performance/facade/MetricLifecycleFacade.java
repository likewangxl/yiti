package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.CascadeRefresher;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 指标生命周期门面，负责事务外的槽位分布式锁.
 *
 * <p>V1.3 R4.1 扩展：新增 {@code xxxDto} 系列方法与 {@link #trialRunDto} /
 * {@link #executeMetric} / {@link #listSlotsDto}，把 Controller 原本持有的
 * entity 中间变量装配逻辑下沉到 Facade 层，Controller 彻底不感知 entity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricLifecycleFacade {

    private static final String LOCK_KEY_PREFIX = "perf:slot-alloc:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final RedisScript<Long> COMPARE_AND_DEL = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1])==ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricDefService metricDefService;
    /** V1.3 R4.1：execute / trialRun 需要的协作组件. */
    private final MetricTrialService metricTrialService;
    private final MetricCalcService metricCalcService;
    private final CascadeRefresher cascadeRefresher;
    private final SysControlService sysControlService;
    private final PerfRunTaskMapper perfRunTaskMapper;
    /** V1.3 R4.1：释放指标槽位需要. */
    private final MetricSlotService metricSlotService;
    /** V1.3 R4.1：refs / ref-by 查询需要. */
    private final MetricRefService metricRefService;

    /**
     * 在基础维度级别申请锁后创建指标.
     *
     * @param cmd 创建命令
     * @return 新建指标
     */
    public PerfMetricDef createMetric(CreateMetricDefCmd cmd) {
        String lockKey = LOCK_KEY_PREFIX + cmd.getBaseDim();
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, cmd.getBaseDim());
        }
        try {
            return metricDefService.create(cmd);
        } finally {
            redisTemplate.execute(COMPARE_AND_DEL, Collections.singletonList(lockKey), token);
        }
    }

    /**
     * Update metric without additional slot lock.
     *
     * @param cmd update command
     * @return updated metric
     */
    public PerfMetricDef updateMetric(UpdateMetricDefCmd cmd) {
        return metricDefService.update(cmd);
    }

    /**
     * Disable metric without additional slot lock.
     *
     * @param metricCode metric code
     * @param reason     disable reason
     * @param operator   operator
     */
    public void disableMetric(String metricCode, String reason, String operator) {
        metricDefService.disable(metricCode, reason, operator);
    }

    // ==================== V1.3 R4.1 DTO 门面方法 ====================

    /**
     * V1.3 R4.1：创建指标并返回 DTO.
     */
    public MetricDefRespDTO createMetricDto(CreateMetricDefCmd cmd) {
        return MetricAssembler.toRespDTO(createMetric(cmd));
    }

    /**
     * V1.3 R4.1：更新指标并返回 DTO.
     */
    public MetricDefRespDTO updateMetricDto(UpdateMetricDefCmd cmd) {
        return MetricAssembler.toRespDTO(updateMetric(cmd));
    }

    /**
     * V1.3 R4.1：根据 metricCode 获取 entity 内部 id，执行 slot release。
     *
     * <p>Controller 原先依赖 entity.getId() 调 slotService；下沉后 Controller 只传 metricCode.
     */
    public void releaseSlotByMetricCode(String metricCode, String reason, String operator) {
        PerfMetricDef metricDef = metricDefService.getByCode(metricCode);
        metricSlotService.releaseSlot(metricDef.getId(), operator, reason);
    }

    /**
     * V1.3 R4.1：Trial 运行装配 DTO，Controller 不再直接使用 {@link MetricTrialResult}.
     *
     * <p>Trial 不落 perf_run_task（P3.1 契约：全程无侧效），taskId 生成一个 UUID
     * 用于消费方追踪；失败路径保持 V1.1 行为（异常透传由全局 handler 映射）.
     */
    public MetricTrialRespDTO trialRunDto(String metricCode, LocalDate dataDate,
                                          Integer sampleSize, Map<String, Object> params) {
        LocalDateTime startedAt = LocalDateTime.now();
        String trialTaskId = UUID.randomUUID().toString().replace("-", "");
        MetricTrialResult serviceResult = metricTrialService.trial(
                metricCode, dataDate, sampleSize, params);
        LocalDateTime endedAt = LocalDateTime.now();
        return MetricTrialRespDTO.builder()
                .taskId(trialTaskId)
                .metricCode(metricCode)
                .sampleSize(serviceResult.getSampleSize())
                .totalRows(serviceResult.getTotalRows())
                .status("SUCCESS")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .errorMsg(null)
                .exprResult(serviceResult.getExprResult())
                .executionMillis(serviceResult.getExecutionMillis())
                .sampleRows(serviceResult.getSamples())
                .build();
    }

    /**
     * V1.3 R4.1：指标立即执行（含 cascade / version 解析 / run_task 状态读取），返回 DTO.
     *
     * <p>把原 {@code MetricDefController.execute} 方法体下沉：
     * <ol>
     *   <li>预校验 metric 存在性 → 避免孤立 run_task</li>
     *   <li>从 sys_control 解析版本（失败则兜底 v_default）</li>
     *   <li>按 cascade 分派到 {@link CascadeRefresher} 或 {@link MetricCalcService}</li>
     *   <li>读 perf_run_task 真实 status（同步完成场景已是 SUCCESS / FAILED）</li>
     * </ol>
     */
    public RunTaskInfoDTO executeMetric(String metricCode, LocalDate dataDate, Boolean cascadeInput) {
        // 预校验指标存在性：不存在时抛 PERF-40001，避免产生孤立 run_task
        PerfMetricDef def = metricDefService.getByCode(metricCode);

        // 解析版本：从 sys_control 当前生效版本读取（可为空时使用兜底版本）
        String version;
        try {
            SysControl current = sysControlService.getCurrentVersion(def.getBaseDim());
            version = current.getCurrentVersion();
        } catch (Exception ex) {
            // sys_control 未初始化时使用兜底版本（测试场景 / 首次执行）；
            // 生产环境通过 System Control Init 流程保证此不发生。
            log.warn("[MetricLifecycleFacade.executeMetric] sys_control 读取失败，采用兜底版本: {}", ex.getMessage());
            version = "v_default";
        }

        boolean cascade = cascadeInput == null ? Boolean.TRUE : cascadeInput;
        String taskId;
        if (cascade) {
            taskId = cascadeRefresher.refreshCascade(metricCode, dataDate, version);
        } else {
            taskId = metricCalcService.calcMetric(metricCode, dataDate, version);
        }

        // V1.3 R3.3：从 perf_run_task 读取真实状态（Service 可能已同步完成为 SUCCESS/FAILED，
        // 也可能仍 RUNNING）；查不到时退化为 RUNNING 占位（极端竞态下 Service 未及时 commit）.
        PerfRunTask task = perfRunTaskMapper.selectById(taskId);
        String status = task != null && task.getStatus() != null ? task.getStatus() : "RUNNING";
        return RunTaskInfoDTO.builder()
                .taskId(taskId)
                .status(status)
                .metricCode(metricCode)
                .dataDate(dataDate)
                .version(version)
                .build();
    }
}
