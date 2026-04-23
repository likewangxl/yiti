package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
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
 * 绩效数据导入 REST 控制器（V1.1 Task P5.5 Red 骨架；Green 填实现）.
 *
 * <p>路径映射（对齐 03 §D + V1.1 计划）：
 * <ul>
 *   <li>POST   /api/perf/import/upload?importType=XXX        → 上传并异步执行（返回 batchId）</li>
 *   <li>GET    /api/perf/import/batches/{batchId}            → 批次详情</li>
 *   <li>GET    /api/perf/import/batches/{batchId}/errors     → 错误明细（每行）</li>
 *   <li>POST   /api/perf/import/batches/{batchId}/retry      → 重试失败批次</li>
 *   <li>DELETE /api/perf/import/batches/{batchId}            → 删除批次</li>
 * </ul>
 *
 * <p>鉴权：全部走 {@code @BizAuth(bizType = PERF_CONFIG)}（对齐 BizAuthConsistencyArchTest）。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/import")
@Tag(name = "Performance Import", description = "绩效数据导入（V1.1）")
@Validated
@RequiredArgsConstructor
public class PerfImportController {

    @SuppressWarnings("unused") // P5.5 Green 使用
    private final PerfImportService perfImportService;
    @SuppressWarnings("unused")
    private final CurrentUserApi currentUserApi;

    /** 上传 Excel 并启动导入. 返回新建批次 ID. */
    @PostMapping("/upload")
    @Operation(summary = "上传 Excel 并启动导入")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.IMPORT)
    @AuditLog(action = "PERF_IMPORT_UPLOAD", resourceType = "PERF_IMPORT_BATCH")
    public ResponseWrapper<String> upload(@RequestParam("importType") @NotBlank String importType,
                                          @RequestParam("file") MultipartFile file) {
        log.warn("[PerfImportController.upload] P5.5 Red 骨架，待 Green 实现");
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "PerfImportController.upload 待 Task P5.5 Green 实现");
    }

    /** 查询批次详情. */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "查询导入批次详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfImportBatchRespDTO> getBatch(
            @PathVariable("batchId") @NotBlank String batchId) {
        log.warn("[PerfImportController.getBatch] P5.5 Red 骨架，待 Green 实现");
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "PerfImportController.getBatch 待 Task P5.5 Green 实现");
    }

    /** 查询批次错误明细. */
    @GetMapping("/batches/{batchId}/errors")
    @Operation(summary = "查询导入错误明细")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> getErrors(
            @PathVariable("batchId") @NotBlank String batchId) {
        log.warn("[PerfImportController.getErrors] P5.5 Red 骨架，待 Green 实现");
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "PerfImportController.getErrors 待 Task P5.5 Green 实现");
    }

    /** 重试失败批次. */
    @PostMapping("/batches/{batchId}/retry")
    @Operation(summary = "重试失败导入批次")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "PERF_IMPORT_RETRY", resourceType = "PERF_IMPORT_BATCH")
    public ResponseWrapper<Void> retry(@PathVariable("batchId") @NotBlank String batchId) {
        log.warn("[PerfImportController.retry] P5.5 Red 骨架，待 Green 实现");
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "PerfImportController.retry 待 Task P5.5 Green 实现");
    }

    /** 删除批次（高危，reasonRequired=true）. */
    @DeleteMapping("/batches/{batchId}")
    @Operation(summary = "删除导入批次")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "PERF_IMPORT_DELETE", resourceType = "PERF_IMPORT_BATCH", reasonRequired = true)
    public ResponseWrapper<Void> delete(@PathVariable("batchId") @NotBlank String batchId) {
        log.warn("[PerfImportController.delete] P5.5 Red 骨架，待 Green 实现");
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "PerfImportController.delete 待 Task P5.5 Green 实现");
    }
}
