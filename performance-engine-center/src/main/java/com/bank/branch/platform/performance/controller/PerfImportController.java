package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.controller.dto.PerfImportUploadRespDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.importer.PerfImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * 绩效数据导入 REST 控制器（V1.1 Task P5.5，5 个端点）.
 *
 * <p>路径映射（对齐 03 §D + V1.1 计划）：
 * <ul>
 *   <li>POST   /api/perf/import/upload?importType=XXX        → 上传并同步启动导入（返回 batchId）</li>
 *   <li>GET    /api/perf/import/batches/{batchId}            → 批次详情</li>
 *   <li>GET    /api/perf/import/batches/{batchId}/errors     → 错误明细（每行）</li>
 *   <li>POST   /api/perf/import/batches/{batchId}/retry      → 重试失败批次</li>
 *   <li>DELETE /api/perf/import/batches/{batchId}            → 删除批次</li>
 * </ul>
 *
 * <p>鉴权：全部走 {@code @BizAuth(bizType = PERF_CONFIG)}，粒度通过 action 区分
 * （对齐 BizAuthConsistencyArchTest 守护）。
 *
 * <p>审计：upload / retry / delete 走 {@code @AuditLog}；delete 为高危走 {@code reasonRequired=true}。
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，DTO 装配全部下沉到
 * {@link PerfImportService#getBatchDto}.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/import")
@Tag(name = "Performance Import", description = "绩效数据导入（V1.1）")
@Validated
@RequiredArgsConstructor
public class PerfImportController {

    private final PerfImportService perfImportService;
    private final CurrentUserApi currentUserApi;

    /** dataDate 唯一可解析格式（与前端 el-date-picker value-format="YYYY-MM-DD" 对齐）. */
    private static final DateTimeFormatter DATA_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /**
     * 上传 Excel 并启动导入. 返回新建批次 ID.
     *
     * <p>importType 取值：TARGET / BASE_DATA / ALLOC / METRIC_DEF / METRIC_RESULT；
     * 其他值由 Service 抛 BIZ_KIND_INVALID (PERF-40002).
     * <p>空文件 → VALIDATION_FAILED (PERF-42200).
     * <p>V1.12 微调（2026-05-19）：METRIC_RESULT 必带 {@code dataDate} 表单参数（yyyy-MM-dd），
     * 整文件统一使用；其他 importType 忽略 dataDate.
     */
    @PostMapping("/upload")
    @Operation(summary = "上传 Excel 并启动导入")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.IMPORT)
    @AuditLog(action = "PERF_IMPORT_UPLOAD", resourceType = "PERF_IMPORT_BATCH")
    public ResponseWrapper<PerfImportUploadRespDTO> upload(@RequestParam("importType") @NotBlank String importType,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "dataDate", required = false) String dataDate,
                                                           @RequestParam(value = "schemeCode", required = false) String schemeCode,
                                                           @RequestParam(value = "archiveSource", required = false, defaultValue = "true") boolean archiveSource) {
        // V1.11：响应破坏性变更为 PerfImportUploadRespDTO（含 insertedRows / updatedRows），
        // 前端从 data: string 改为 data: { batchId, totalRows, insertedRows, updatedRows, errorRows }
        log.info("[PerfImportController.upload] importType={}, fileName={}, size={}, dataDate={}, schemeCode={}",
                importType, file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize(), dataDate, schemeCode);
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "file 不能为空");
        }
        LocalDate parsedDataDate = parseDataDate(dataDate);
        String operatorId = currentUserApi.getCurrentEmpId();
        String batchId = perfImportService.startImport(importType, file, operatorId, parsedDataDate, schemeCode, archiveSource);
        PerfImportBatchRespDTO batchDto = perfImportService.getBatchDto(batchId);
        PerfImportUploadRespDTO resp = PerfImportUploadRespDTO.builder()
                .batchId(batchId)
                .totalRows(batchDto.getTotalRows())
                .insertedRows(batchDto.getInsertedRows())
                .updatedRows(batchDto.getUpdatedRows())
                .errorRows(batchDto.getErrorRows())
                .errorSummary(batchDto.getRemark())
                .build();
        return ResponseWrapper.success(resp);
    }

    /**
     * 解析前端 dataDate 字符串为 LocalDate；空串返回 null（由 Service 层做"METRIC_RESULT 必填"校验）；
     * 格式错抛 VALIDATION_FAILED.
     */
    private static LocalDate parseDataDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATA_DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "dataDate 格式非法（期望 yyyy-MM-dd）: " + raw);
        }
    }

    /**
     * 查询批次详情. 不存在抛 IMPORT_BATCH_NOT_FOUND (PERF-40017).
     */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "查询导入批次详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfImportBatchRespDTO> getBatch(
            @PathVariable("batchId") @NotBlank String batchId) {
        log.debug("[PerfImportController.getBatch] batchId={}", batchId);
        return ResponseWrapper.success(perfImportService.getBatchDto(batchId));
    }

    /**
     * 查询批次错误明细（按行拆分 remark）. 无错误返回空列表.
     */
    @GetMapping("/batches/{batchId}/errors")
    @Operation(summary = "查询导入错误明细")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> getErrors(
            @PathVariable("batchId") @NotBlank String batchId) {
        log.debug("[PerfImportController.getErrors] batchId={}", batchId);
        List<String> errors = perfImportService.getErrorDetails(batchId);
        return ResponseWrapper.success(errors);
    }

    /**
     * 重试失败批次（仅 FAILED 状态可重试，否则 PERF-42200）.
     */
    @PostMapping("/batches/{batchId}/retry")
    @Operation(summary = "重试失败导入批次")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "PERF_IMPORT_RETRY", resourceType = "PERF_IMPORT_BATCH")
    public ResponseWrapper<Void> retry(@PathVariable("batchId") @NotBlank String batchId) {
        log.info("[PerfImportController.retry] batchId={}", batchId);
        perfImportService.retry(batchId);
        return ResponseWrapper.success();
    }

    /**
     * 删除批次（仅终态 SUCCESS / FAILED 可删）. 高危操作，强制 reason.
     */
    @DeleteMapping("/batches/{batchId}")
    @Operation(summary = "删除导入批次")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "PERF_IMPORT_DELETE", resourceType = "PERF_IMPORT_BATCH", reasonRequired = true)
    public ResponseWrapper<Void> delete(@PathVariable("batchId") @NotBlank String batchId) {
        log.info("[PerfImportController.delete] batchId={}", batchId);
        perfImportService.delete(batchId);
        return ResponseWrapper.success();
    }

    /**
     * 分页查询导入批次列表（应用统一 DATA_SCOPE：管理员全见 / 其他角色仅见自己）.
     *
     * <p>不分导入类型，固定排除 DELETED，按 created_time 倒序。
     */
    @GetMapping("/batches")
    @Operation(summary = "分页查询导入批次列表")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<PerfImportBatchRespDTO>> pageBatches(
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        log.debug("[PerfImportController.pageBatches] pageNo={}, pageSize={}", pageNo, pageSize);
        return ResponseWrapper.success(perfImportService.pageBatches(pageNo, pageSize));
    }

    /**
     * 下载批次源文件（从 OBS 流式返回）.
     *
     * <p>鉴权同列表数据范围：非管理员只能下载自己的批次；source_object_key 为空抛
     * IMPORT_BATCH_NO_SOURCE_FILE。
     */
    @GetMapping("/batches/{batchId}/source-file")
    @Operation(summary = "下载导入源文件")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_IMPORT_DOWNLOAD_SOURCE", resourceType = "PERF_IMPORT_BATCH")
    public void downloadSourceFile(@PathVariable("batchId") @NotBlank String batchId,
                                   HttpServletResponse response) throws IOException {
        log.info("[PerfImportController.downloadSourceFile] batchId={}", batchId);
        PerfImportService.ImportSourceFile src = perfImportService.getSourceFile(batchId);
        byte[] data = src.content();
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(src.fileName(), StandardCharsets.UTF_8) + "\"");
        response.setContentLengthLong(data.length);
        response.getOutputStream().write(data);
        response.flushBuffer();
    }
}
