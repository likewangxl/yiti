package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
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
 * (Plan Task 4.4 钦定, 符合审计只记录写/高危操作的通用约束)。
 *
 * <p>数据范围过滤由 {@link PerfRunTaskService#page} 统一处理: 管理员/ALL 范围全见,
 * 其他范围收敛为 "仅见自己 started_by" 的片段 (v1.0 简化实现)。
 *
 * <p>异常策略: Controller 不做 try-catch, {@code PerfException} 冒泡至全局异常处理器,
 * 业务错误统一以 200 + 错误码返回 (任务不存在 → PERF-40405)。
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
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dataDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDate,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        throw new UnsupportedOperationException("Task 4.4 Green 阶段实现");
    }

    /**
     * 获取任务日志详情. 不存在抛 PERF-40405.
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务日志详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfRunTaskDTO> getById(@PathVariable("id") @NotBlank String id) {
        throw new UnsupportedOperationException("Task 4.4 Green 阶段实现");
    }
}
