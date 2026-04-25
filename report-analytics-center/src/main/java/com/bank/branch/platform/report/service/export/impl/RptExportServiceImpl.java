package com.bank.branch.platform.report.service.export.impl;

import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptExportTaskMapper;
import com.bank.branch.platform.report.service.export.ExportStrategy;
import com.bank.branch.platform.report.service.export.RptExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 报表异步导出服务实现（Task M5.2.2 Green）.
 *
 * <p>策略路由：注入容器内所有 {@link ExportStrategy}，以 {@code exportType()} 为 key
 * 构造装配 Map。新增策略类型只需新增 {@code @Component} 即可加入装配（参考
 * performance-engine-center 的 {@code PerfExportServiceImpl}）.
 *
 * <p>状态机：
 * <pre>
 *   createTask 流程
 *     insert(PENDING)
 *       → updateStatus(RUNNING)
 *       → strategy.execute
 *         ├─ 成功 → updateSuccess(fileKey, rowCount, fileSize, expireAt)
 *         └─ 抛错 → updateFailed(errorMsg) + 向上抛
 * </pre>
 *
 * <p>V1.0 同步执行：createTask 内串行调 strategy.execute（02 §9 + BR-2 决策）。
 * V1.1+ 切异步时整个"落库 → 执行 → 回填"链路移到异步线程，createTask 立即返回 PENDING.
 */
@Slf4j
@Service
public class RptExportServiceImpl implements RptExportService {

    /** 文件默认过期时间 = 创建时间 + 7 天. */
    private static final int FILE_EXPIRE_DAYS = 7;

    private final RptExportTaskMapper taskMapper;
    private final Map<String, ExportStrategy> strategyMap;
    /** 模块独立 ObjectMapper，含 LocalDate/LocalDateTime 序列化能力. */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public RptExportServiceImpl(RptExportTaskMapper taskMapper, List<ExportStrategy> strategies) {
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
        log.info("[RptExportService] 已装配 {} 个导出策略: {}",
            strategyMap.size(), strategyMap.keySet());
    }

    @Override
    public String createTask(String exportType, Map<String, Object> params, String operatorId) {
        if (exportType == null || exportType.isBlank()) {
            throw new RptException(RptErrorCode.EXPORT_START_FAILED, new IllegalArgumentException("exportType 必填"));
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new RptException(RptErrorCode.EXPORT_START_FAILED, new IllegalArgumentException("operatorId 必填"));
        }
        ExportStrategy strategy = strategyMap.get(exportType);
        if (strategy == null) {
            throw new RptException(RptErrorCode.EXPORT_START_FAILED,
                new IllegalArgumentException("未知 exportType: " + exportType));
        }

        // 1) 落库 PENDING
        RptExportTask task = new RptExportTask();
        task.setId(generateId());
        task.setExportType(exportType);
        task.setParamsJson(serializeParams(params));
        task.setStatus("PENDING");
        task.setOperatorId(operatorId);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        taskMapper.insert(task);

        // 2) 过渡到 RUNNING
        taskMapper.updateStatus(task.getId(), "RUNNING");
        task.setStatus("RUNNING");

        // 3) 策略执行 + 状态机收尾
        try {
            int rowCount = strategy.execute(task);
            LocalDateTime expireAt = LocalDateTime.now().plusDays(FILE_EXPIRE_DAYS);
            taskMapper.updateSuccess(task.getId(), task.getFileKey(), rowCount,
                task.getFileSize(), expireAt);
            log.info("[RptExportService] 导出成功 taskId={} type={} rows={} fileKey={}",
                task.getId(), exportType, rowCount, task.getFileKey());
            return task.getId();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                taskMapper.updateFailed(task.getId(), truncate(msg));
            } catch (Exception e2) {
                log.warn("[RptExportService] 写 FAILED 状态失败 taskId={}", task.getId(), e2);
            }
            log.error("[RptExportService] 导出失败 taskId={} type={} err={}",
                task.getId(), exportType, msg);
            throw ex;
        }
    }

    @Override
    public RptExportTask getTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_FOUND);
        }
        RptExportTask t = taskMapper.selectById(taskId);
        if (t == null) {
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_FOUND);
        }
        return t;
    }

    @Override
    public RptExportTask getTaskForOwner(String taskId, String operatorId) {
        RptExportTask t = getTask(taskId);
        if (!Objects.equals(t.getOperatorId(), operatorId)) {
            throw new RptException(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN);
        }
        return t;
    }

    @Override
    public void cancelTask(String taskId, String operatorId) {
        RptExportTask t = getTaskForOwner(taskId, operatorId);
        if ("SUCCESS".equals(t.getStatus()) || "FAILED".equals(t.getStatus())) {
            // 状态机：已终态拒绝（CANCELLED 重入由 PENDING/RUNNING 路径承接，幂等）
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_READY);
        }
        taskMapper.updateStatus(taskId, "CANCELLED");
        log.info("[RptExport] task {} cancelled by {}", taskId, operatorId);
    }

    /** 将 params Map 序列化为 JSON；null/empty 返回 "{}". */
    private String serializeParams(Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Collections.emptyMap() : params;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException ex) {
            log.warn("[RptExportService] params 序列化失败, 降级为 toString: {}", ex.getMessage());
            return safe.toString();
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** error_msg 字段为 text，4000 字符足够，超长截断避免过大 payload. */
    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 3900 ? s.substring(0, 3900) + "..." : s;
    }
}
