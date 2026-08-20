package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.controller.dto.BatchExecuteRespDTO;
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
import com.bank.branch.platform.performance.service.MetricAsyncRunner;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import com.bank.branch.platform.performance.service.engine.StatShowSqlRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bank.branch.platform.common.web.lock.LockManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final LockManager lockManager;
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
    /** 2026-07-22：指标执行改「提交即返回」后的后台执行体. */
    private final MetricAsyncRunner metricAsyncRunner;
    /** 重算入口共享日期边界校验（上海时区）。 */
    private final StatShowSqlRouter statShowSqlRouter;

    /**
     * 在基础维度级别申请锁后创建指标.
     *
     * @param cmd 创建命令
     * @return 新建指标
     */
    public PerfMetricDef createMetric(CreateMetricDefCmd cmd) {
        String lockKey = LOCK_KEY_PREFIX + cmd.getBaseDim();
        String token = UUID.randomUUID().toString();
        boolean locked = lockManager.tryLock(lockKey, token, LOCK_TTL.toMillis());
        if (!locked) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, cmd.getBaseDim());
        }
        try {
            return metricDefService.create(cmd);
        } finally {
            lockManager.unlock(lockKey, token);
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

    /**
     * V1.6 通用状态切换：支持 ACTIVE / DRAFT / DISABLED 三向迁移。
     */
    public void changeMetricStatus(String metricCode, String targetStatus, String reason, String operator) {
        metricDefService.changeStatus(metricCode, targetStatus, reason, operator);
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
     * <p>Trial 不落 PERF_RUN_TASK（P3.1 契约：全程无侧效），taskId 生成一个 UUID
     * 用于消费方追踪；失败路径保持 V1.1 行为（异常透传由全局 handler 映射）.
     */
    public MetricTrialRespDTO trialRunDto(String metricCode, LocalDate dataDate,
                                          Integer sampleSize, Map<String, Object> params) {
        // 旧签名兼容：业绩分配日期 allocDate 不传 → null
        return trialRunDto(metricCode, dataDate, sampleSize, params, null);
    }

    /**
     * Trial 运行装配 DTO（新增业绩分配日期 :allocDate 入参）.
     *
     * @param allocDate 业绩分配日期（可为 null，Service 绑定阶段兜底为 dataDate）
     * @see #trialRunDto(String, LocalDate, Integer, Map)
     */
    public MetricTrialRespDTO trialRunDto(String metricCode, LocalDate dataDate,
                                          Integer sampleSize, Map<String, Object> params, LocalDate allocDate) {
        LocalDateTime startedAt = LocalDateTime.now();
        String trialTaskId = UUID.randomUUID().toString().replace("-", "");
        MetricTrialResult serviceResult = metricTrialService.trial(
                metricCode, dataDate, sampleSize, params, allocDate);
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
                .exprVars(serviceResult.getExprVars())
                .dataVersion(serviceResult.getDataVersion())
                .build();
    }

    /**
     * 按客户编号 + 数据日期取 CUST_INDEX_RESULT 指定指标值（余额概览反显）.
     *
     * @param custId   客户编号
     * @param dataDate 数据日期（昨日）
     * @param codes    指标编号列表（MC_001..MC_004）
     * @return metricCode → 数值；查无数据的编号缺省
     */
    public java.util.Map<String, java.math.BigDecimal> custIndexValues(
            String custId, LocalDate dataDate, java.util.List<String> codes) {
        return metricCalcService.loadCustIndexValues(custId, dataDate, codes);
    }

    /**
     * 直接试运行 SQL / Groovy 文本（无需先保存指标），返回 DTO.
     */
    public MetricTrialRespDTO trialRunAdhocDto(String calcLogicType, String baseDim, String sqlText, String exprText,
                                               LocalDate dataDate, Integer sampleSize, Map<String, Object> params) {
        // 旧签名兼容：业绩分配日期 allocDate 不传 → null
        return trialRunAdhocDto(calcLogicType, baseDim, sqlText, exprText, dataDate, sampleSize, params, null);
    }

    /**
     * 直接试运行 SQL / Groovy 文本（新增业绩分配日期 :allocDate 入参），返回 DTO.
     *
     * @param allocDate 业绩分配日期（可为 null，Service 绑定阶段兜底为 dataDate）
     * @see #trialRunAdhocDto(String, String, String, String, LocalDate, Integer, Map)
     */
    public MetricTrialRespDTO trialRunAdhocDto(String calcLogicType, String baseDim, String sqlText, String exprText,
                                               LocalDate dataDate, Integer sampleSize, Map<String, Object> params,
                                               LocalDate allocDate) {
        LocalDateTime startedAt = LocalDateTime.now();
        String trialTaskId = UUID.randomUUID().toString().replace("-", "");
        MetricTrialResult serviceResult = metricTrialService.trialAdhoc(
                calcLogicType, baseDim, sqlText, exprText, dataDate, sampleSize, params, allocDate);
        LocalDateTime endedAt = LocalDateTime.now();
        return MetricTrialRespDTO.builder()
                .taskId(trialTaskId)
                .metricCode("(未保存)")
                .sampleSize(serviceResult.getSampleSize())
                .totalRows(serviceResult.getTotalRows())
                .status("SUCCESS")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .errorMsg(null)
                .exprResult(serviceResult.getExprResult())
                .executionMillis(serviceResult.getExecutionMillis())
                .sampleRows(serviceResult.getSamples())
                .exprVars(serviceResult.getExprVars())
                .dataVersion(serviceResult.getDataVersion())
                .build();
    }

    /**
     * V1.3 R4.1：指标立即执行（含 cascade / version 解析 / run_task 状态读取），返回 DTO.
     *
     * <p>把原 {@code MetricDefController.execute} 方法体下沉：
     * <ol>
     *   <li>预校验 metric 存在性 → 避免孤立 run_task</li>
     *   <li>从 SYS_CONTROL 解析版本（失败则兜底 v_default）</li>
     *   <li>按 cascade 分派到 {@link CascadeRefresher} 或 {@link MetricCalcService}</li>
     *   <li>读 PERF_RUN_TASK 真实 status（同步完成场景已是 SUCCESS / FAILED）</li>
     * </ol>
     */
    public RunTaskInfoDTO executeMetric(String metricCode, LocalDate dataDate, Boolean cascadeInput) {
        // 旧签名兼容：业绩分配日期 allocDate 不传 → null（Service 绑定阶段兜底为 dataDate）
        return executeMetric(metricCode, dataDate, cascadeInput, null);
    }

    /**
     * 指标立即执行（新增业绩分配日期 :allocDate 入参）.
     *
     * <p>非 cascade 分支透传到 {@link MetricCalcService#calcMetric(String, LocalDate, String, String, LocalDate)}；
     * cascade 分支透传到 {@link CascadeRefresher#refreshCascade(String, LocalDate, String, LocalDate)}。
     * allocDate 为 null 时由下游绑定阶段兜底为 dataDate（手动执行可由页面指定具体业绩分配日期）。
     *
     * @param allocDate 业绩分配日期（可为 null）
     * @see #executeMetric(String, LocalDate, Boolean)
     */
    public RunTaskInfoDTO executeMetric(String metricCode, LocalDate dataDate, Boolean cascadeInput, LocalDate allocDate) {
        // 旧签名兼容：不传 async → 同步执行（保留历史调用方"返回即终态"的语义）
        return executeMetric(metricCode, dataDate, cascadeInput, allocDate, Boolean.FALSE);
    }

    /**
     * 指标立即执行，支持<b>异步提交</b>（2026-07-22）.
     *
     * <p>{@code async=true}（页面默认）：请求线程只做「校验指标 + 解析版本 + 预建 PENDING 任务行」，
     * 随即把 taskId 返回给前端，真正的计算交给 {@link MetricAsyncRunner} 后台跑。原全同步实现下
     * HTTP 请求要一直阻塞到 SQL 跑完、级联下游也刷完，重指标/大批量会撞网关读超时，前端表现为
     * 「网络异常或后端未启动」——这正是本重载要消除的问题。
     *
     * <p>任务行刻意在<b>请求线程</b>内预建：{@code started_by} 取自 {@code CurrentUserApi} 的
     * ThreadLocal，异步线程里拿不到会兜底成 SYSTEM，操作人就丢了。
     *
     * <p>{@code async=false}：保留原同步语义，返回的 status 即真实终态。
     *
     * @param asyncInput 是否异步提交（null 视为 true）
     * @return 任务信息；异步时 status=PENDING（线程池拒绝时为 FAILED）
     */
    public RunTaskInfoDTO executeMetric(String metricCode, LocalDate dataDate, Boolean cascadeInput,
                                        LocalDate allocDate, Boolean asyncInput) {
        // 预校验指标存在性：不存在时抛 PERF-40001，避免产生孤立 run_task
        PerfMetricDef def = metricDefService.getByCode(metricCode);

        // 所有同步/异步重算入口统一先校验日期与受影响指标，确保任何 PENDING 创建前请求已被拒绝。
        statShowSqlRouter.validateRecalcDate(dataDate);

        // 解析版本：从 SYS_CONTROL 当前生效版本读取（可为空时使用兜底版本）
        String version;
        try {
            SysControl current = sysControlService.getCurrentVersion(def.getBaseDim());
            version = current.getCurrentVersion();
        } catch (Exception ex) {
            // SYS_CONTROL 未初始化时使用兜底版本（测试场景 / 首次执行）；
            // 生产环境通过 System Control Init 流程保证此不发生。
            log.warn("[MetricLifecycleFacade.executeMetric] SYS_CONTROL 读取失败，采用兜底版本: {}", ex.getMessage());
            version = "v_default";
        }

        boolean cascade = cascadeInput == null ? Boolean.TRUE : cascadeInput;
        boolean async = asyncInput == null || asyncInput;

        if (cascade) {
            cascadeRefresher.preflightCascade(metricCode, dataDate);
        } else {
            metricCalcService.preflightMetric(metricCode, dataDate);
        }

        if (async) {
            return submitAsync(metricCode, dataDate, version, cascade, allocDate);
        }

        String taskId;
        if (cascade) {
            taskId = cascadeRefresher.refreshCascade(metricCode, dataDate, version, allocDate);
        } else {
            // 原非 cascade 调 3 参 calcMetric（默认 triggerType=MANUAL），改带 allocDate 的 5 参重载
            taskId = metricCalcService.calcMetric(metricCode, dataDate, version, "MANUAL", allocDate);
        }

        // V1.3 R3.3：从 PERF_RUN_TASK 读取真实状态（Service 可能已同步完成为 SUCCESS/FAILED，
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

    /**
     * 提交异步执行：请求线程内预建 PENDING 任务行 → 交给后台线程池 → 立刻返回 taskId.
     *
     * <p>线程池拒绝（队列满）时把预建行就地标 FAILED 并返回 FAILED——否则任务会永远停在
     * PENDING，监控页看着像"在跑"，实际没有任何线程会碰它。
     */
    private RunTaskInfoDTO submitAsync(String metricCode, LocalDate dataDate, String version,
                                       boolean cascade, LocalDate allocDate) {
        String taskId = metricCalcService.createPendingTask(metricCode, dataDate, version, "MANUAL");
        String status = "PENDING";
        try {
            metricAsyncRunner.runAsync(metricCode, dataDate, version, cascade, allocDate, taskId);
            log.info("[MetricLifecycleFacade.submitAsync] 已提交指标 {} 后台执行 taskId={}, cascade={}",
                    metricCode, taskId, cascade);
        } catch (RuntimeException ex) {
            String msg = "提交后台执行失败（执行队列已满或线程池已关闭）: " + ex.getMessage();
            log.warn("[MetricLifecycleFacade.submitAsync] 指标 {} {}", metricCode, msg);
            perfRunTaskMapper.updateStatus(taskId, "FAILED", msg);
            status = "FAILED";
        }
        return RunTaskInfoDTO.builder()
                .taskId(taskId)
                .status(status)
                .metricCode(metricCode)
                .dataDate(dataDate)
                .version(version)
                .build();
    }

    /**
     * 批量执行：对给定指标逐个执行（非级联），best-effort 聚合结果.
     *
     * <p>单指标失败不中断整批（仿 HistoryRecalcService）；每指标各自写 PERF_RUN_TASK 行。
     * 本方法不开 @Transactional：各 calcMetric 由 {@link #executeMetric} 独立管理 run_task。
     *
     * @param metricCodes 指标编码列表
     * @param dataDate    数据日期
     * @return 聚合结果（total/success/failed + 逐指标明细）
     */
    public BatchExecuteRespDTO batchExecute(List<String> metricCodes, LocalDate dataDate) {
        // 旧签名兼容：不传 async → 同步逐个执行
        return batchExecute(metricCodes, dataDate, Boolean.FALSE);
    }

    /**
     * 批量执行，支持<b>异步提交</b>（2026-07-22）.
     *
     * <p>{@code async=true}（页面默认）时 success/failed 的语义是<b>提交</b>成功/失败数，
     * 各项 status=PENDING、带 runTaskId，真实计算结果需前端轮询任务历史；
     * {@code async=false} 时仍是执行成功/失败数、status 为终态。
     *
     * @param asyncInput 是否异步提交（null 视为 true）
     */
    public BatchExecuteRespDTO batchExecute(List<String> metricCodes, LocalDate dataDate, Boolean asyncInput) {
        boolean async = asyncInput == null || asyncInput;
        List<BatchExecuteRespDTO.Item> results = new ArrayList<>(metricCodes.size());
        int success = 0;
        int failed = 0;
        for (String code : metricCodes) {
            try {
                // cascade=false：批量场景走直算，避免大批量级联放大；version 内部解析
                RunTaskInfoDTO r = executeMetric(code, dataDate, Boolean.FALSE, null, async);
                results.add(BatchExecuteRespDTO.Item.builder()
                        .metricCode(code).status(r.getStatus()).runTaskId(r.getTaskId()).build());
                success++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                if (msg.length() > 500) {
                    msg = msg.substring(0, 500);
                }
                log.warn("[MetricLifecycleFacade.batchExecute] 指标 {} 执行失败: {}", code, msg);
                results.add(BatchExecuteRespDTO.Item.builder()
                        .metricCode(code).status("FAILED").errorMsg(msg).build());
                failed++;
            }
        }
        return BatchExecuteRespDTO.builder()
                .total(metricCodes.size()).success(success).failed(failed).results(results).build();
    }
}
