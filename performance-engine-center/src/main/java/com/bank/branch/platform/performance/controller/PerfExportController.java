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

/**
 * 绩效异步导出 REST 控制器（V1.2 Task Q6.4 Red 骨架）.
 *
 * <p>路径映射（03 §J.7 权威）：
 * <ul>
 *   <li>POST /api/perf/export/kpi      → 创建 KPI 导出任务</li>
 *   <li>POST /api/perf/export/metric   → 创建指标宽表导出任务</li>
 *   <li>POST /api/perf/export/alloc    → 创建分配关系导出任务</li>
 *   <li>POST /api/perf/export/detail   → 创建 KPI 明细导出任务</li>
 *   <li>GET  /api/perf/export/task/{id} → 查询任务状态</li>
 * </ul>
 *
 * <p>单档授权：全部端点 {@code @BizAuth(bizType=PERF_CONFIG, action=...)}，
 * 细粒度资源 ID 见 V1_2_2 脚本（P_PERF_EXPORT_KPI/METRIC/ALLOC/DETAIL + P_PERF_EXPT_STATUS）.
 *
 * <p>Red 阶段：方法体抛 UOE，Green 阶段委托 {@link PerfExportService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/export")
@Tag(name = "Performance Export", description = "绩效异步导出（V1.2）")
@Validated
@RequiredArgsConstructor
public class PerfExportController {

    @SuppressWarnings("unused")
    private final PerfExportService perfExportService;
    @SuppressWarnings("unused")
    private final CurrentUserApi currentUserApi;

    @PostMapping("/kpi")
    @Operation(summary = "创建 KPI 导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_KPI", resourceType = "PERF_EXPORT_TASK")
    public ResponseWrapper<ExportTaskRespDTO> exportKpi(@Valid @RequestBody ExportKpiReqDTO req) {
        throw new UnsupportedOperationException("Q6.4 Green 交付");
    }

    @PostMapping("/metric")
    @Operation(summary = "创建指标宽表导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_METRIC", resourceType = "PERF_EXPORT_TASK")
    public ResponseWrapper<ExportTaskRespDTO> exportMetric(@Valid @RequestBody ExportMetricReqDTO req) {
        throw new UnsupportedOperationException("Q6.4 Green 交付");
    }

    @PostMapping("/alloc")
    @Operation(summary = "创建分配关系导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_ALLOC", resourceType = "PERF_EXPORT_TASK", reasonRequired = true)
    public ResponseWrapper<ExportTaskRespDTO> exportAlloc(@Valid @RequestBody ExportAllocReqDTO req) {
        throw new UnsupportedOperationException("Q6.4 Green 交付");
    }

    @PostMapping("/detail")
    @Operation(summary = "创建 KPI 明细导出任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
    @AuditLog(action = "PERF_EXPORT_DETAIL", resourceType = "PERF_EXPORT_TASK", reasonRequired = true)
    public ResponseWrapper<ExportTaskRespDTO> exportDetail(@Valid @RequestBody ExportKpiReqDTO req) {
        throw new UnsupportedOperationException("Q6.4 Green 交付");
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询导出任务状态")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<ExportTaskRespDTO> getTask(@PathVariable("taskId") @NotBlank String taskId) {
        throw new UnsupportedOperationException("Q6.4 Green 交付");
    }
}
