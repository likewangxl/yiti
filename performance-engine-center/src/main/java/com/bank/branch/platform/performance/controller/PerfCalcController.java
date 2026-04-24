package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.PerfCalcApi;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.controller.dto.RecalcReqDTO;
import com.bank.branch.platform.performance.controller.dto.RecalcRespDTO;

import java.util.Optional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效计算触发 REST 控制器（V1.1 Task P7.2）.
 *
 * <p>V1.1 端点清单：
 * <ul>
 *   <li>POST /api/perf/recalc — 历史回算（本期交付）</li>
 * </ul>
 *
 * <p>其他触发类端点已在其他 Controller 交付：
 * <ul>
 *   <li>POST /api/perf/metrics/{metricCode}/execute → {@code MetricDefController.execute}</li>
 *   <li>POST /api/perf/kpi-calc/trigger → {@code KpiSchemeController} / {@code KpiApi}</li>
 *   <li>GET /api/perf/run-tasks / {id} → {@code PerfRunTaskController}</li>
 * </ul>
 *
 * <p>鉴权 / 审计约束（对齐 03 §F.2 + @BizAuth 单档策略）：
 * <ul>
 *   <li>{@code @BizAuth(bizType = PERF_CONFIG, action = EXECUTE)} — 守护于
 *       {@code BizAuthConsistencyArchTest}</li>
 *   <li>{@code @AuditLog(action = "PERF_RECALC", resourceType = "PERF_RUN_TASK",
 *       reasonRequired = true)} — 高危操作强制 reason</li>
 * </ul>
 *
 * <p>返回 DTO 而非 entity（守护于 {@code NoEntityInControllerArchTest}）.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf")
@Tag(name = "Performance Calc Trigger", description = "绩效计算触发（V1.1）")
@Validated
@RequiredArgsConstructor
public class PerfCalcController {

    private final PerfCalcApi perfCalcApi;
    private final CurrentUserApi currentUserApi;

    /**
     * 历史回算（03 §F.2）.
     *
     * <p>端点：{@code POST /api/perf/recalc}
     *
     * <p>流程：
     * <ol>
     *   <li>DTO 校验（Bean Validation）通过后，解析当前发起人 empId</li>
     *   <li>委托 {@link PerfCalcApi#triggerRecalc(String, java.time.LocalDate,
     *       java.time.LocalDate, java.util.List, String, String, String)} 7 参数版本</li>
     *   <li>返回父级 run_task ID + 初始状态（业务异常由全局 GlobalExceptionHandler 映射）</li>
     * </ol>
     *
     * <p>高危约束：
     * <ul>
     *   <li>@AuditLog.reasonRequired=true，入参 DTO 的 reason @NotBlank 校验</li>
     *   <li>日期范围 ≤ 365 天，由 Service 层兜底校验（VALIDATION_FAILED）</li>
     *   <li>cycleDateTo &gt;= cycleDateFrom，Service 层兜底校验（VALIDATION_FAILED）</li>
     * </ul>
     *
     * @param req 回算请求（cycleType/cycleDateFrom/cycleDateTo/metricCodes/version/reason）
     * @return 父级 run_task 主键 + 状态
     */
    @PostMapping("/recalc")
    @Operation(summary = "历史回算（高危，需 reason）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "PERF_RECALC", resourceType = "PERF_RUN_TASK", reasonRequired = true)
    public ResponseWrapper<RecalcRespDTO> recalc(@Valid @RequestBody RecalcReqDTO req) {
        log.info("[PerfCalcController.recalc] cycleType={}, from={}, to={}, metricCodes={}, version={}, reason={}",
                req.getCycleType(), req.getCycleDateFrom(), req.getCycleDateTo(),
                req.getMetricCodes(), req.getVersion(), req.getReason());

        String operator = currentUserApi.getCurrentEmpId();
        String parentTaskId = perfCalcApi.triggerRecalc(
                req.getCycleType(),
                req.getCycleDateFrom(),
                req.getCycleDateTo(),
                req.getMetricCodes(),
                req.getVersion(),
                req.getReason(),
                operator);

        // V1.3 R4.2：读 perf_run_task 真实终态，不再硬编码 "RUNNING"。
        // HistoryRecalcService 为同步执行，父 task 在 triggerRecalc 返回瞬间已是
        // SUCCESS / PARTIAL / FAILED 终态；查不到时（极端竞态 Service 未及时 commit）
        // 退化到 "RUNNING" 占位，保持调用方侧 getRunTask 轮询语义。
        // V1.4 S4.1：fallback 分支打 warn 日志，便于运维从日志中定位
        //   "Service 未 commit / 库主从延迟" 等极端竞态（Reviewer R4.2 建议项）。
        Optional<PerfRunTaskDTO> taskOpt = perfCalcApi.getRunTask(parentTaskId);
        if (taskOpt.isEmpty()) {
            log.warn("[recalc] 父 task {} 查不到，退化 RUNNING 状态（可能 Service 未 commit）",
                    parentTaskId);
        }
        String realStatus = taskOpt
                .map(PerfRunTaskDTO::getStatus)
                .orElse("RUNNING");
        return ResponseWrapper.success(RecalcRespDTO.builder()
                .taskId(parentTaskId)
                .status(realStatus)
                .build());
    }
}
