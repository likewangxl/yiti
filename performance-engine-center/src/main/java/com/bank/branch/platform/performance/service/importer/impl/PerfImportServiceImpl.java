package com.bank.branch.platform.performance.service.importer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfImportBatchMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.LocalImportFileStorage;
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
import java.util.Set;
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

    /**
     * 走「本地目录」存储的导入类型（不上传 OBS）：指标导入 / KPI 导入 / 目标导入.
     * 其余类型（TARGET / BASE_DATA / ALLOC / METRIC_RESULT / KPI_SCORE）仍归档 OBS。
     * 下载源文件时按 {@code import_type} 反向分流到对应存储读取。
     */
    private static final Set<String> LOCAL_STORAGE_TYPES = Set.of("METRIC_DEF", "KPI_SCHEME", "TARGET_PLAN");

    private final PerfImportBatchMapper batchMapper;
    private final Map<String, ImportStrategy> strategyMap;
    private final FileApi fileApi;
    private final LocalImportFileStorage localImportFileStorage;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    public PerfImportServiceImpl(PerfImportBatchMapper batchMapper,
                                 List<ImportStrategy> strategies,
                                 FileApi fileApi,
                                 LocalImportFileStorage localImportFileStorage,
                                 CurrentUserApi currentUserApi,
                                 BizScopeApi bizScopeApi) {
        this.batchMapper = batchMapper;
        this.fileApi = fileApi;
        this.localImportFileStorage = localImportFileStorage;
        this.currentUserApi = currentUserApi;
        this.bizScopeApi = bizScopeApi;
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
    public String startImport(String importType, MultipartFile file, String operatorId,
                              LocalDate dataDate, String schemeCode, boolean archiveSource) {
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
        // KPI_SCORE 整文件必填：数据日期 + KPI 方案编码（均由页面输入项传入）
        if ("KPI_SCORE".equals(importType)) {
            if (dataDate == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "dataDate 必填（KPI_SCORE 必传 yyyy-MM-dd）");
            }
            if (schemeCode == null || schemeCode.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "schemeCode 必填（KPI_SCORE 必传 KPI 方案编码）");
            }
        }
        // METRIC_RESULT dataDate 整文件必填（V1.12 微调）；其他类型忽略 dataDate
        if ("METRIC_RESULT".equals(importType) && dataDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "dataDate 必填（METRIC_RESULT 必传 yyyy-MM-dd）");
        }

        // 0) 源文件归档：指标 / KPI / 目标 三类导入存「本地目录」（不上传 OBS），其余仍归档 OBS。
        //    sourceObjectKey 落库：本地存储=相对 key（yyyyMMdd/<uuid>.<ext>）；OBS=file_object 主键。
        //    archiveSource 入参保留以兼容签名，但实现内忽略（始终归档）。
        String sourceObjectKey;
        if (LOCAL_STORAGE_TYPES.contains(importType)) {
            sourceObjectKey = localImportFileStorage.save(file);
        } else {
            FileObjectDTO archived = fileApi.upload(file, operatorId, FileCategory.PERF_IMPORT);
            sourceObjectKey = archived.getId();
        }

        // 1) 创建批次，初始 CREATED
        PerfImportBatch batch = new PerfImportBatch();
        batch.setId(generateId());
        batch.setBatchNo(generateBatchNo());
        batch.setImportType(importType);
        batch.setFileName(file.getOriginalFilename());
        batch.setFileMd5(null); // V1.1 本期简化：不做 MD5 幂等
        batch.setSourceObjectKey(sourceObjectKey);
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
            ImportContext ctx = new ImportContext(dataDate, schemeCode);
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
        return toDto(getBatch(batchId));
    }

    /**
     * PerfImportBatch → 响应 DTO（统一装配，列表/详情共用）.
     */
    private PerfImportBatchRespDTO toDto(PerfImportBatch b) {
        int updated = b.getUpdatedRows() == null ? 0 : b.getUpdatedRows();
        int success = b.getSuccessRows() == null ? 0 : b.getSuccessRows();
        int inserted = Math.max(0, success - updated);
        return PerfImportBatchRespDTO.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .importType(b.getImportType())
                .fileName(b.getFileName())
                .sourceObjectKey(b.getSourceObjectKey())
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

    @Override
    public PageResult<PerfImportBatchRespDTO> pageBatches(int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize <= 0 ? 10 : Math.min(pageSize, 100);
        String selfEmpId = resolveSelfEmpId(); // null=管理员全见；否则=仅该用户

        Page<PerfImportBatch> page = new Page<>(safePageNo, safePageSize);
        LambdaQueryWrapper<PerfImportBatch> qw = new LambdaQueryWrapper<PerfImportBatch>()
                .ne(PerfImportBatch::getStatus, "DELETED")
                // selfEmpId 取自认证 ThreadLocal, 可信; 条件式拼接, 非用户入参
                .eq(selfEmpId != null, PerfImportBatch::getCreatedBy, selfEmpId)
                .orderByDesc(PerfImportBatch::getCreatedTime);
        IPage<PerfImportBatch> result = batchMapper.selectPage(page, qw);

        List<PerfImportBatchRespDTO> dtos = result.getRecords().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageResult.of(safePageNo, safePageSize, result.getTotal(), dtos);
    }

    /**
     * 解析当前用户的数据范围：返回需要施加的 created_by 过滤值；
     * {@code null} 表示管理员（DataScopeType.ALL）全见、不过滤。
     */
    private String resolveSelfEmpId() {
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeType scope = bizScopeApi.resolveScope(empId, BizType.PERF_CONFIG);
        return scope == DataScopeType.ALL ? null : empId;
    }

    @Override
    public ImportSourceFile getSourceFile(String batchId) {
        PerfImportBatch b = getBatch(batchId);
        // 数据范围校验：非管理员只能下载自己的批次
        String selfEmpId = resolveSelfEmpId();
        if (selfEmpId != null && !selfEmpId.equals(b.getCreatedBy())) {
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_NO_PERMISSION, batchId);
        }
        String objectKey = b.getSourceObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_NO_SOURCE_FILE, batchId);
        }
        // 与保存时一致：指标 / KPI / 目标 三类从本地目录读取，其余从 OBS 读取
        byte[] content = LOCAL_STORAGE_TYPES.contains(b.getImportType())
                ? localImportFileStorage.read(objectKey)
                : fileApi.getFileContent(objectKey);
        String fileName = b.getFileName() == null || b.getFileName().isBlank()
                ? (b.getBatchNo() + ".xlsx") : b.getFileName();
        return new ImportSourceFile(fileName, content);
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
