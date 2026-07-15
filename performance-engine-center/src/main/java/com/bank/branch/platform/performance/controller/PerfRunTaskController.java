package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import com.bank.branch.platform.performance.service.dto.RunTaskQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 绩效任务执行日志 REST 控制器 (2 个只读端点, 对齐 PT_RESOURCE P_PERF_RT_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET /api/perf/run-tasks       → P_PERF_RT_LIST</li>
 *   <li>GET /api/perf/run-tasks/{id}  → P_PERF_RT_GET</li>
 * </ul>
 *
 * <p>读操作端点仅标注 {@code @BizAuth(READ/LIST)}, 不带 {@code @AuditLog}
 * (Plan Task 4.4 L1492 钦定, 符合审计只记录写/高危操作的通用约束)。
 *
 * <p>数据范围过滤由 {@link PerfRunTaskService#pageDto} 统一处理: 管理员/ALL 范围全见,
 * 其他范围收敛为 "仅见自己 started_by" 的片段 (v1.0 简化实现)。
 *
 * <p>异常策略: Controller 不做 try-catch, {@code PerfException} 冒泡至全局异常处理器,
 * 业务错误统一以 200 + 错误码返回 (任务不存在 → PERF-40007, V1.1 P8.1 语义对齐)。
 * 未登录场景由 {@link CurrentUserApi#getCurrentEmpId()} 抛 {@code AuthException},
 * 由全局异常处理器映射 HTTP 401.
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，所有装配下沉到
 * {@link PerfRunTaskService#pageDto} / {@link PerfRunTaskService#getByIdDto}。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/run-tasks")
@Tag(name = "Performance Run Task", description = "绩效任务执行日志 (只读)")
@Validated
@RequiredArgsConstructor
public class PerfRunTaskController {

    private final CurrentUserApi currentUserApi;
    private final PerfRunTaskService perfRunTaskService;

    /**
     * 分页查询任务日志 (含数据范围过滤).
     *
     * <p>V1.0 Controller 层不接受 {@code taskKey} 参数 (Service 签名保留 nullable,
     * 该维度保留给 V1.1 的调用方); 目前三个过滤条件 taskType / status / dataDate 足以覆盖
     * 任务查询 UI 场景。
     *
     * @param taskType 任务类型 (nullable)
     * @param status   状态 (nullable)
     * @param dataDate 数据日期 (nullable, ISO yyyy-MM-dd)
     * @param pageNo   页码 (默认 1)
     * @param pageSize 页大小 (默认 20, 最大 100)
     */
    @GetMapping
    @Operation(summary = "分页查询任务日志")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PerfRunTaskDTO> list(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "triggerType", required = false) String triggerType,
            @RequestParam(value = "taskKey", required = false) String taskKey,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startedBy", required = false) String startedBy,
            @RequestParam(value = "dataDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDate,
            @RequestParam(value = "dataDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDateFrom,
            @RequestParam(value = "dataDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        // 记录 empId 便于排查数据范围相关问题 (未登录时此处会抛 AuthException → 401)
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[PerfRunTaskController.list] empId={}, taskType={}, triggerType={}, taskKey={}, status={}, "
                        + "startedBy={}, dataDate={}, dataDateFrom={}, dataDateTo={}, pageNo={}, pageSize={}",
                empId, taskType, triggerType, taskKey, status, startedBy, dataDate, dataDateFrom, dataDateTo, pageNo, pageSize);

        RunTaskQuery query = new RunTaskQuery(taskType, triggerType, taskKey, status, startedBy,
                dataDate, dataDateFrom, dataDateTo);
        PageResult<PerfRunTaskDTO> dtoPage = perfRunTaskService.pageDto(query, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * 获取任务日志详情. 不存在抛 PERF-40007 (V1.1 P8.1 语义对齐，原为 METRIC_NOT_FOUND 错位复用).
     *
     * <p>未登录时 {@link CurrentUserApi#getCurrentEmpId()} 抛 {@code AuthException},
     * 由全局异常处理器映射 HTTP 401 (plan Task 4.4 L1488 DoD 场景)。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务日志详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfRunTaskDTO> getById(@PathVariable("id") @NotBlank String id) {
        // 未登录在此处即抛 AuthException (401), 先于业务查询触发, 符合 DoD L1488
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[PerfRunTaskController.getById] empId={}, id={}", empId, id);

        // V1.1 P8.1 修正：run_task 不存在改抛 RUN_TASK_NOT_FOUND（PERF-40007），
        // 不再复用 METRIC_NOT_FOUND（语义是"指标不存在"，与 run_task 场景不符）
        // V1.3 R4.1：Service.getByIdDto 内部完成 entity 装配 + 不存在抛异常
        return ResponseWrapper.success(perfRunTaskService.getByIdDto(id));
    }

    /**
     * 任务监控：按指标分组汇总分页（列表页数据源）.
     *
     * @param taskType      任务类型（默认 METRIC_RUN）
     * @param metricKeyword 指标编码/名称关键字（nullable）
     */
    @GetMapping("/metric-summary")
    @Operation(summary = "按指标分组汇总任务（任务监控列表）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<MetricSummaryDTO> metricSummary(
            @RequestParam(value = "taskType", defaultValue = "METRIC_RUN") String taskType,
            @RequestParam(value = "metricKeyword", required = false) String metricKeyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[PerfRunTaskController.metricSummary] empId={}, taskType={}, keyword={}, pageNo={}, pageSize={}",
                empId, taskType, metricKeyword, pageNo, pageSize);
        PageResult<MetricSummaryDTO> page = perfRunTaskService.pageMetricSummary(
                taskType, metricKeyword, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }
}
