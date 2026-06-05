package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.performance.controller.dto.KpiScoreCalcReqDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreStatsDTO;
import com.bank.branch.platform.performance.controller.dto.MetricOptionDTO;
import com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO;
import com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO;
import com.bank.branch.platform.performance.service.KpiScoreCalcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * KPI 分值计算接口（手动触发 / 前端重算）.
 *
 * <p>{@code POST /api/perf/kpi-score/calc}：按数据日期（必填）+ KPI 方案编码（可空，空=全部 ACTIVE 方案）
 * 重新计算 KPI 得分并 upsert 到 {@code PERF_KPI_SCORE}。既供运维手动触发，也供前端"重算单个 KPI"。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/kpi-score")
@RequiredArgsConstructor
@Tag(name = "KPI分值计算", description = "按数据日期 + KPI方案重算 KPI 得分")
public class KpiScoreCalcController {

    private final KpiScoreCalcService kpiScoreCalcService;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final AuditApi auditApi;

    /**
     * 触发 KPI 分值计算（考核计算页面"触发"按钮）.
     *
     * <p>请求体含 数据日期 + KPI方案编码 + 触发原因；触发原因由 {@code @AuditLog} 切面记入审批日志。
     *
     * @param req 触发请求（数据日期 / 方案编码 / 触发原因，均必填）
     * @return 任务流水 ID
     */
    @PostMapping("/calc")
    @Operation(summary = "触发/重算 KPI 得分（先记审计日志再计算）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    public ResponseWrapper<String> calc(@Valid @RequestBody KpiScoreCalcReqDTO req,
                                        HttpServletRequest request) {
        LocalDate dt = LocalDate.parse(req.getDataDate());
        // 触发人工号 = 当前用户 empId 解析出的 PT_USER.username（约定：工号=username）；姓名取 displayName
        String empId = currentUserApi.getCurrentEmpId();
        String triggerBy = empId;
        String empName = null;
        if (empId != null) {
            UserDTO u = userApi.getUserByEmpId(empId);
            if (u != null) {
                if (u.getUsername() != null) {
                    triggerBy = u.getUsername();
                }
                empName = u.getDisplayName();
            }
        }

        // 先记录触发审计日志（AuditApi.log 走 REQUIRES_NEW 独立事务，确保计算开始前先留痕），再调用计算服务
        auditApi.log(AuditLogCmd.builder()
                .traceId(MDC.get("traceId"))
                .empId(empId)
                .empName(empName)
                .bizType(BizType.PERF_CONFIG.getCode())
                .bizAction(BizAction.EXECUTE.name())
                .resourceUrl(request.getRequestURI())
                .requestMethod(request.getMethod())
                .requestParams("dataDate=" + req.getDataDate() + ", schemeCode=" + req.getSchemeCode())
                .responseStatus(200)
                .reason(req.getReason())
                .build());

        log.info("[KpiScoreCalcController.calc] 审计已记录，开始计算 dataDate={}, schemeCode={}, triggerBy={}, reason={}",
                dt, req.getSchemeCode(), triggerBy, req.getReason());
        String taskId = kpiScoreCalcService.calculate(dt, req.getSchemeCode(), "MANUAL", triggerBy);
        return ResponseWrapper.success(taskId);
    }

    /**
     * 考核计算页面统计：最后一次 KPI 计算任务的成功数/失败数/耗时 + 本月 KPI 计算任务数.
     *
     * @return 统计 DTO
     */
    @GetMapping("/stats")
    @Operation(summary = "考核计算统计（最后一次KPI任务 + 本月任务数）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<KpiScoreStatsDTO> stats() {
        return ResponseWrapper.success(kpiScoreCalcService.getStats());
    }

    /**
     * 考核计算数据列表：KPI 方案级计算记录（PERF_KPI_CALC_LOG），按数据日期 + KPI方案过滤.
     *
     * @param dataDate   数据日期（yyyy-MM-dd，可空）
     * @param schemeCode KPI 方案编码（可空）
     * @param pageNo     页码
     * @param pageSize   每页条数
     * @return 分页记录
     */
    @GetMapping("/logs")
    @Operation(summary = "KPI方案级计算记录列表")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfKpiCalcLogDTO> logs(
            @RequestParam(value = "dataDate", required = false) String dataDate,
            @RequestParam(value = "schemeCode", required = false) String schemeCode,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        LocalDate dt = (dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null;
        PageResult<PerfKpiCalcLogDTO> page = kpiScoreCalcService.pageLogs(dt, schemeCode, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }

    /**
     * KPI 计算结果详情：某数据日期 + KPI方案下的计分明细（PERF_KPI_SCORE），
     * 列含 维度 / 指标 / 对象 / 得分，分页.
     *
     * @param dataDate   数据日期（yyyy-MM-dd，必填）
     * @param schemeCode KPI 方案编码（必填）
     * @param pageNo     页码
     * @param pageSize   每页条数
     * @return 分页计分明细
     */
    @GetMapping("/results")
    @Operation(summary = "KPI计算结果明细（维度/指标/对象/得分）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfKpiScoreResultDTO> results(
            @RequestParam(value = "dataDate", required = false) String dataDate,
            @RequestParam(value = "schemeCode", required = false) String schemeCode,
            @RequestParam(value = "metricCode", required = false) String metricCode,
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        LocalDate dt = (dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null;
        PageResult<PerfKpiScoreResultDTO> page = kpiScoreCalcService.pageScores(dt, schemeCode, metricCode, subjectType, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }

    /**
     * KPI 计算结果详情页"指标"下拉：仅含该 KPI 方案配置的指标（编号 + 名称）.
     *
     * @param schemeCode KPI 方案编码
     * @return 指标下拉项
     */
    @GetMapping("/scheme-metrics")
    @Operation(summary = "KPI方案的指标下拉项")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<java.util.List<MetricOptionDTO>> schemeMetrics(
            @RequestParam("schemeCode") String schemeCode) {
        return ResponseWrapper.success(kpiScoreCalcService.listSchemeMetrics(schemeCode));
    }
}
