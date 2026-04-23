package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.importer.PerfImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

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
 * <p>Controller 不直接返回 entity（{@code NoEntityInControllerArchTest} 守护），
 * 所有 PerfImportBatch 通过 {@link #toDto} 装配为 {@link PerfImportBatchRespDTO}.
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

    /**
     * 上传 Excel 并启动导入. 返回新建批次 ID.
     *
     * <p>importType 取值：TARGET / BASE_DATA / ALLOC；其他值由 Service 抛 BIZ_KIND_INVALID (PERF-40002).
     * <p>空文件 → VALIDATION_FAILED (PERF-42200).
     */
    @PostMapping("/upload")
    @Operation(summary = "上传 Excel 并启动导入")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.IMPORT)
    @AuditLog(action = "PERF_IMPORT_UPLOAD", resourceType = "PERF_IMPORT_BATCH")
    public ResponseWrapper<String> upload(@RequestParam("importType") @NotBlank String importType,
                                          @RequestParam("file") MultipartFile file) {
        log.info("[PerfImportController.upload] importType={}, fileName={}, size={}",
                importType, file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "file 不能为空");
        }
        String operatorId = currentUserApi.getCurrentEmpId();
        String batchId = perfImportService.startImport(importType, file, operatorId);
        return ResponseWrapper.success(batchId);
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
        PerfImportBatch b = perfImportService.getBatch(batchId);
        return ResponseWrapper.success(toDto(b));
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

    /** entity → DTO 装配（遵守 NoEntityInControllerArchTest 约束）. */
    private static PerfImportBatchRespDTO toDto(PerfImportBatch b) {
        if (b == null) {
            return null;
        }
        return PerfImportBatchRespDTO.builder()
                .id(b.getId())
                .batchNo(b.getBatchNo())
                .importType(b.getImportType())
                .fileName(b.getFileName())
                .status(b.getStatus())
                .totalRows(b.getTotalRows())
                .successRows(b.getSuccessRows())
                .errorRows(b.getErrorRows())
                .remark(b.getRemark())
                .createdBy(b.getCreatedBy())
                .createdTime(b.getCreatedTime())
                .updatedTime(b.getUpdatedTime())
                .build();
    }
}
