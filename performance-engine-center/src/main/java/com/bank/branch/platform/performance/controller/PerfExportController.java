package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.ExportAllocReqDTO;
import com.bank.branch.platform.performance.controller.dto.ExportKpiReqDTO;
import com.bank.branch.platform.performance.controller.dto.ExportMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.ExportTaskRespDTO;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.service.export.PerfExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 绩效异步导出 REST 控制器（V1.2 Task Q6.4）.
 *
 * <p>路径映射（03 §J.7 权威）：
 * <ul>
 *   <li>POST /api/perf/export/kpi      → 创建 KPI 导出任务</li>
 *   <li>POST /api/perf/export/metric   → 创建指标宽表导出任务</li>
 *   <li>POST /api/perf/export/alloc    → 创建分配关系导出任务</li>
 *   <li>POST /api/perf/export/detail   → 创建 KPI 明细导出任务</li>
 *   <li>GET  /api/perf/export/task/{id} → 查询导出任务状态</li>
 * </ul>
 *
 * <p>单档授权：全部端点 {@code @BizAuth(bizType=PERF_CONFIG, action=...)}，
 * 细粒度资源 ID 见 V1_2_2 脚本（P_PERF_EXPORT_KPI/ALLOC + P_PERF_EXPT_MTR/DTL/TASK）.
 *
 * <p>Controller 不直接返回 entity（NoEntityInControllerArchTest 守护），
 * 所有 PerfExportTask 通过 {@link #toDto} 装配为 {@link ExportTaskRespDTO}.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/export")
@Tag(name = "Performance Export", description = "绩效异步导出（V1.2）")
@Validated
@RequiredArgsConstructor
public class PerfExportController {

    private final PerfExportService perfExportService;
    private final CurrentUserApi currentUserApi;

    /**
     * 创建 KPI 导出任务.
     */
    @PostMapping("/kpi")
    @Operation(summary = "创建 KPI 导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_KPI", resourceType = "PERF_EXPORT_TASK")
    public ResponseWrapper<ExportTaskRespDTO> exportKpi(@Valid @RequestBody ExportKpiReqDTO req) {
        log.info("[PerfExportController.exportKpi] cycleType={}, cycleDate={}, asOfDate={}",
                req.getCycleType(), req.getCycleDate(), req.getAsOfDate());
        Map<String, Object> params = new HashMap<>();
        params.put("cycleType", req.getCycleType());
        params.put("cycleDate", req.getCycleDate().toString());
        params.put("asOfDate", req.getAsOfDate().toString());
        if (req.getDataVersion() != null) {
            params.put("dataVersion", req.getDataVersion());
        }
        String taskId = perfExportService.createTask("KPI", params, currentUserApi.getCurrentEmpId());
        PerfExportTask task = perfExportService.getTask(taskId);
        return ResponseWrapper.success(toDto(task));
    }

    /**
     * 创建指标宽表导出任务.
     */
    @PostMapping("/metric")
    @Operation(summary = "创建指标宽表导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_METRIC", resourceType = "PERF_EXPORT_TASK")
    public ResponseWrapper<ExportTaskRespDTO> exportMetric(@Valid @RequestBody ExportMetricReqDTO req) {
        log.info("[PerfExportController.exportMetric] metricCodes={}, baseDim={}, dataDate={}",
                req.getMetricCodes(), req.getBaseDim(), req.getDataDate());
        Map<String, Object> params = new HashMap<>();
        params.put("metricCodes", req.getMetricCodes());
        params.put("baseDim", req.getBaseDim());
        params.put("dataDate", req.getDataDate().toString());
        params.put("version", req.getVersion());
        if (req.getOrgCodes() != null) {
            params.put("orgCodes", req.getOrgCodes());
        }
        String taskId = perfExportService.createTask("METRIC", params, currentUserApi.getCurrentEmpId());
        PerfExportTask task = perfExportService.getTask(taskId);
        return ResponseWrapper.success(toDto(task));
    }

    /**
     * 创建分配关系导出任务（高危，reasonRequired=true）.
     */
    @PostMapping("/alloc")
    @Operation(summary = "创建分配关系导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_ALLOC", resourceType = "PERF_EXPORT_TASK", reasonRequired = true)
    public ResponseWrapper<ExportTaskRespDTO> exportAlloc(@Valid @RequestBody ExportAllocReqDTO req) {
        log.info("[PerfExportController.exportAlloc] effectiveDate={}, bizKind={}, empId={}",
                req.getEffectiveDate(), req.getBizKind(), req.getEmpId());
        Map<String, Object> params = new HashMap<>();
        params.put("effectiveDate", req.getEffectiveDate().toString());
        if (req.getBizKind() != null) {
            params.put("bizKind", req.getBizKind());
        }
        if (req.getEmpId() != null) {
            params.put("empId", req.getEmpId());
        }
        String taskId = perfExportService.createTask("ALLOC", params, currentUserApi.getCurrentEmpId());
        PerfExportTask task = perfExportService.getTask(taskId);
        return ResponseWrapper.success(toDto(task));
    }

    /**
     * 创建 KPI 明细导出任务（高危，reasonRequired=true）.
     */
    @PostMapping("/detail")
    @Operation(summary = "创建 KPI 明细导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_DETAIL", resourceType = "PERF_EXPORT_TASK", reasonRequired = true)
    public ResponseWrapper<ExportTaskRespDTO> exportDetail(@Valid @RequestBody ExportKpiReqDTO req) {
        log.info("[PerfExportController.exportDetail] cycleType={}, cycleDate={}, asOfDate={}",
                req.getCycleType(), req.getCycleDate(), req.getAsOfDate());
        Map<String, Object> params = new HashMap<>();
        params.put("cycleType", req.getCycleType());
        params.put("cycleDate", req.getCycleDate().toString());
        params.put("asOfDate", req.getAsOfDate().toString());
        if (req.getDataVersion() != null) {
            params.put("dataVersion", req.getDataVersion());
        }
        String taskId = perfExportService.createTask("DETAIL", params, currentUserApi.getCurrentEmpId());
        PerfExportTask task = perfExportService.getTask(taskId);
        return ResponseWrapper.success(toDto(task));
    }

    /**
     * 查询导出任务状态.
     *
     * <p>未做 owner 校验（V1.2 阶段），后续若需限制"只能下载本人任务"，在此处改为
     * {@code perfExportService.getTaskForOwner(taskId, currentUserApi.getCurrentEmpId())}.
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询导出任务状态")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<ExportTaskRespDTO> getTask(@PathVariable("taskId") @NotBlank String taskId) {
        log.debug("[PerfExportController.getTask] taskId={}", taskId);
        PerfExportTask task = perfExportService.getTask(taskId);
        return ResponseWrapper.success(toDto(task));
    }

    /** entity → DTO 装配（遵守 NoEntityInControllerArchTest 约束）. */
    private static ExportTaskRespDTO toDto(PerfExportTask t) {
        if (t == null) {
            return null;
        }
        return ExportTaskRespDTO.builder()
                .id(t.getId())
                .exportType(t.getExportType())
                .status(t.getStatus())
                .fileKey(t.getFileKey())
                .fileSize(t.getFileSize())
                .rowCount(t.getRowCount())
                .expireAt(t.getExpireAt())
                .operatorId(t.getOperatorId())
                .errorMsg(t.getErrorMsg())
                .createdTime(t.getCreatedTime())
                .updatedTime(t.getUpdatedTime())
                .build();
    }
}
