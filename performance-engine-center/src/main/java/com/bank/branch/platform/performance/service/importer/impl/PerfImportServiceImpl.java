package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfImportBatchMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.PerfImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 绩效数据导入统一入口实现（Task P5.1 Green）.
 *
 * <p>策略路由：注入 Spring Bean 容器里的所有 {@link ImportStrategy}，以 importType
 * 为 Key 构造 Map。新增策略类型只需新增一个 {@code @Component} 即可加入。
 *
 * <p>状态机：
 * <pre>
 *   startImport 流程
 *     insert(CREATED)
 *       → updateStatus(RUNNING)
 *       → strategy.execute
 *         ├─ 成功 → updateCounts + updateStatus(SUCCESS)
 *         └─ 抛错 → updateStatus(FAILED) + 向上抛
 * </pre>
 *
 * <p>并发：同 batch_no 由 DB UK 拦截（DuplicateKeyException），此处用"毫秒+序号"避免
 * 单机重复；多节点并发上传相同文件的幂等本期简化处理（见 V1.1 计划风险表）。
 */
@Slf4j
@Service
public class PerfImportServiceImpl implements PerfImportService {

    private static final DateTimeFormatter BATCH_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 同毫秒批次号后缀计数器（跨重启不保证连续，只保证单机同毫秒唯一）. */
    private static final AtomicInteger BATCH_NO_SEQ = new AtomicInteger(0);

    private final PerfImportBatchMapper batchMapper;
    private final Map<String, ImportStrategy> strategyMap;

    public PerfImportServiceImpl(PerfImportBatchMapper batchMapper,
                                 List<ImportStrategy> strategies) {
        this.batchMapper = batchMapper;
        this.strategyMap = new HashMap<>();
        for (ImportStrategy s : strategies) {
            String type = s.importType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("ImportStrategy " + s.getClass().getName()
                        + " 返回空 importType");
            }
            if (this.strategyMap.putIfAbsent(type, s) != null) {
                throw new IllegalStateException("ImportStrategy importType 冲突: " + type);
            }
        }
        log.info("[PerfImportService] 已装配 {} 个导入策略: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    @Override
    public String startImport(String importType, MultipartFile file, String operatorId, LocalDate dataDate) {
        if (importType == null || importType.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "importType 必填");
        }
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "file 必填");
        }
        ImportStrategy strategy = strategyMap.get(importType);
        if (strategy == null) {
            throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, importType);
        }
        // METRIC_RESULT dataDate 整文件必填（V1.12 微调）；其他类型忽略 dataDate
        if ("METRIC_RESULT".equals(importType) && dataDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "dataDate 必填（METRIC_RESULT 必传 yyyy-MM-dd）");
        }

        // 1) 创建批次，初始 CREATED
        PerfImportBatch batch = new PerfImportBatch();
        batch.setId(generateId());
        batch.setBatchNo(generateBatchNo());
        batch.setImportType(importType);
        batch.setFileName(file.getOriginalFilename());
        batch.setFileMd5(null); // V1.1 本期简化：不做 MD5 幂等
        batch.setStatus("CREATED");
        batch.setTotalRows(0);
        batch.setSuccessRows(0);
        batch.setErrorRows(0);
        batch.setCreatedBy(operatorId);
        batch.setCreatedTime(LocalDateTime.now());
        batchMapper.insert(batch);

        // 2) 切到 RUNNING
        batchMapper.updateStatus(batch.getId(), "RUNNING", null);
        batch.setStatus("RUNNING");

        // 3) 调用策略执行，包装状态机
        try {
            ImportContext ctx = new ImportContext(dataDate);
            ImportResult result = strategy.execute(batch, file, ctx);
            if (result == null) {
                result = new ImportResult(0, 0, 0, null);
            }
            batchMapper.updateCounts(batch.getId(),
                    result.getTotalRows(), result.getSuccessRows(), result.getErrorRows(),
                    result.getUpdatedRows());
            String remark = result.getErrorSummary();
            batchMapper.updateStatus(batch.getId(), "SUCCESS", truncate(remark));
            log.info("[PerfImportService] 导入成功 batchId={}, type={}, rows={}/{}/{}",
                    batch.getId(), importType,
                    result.getTotalRows(), result.getSuccessRows(), result.getErrorRows());
            return batch.getId();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                batchMapper.updateStatus(batch.getId(), "FAILED", truncate(msg));
            } catch (Exception e2) {
                log.warn("[PerfImportService] 写 FAILED 状态失败 batchId={}", batch.getId(), e2);
            }
            log.error("[PerfImportService] 导入失败 batchId={}, type={}, err={}",
                    batch.getId(), importType, msg);
            throw ex;
        }
    }

    @Override
    public PerfImportBatch getBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "batchId 必填");
        }
        PerfImportBatch b = batchMapper.selectByBatchId(batchId);
        if (b == null) {
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_NOT_FOUND, batchId);
        }
        return b;
    }

    @Override
    public List<String> getErrorDetails(String batchId) {
        PerfImportBatch b = getBatch(batchId);
        String remark = b.getRemark();
        if (remark == null || remark.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(remark.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public void retry(String batchId) {
        PerfImportBatch b = getBatch(batchId);
        if (!"FAILED".equals(b.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "仅 FAILED 状态可重试, 当前=" + b.getStatus());
        }
        // 简化实现：复位为 CREATED，由调用方重新走 startImport 或后续迭代支持 re-run
        batchMapper.updateStatus(batchId, "CREATED", "retry reset");
    }

    @Override
    public void delete(String batchId) {
        PerfImportBatch b = getBatch(batchId);
        if (!"SUCCESS".equals(b.getStatus()) && !"FAILED".equals(b.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "仅终态可删, 当前=" + b.getStatus());
        }
        // 简化实现：软删标记（V1.1 无专用 delete 方法，复用 updateStatus 标记 DELETED）
        batchMapper.updateStatus(batchId, "DELETED", null);
    }

    @Override
    public PerfImportBatchRespDTO getBatchDto(String batchId) {
        // V1.3 R4.1：DTO 装配下沉到 Service，Controller 不再持有 PerfImportBatch
        PerfImportBatch b = getBatch(batchId);
        int updated = b.getUpdatedRows() == null ? 0 : b.getUpdatedRows();
        int success = b.getSuccessRows() == null ? 0 : b.getSuccessRows();
        int inserted = Math.max(0, success - updated);
        return PerfImportBatchRespDTO.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .importType(b.getImportType())
                .fileName(b.getFileName())
                .status(b.getStatus())
                .totalRows(b.getTotalRows())
                .successRows(b.getSuccessRows())
                .errorRows(b.getErrorRows())
                .updatedRows(updated)
                .insertedRows(inserted)
                .remark(b.getRemark())
                .createdBy(b.getCreatedBy())
                .createdTime(b.getCreatedTime())
                .updatedTime(b.getUpdatedTime())
                .build();
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateBatchNo() {
        String ts = LocalDateTime.now().format(BATCH_NO_FMT);
        int seq = BATCH_NO_SEQ.updateAndGet(v -> v >= 999 ? 0 : v + 1);
        return "IMP" + ts + String.format("%03d", seq);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        // perf_import_batch.remark 为 varchar(4000)（DDL），超长截断
        return s.length() > 3900 ? s.substring(0, 3900) + "..." : s;
    }
}
