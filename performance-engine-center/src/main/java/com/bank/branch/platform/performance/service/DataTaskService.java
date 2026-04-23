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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 外部数据任务上报服务（V1.1 Task P6.2 实现 / P6.3 幂等增强）.
 *
 * <p>职责：
 * <ul>
 *   <li>以 {@code cmd.taskId} 为幂等键落库 {@code perf_run_task}（task_type=EXT_DATA）
 *   <li>SUCCESS 上报 → 直接把 run_task 置为 SUCCESS 状态（记录数据就绪）；
 *       后续下游编排（KPI 计算 / sys_control 版本发布）由对应模块监听此 run_task 或独立任务承担，
 *       V1.1 本 Phase 保持单体内松耦合：事件驱动 / 主动查询均可，不强制</li>
 *   <li>FAILED 上报 → run_task 置为 FAILED，errorMsg 回填 error_msg，不触发任何后续计算</li>
 * </ul>
 *
 * <p><strong>幂等（P6.2）</strong>：先查再写，{@code task_key = taskId} 存在则返回 accepted=false 透传既有记录，
 * 不重复插入；并发安全由 P6.3 Redis SETNX 加固。
 *
 * <p><strong>事务</strong>：{@code @Transactional(rollbackFor=Exception.class)} 包起"查 + 写"，
 * 保证 run_task 插入与后续动作要么全成功要么回滚（当前无下游写动作，事务仅保证单一 insert 原子性，
 * 保留扩展位）。
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

    /** 合法 dataType 集合（与 03 §G.1 对齐）. */
    private static final java.util.Set<String> VALID_DATA_TYPES = java.util.Set.of(
            "ALLOC_RELATION", "EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT");

    /** 合法 status 集合（与 03 §G.1 对齐）. */
    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            RunTaskStatusEnum.SUCCESS.name(), RunTaskStatusEnum.FAILED.name());

    private final PerfRunTaskMapper perfRunTaskMapper;
    private final ObjectMapper objectMapper;

    public DataTaskService(PerfRunTaskMapper perfRunTaskMapper) {
        this.perfRunTaskMapper = perfRunTaskMapper;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * 上报入口：幂等落库 + 按状态触发（或记录失败）.
     *
     * @param cmd 上报命令（非空）
     * @return 受理结果；{@code accepted=false} 表示幂等命中
     */
    @Transactional(rollbackFor = Exception.class)
    public DataTaskReportResultDTO report(DataTaskStatusCmd cmd) {
        validate(cmd);

        // 幂等：taskId 查 run_task（V1.0 语义：task_key 即业务唯一 "任务编号"）
        PerfRunTask existing = perfRunTaskMapper.selectByTaskNo(cmd.getTaskId());
        if (existing != null) {
            log.info("[DataTaskService.report] 幂等命中 taskId={} 既有 runTaskId={}",
                    cmd.getTaskId(), existing.getId());
            return DataTaskReportResultDTO.builder()
                    .taskId(cmd.getTaskId())
                    .accepted(false)
                    .perfRunTaskId(existing.getId())
                    .build();
        }

        // 新建 run_task
        PerfRunTask task = buildRunTask(cmd);
        perfRunTaskMapper.insert(task);
        log.info("[DataTaskService.report] 新建 run_task id={} taskId={} status={} dataType={} dataDate={}",
                task.getId(), cmd.getTaskId(), cmd.getStatus(), cmd.getDataType(), cmd.getDataDate());

        return DataTaskReportResultDTO.builder()
                .taskId(cmd.getTaskId())
                .accepted(true)
                .perfRunTaskId(task.getId())
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
