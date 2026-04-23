package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfExportTaskMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.PerfExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 绩效异步导出统一入口实现（Task Q6.1 Green）.
 *
 * <p>策略路由：注入容器内所有 {@link ExportStrategy}，以 exportType 为 Key 构造 Map。
 * 新增策略类型只需新增一个 {@code @Component} 即可加入装配。
 *
 * <p>任务状态机：
 * <pre>
 *   createTask 流程
 *     insert(PENDING)
 *       → updateStatus(RUNNING)
 *       → strategy.execute
 *         ├─ 成功 → updateSuccess(fileKey, rowCount, fileSize, expireAt)
 *         └─ 抛错 → updateFailed(errorMsg) + 向上抛
 * </pre>
 *
 * <p>V1.2 初期同步执行：createTask 方法内直接调 strategy.execute。V1.3 可通过
 * {@code @Async} 或 CompletableFuture 切换到异步执行，届时需把整个"落库 → 执行 → 回填"
 * 链路拆到异步线程中，createTask 立即返回 PENDING 状态的 taskId。
 */
@Slf4j
@Service
public class PerfExportServiceImpl implements PerfExportService {

    /** 文件默认过期时间 = 创建时间 + 7 天. */
    private static final int FILE_EXPIRE_DAYS = 7;

    private final PerfExportTaskMapper taskMapper;
    private final Map<String, ExportStrategy> strategyMap;
    /** 复用 Spring 全局 ObjectMapper 更佳，此处简化用独立实例. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerfExportServiceImpl(PerfExportTaskMapper taskMapper,
                                 List<ExportStrategy> strategies) {
        this.taskMapper = taskMapper;
        this.strategyMap = new HashMap<>();
        for (ExportStrategy s : strategies) {
            String type = s.exportType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("ExportStrategy " + s.getClass().getName()
                        + " 返回空 exportType");
            }
            if (this.strategyMap.putIfAbsent(type, s) != null) {
                throw new IllegalStateException("ExportStrategy exportType 冲突: " + type);
            }
        }
        log.info("[PerfExportService] 已装配 {} 个导出策略: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    @Override
    public String createTask(String exportType, Map<String, Object> params, String operatorId) {
        if (exportType == null || exportType.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "exportType 必填");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "operatorId 必填");
        }
        ExportStrategy strategy = strategyMap.get(exportType);
        if (strategy == null) {
            throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, exportType);
        }

        // 1) 落库 PENDING
        PerfExportTask task = new PerfExportTask();
        task.setId(generateId());
        task.setExportType(exportType);
        task.setParamsJson(serializeParams(params));
        task.setStatus("PENDING");
        task.setOperatorId(operatorId);
        taskMapper.insert(task);

        // 2) 过渡到 RUNNING
        taskMapper.updateStatus(task.getId(), "RUNNING");
        task.setStatus("RUNNING");

        // 3) 策略执行 + 状态机收尾
        try {
            int rowCount = strategy.execute(task);
            // 策略内部已回写 file_key/file_size 到 task 上（约定行为），
            // 此处只补齐状态 + rowCount + expireAt 作为最终 Sink 写入，避免策略重复调 Mapper。
            LocalDateTime expireAt = LocalDateTime.now().plusDays(FILE_EXPIRE_DAYS);
            taskMapper.updateSuccess(task.getId(), task.getFileKey(), rowCount,
                    task.getFileSize(), expireAt);
            log.info("[PerfExportService] 导出成功 taskId={}, type={}, rows={}, fileKey={}",
                    task.getId(), exportType, rowCount, task.getFileKey());
            return task.getId();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                taskMapper.updateFailed(task.getId(), truncate(msg));
            } catch (Exception e2) {
                log.warn("[PerfExportService] 写 FAILED 状态失败 taskId={}", task.getId(), e2);
            }
            log.error("[PerfExportService] 导出失败 taskId={}, type={}, err={}",
                    task.getId(), exportType, msg);
            throw ex;
        }
    }

    @Override
    public PerfExportTask getTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "taskId 必填");
        }
        PerfExportTask t = taskMapper.selectById(taskId);
        if (t == null) {
            throw new PerfException(PerfErrorCode.EXPORT_TASK_NOT_FOUND, taskId);
        }
        return t;
    }

    @Override
    public PerfExportTask getTaskForOwner(String taskId, String operatorId) {
        PerfExportTask t = getTask(taskId);
        if (operatorId == null || !operatorId.equals(t.getOperatorId())) {
            throw new PerfException(PerfErrorCode.EXPORT_TASK_OWNER_MISMATCH,
                    taskId, operatorId);
        }
        return t;
    }

    /** 将 params Map 序列化为 JSON；null/empty 返回 "{}". */
    private String serializeParams(Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Collections.emptyMap() : params;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException ex) {
            log.warn("[PerfExportService] params 序列化失败, 降级为 toString: {}", ex.getMessage());
            return safe.toString();
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** error_msg 字段为 text，但 4000 字符足够，超长截断避免过大 payload. */
    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 3900 ? s.substring(0, 3900) + "..." : s;
    }
}
