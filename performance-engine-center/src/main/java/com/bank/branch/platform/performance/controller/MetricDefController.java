package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateMetricReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.facade.MetricLifecycleFacade;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metric definition read controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/metrics")
@Tag(name = "Performance Metric", description = "Metric definition read endpoints")
@Validated
@RequiredArgsConstructor
public class MetricDefController {

    private final CurrentUserApi currentUserApi;
    private final MetricLifecycleFacade metricLifecycleFacade;
    private final MetricDefService metricDefService;
    private final MetricRefService metricRefService;
    private final MetricSlotService metricSlotService;
    private final MetricTrialService metricTrialService;

    /**
     * List metric definitions with page result.
     * 返回 MetricDefRespDTO，不暴露 entity 内部字段。
     */
    @GetMapping
    @Operation(summary = "List metric definitions")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<MetricDefRespDTO> list(
            @RequestParam(value = "baseDim", required = false) String baseDim,
            @RequestParam(value = "metricLevel", required = false) Integer metricLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[MetricDefController.list] baseDim={}, metricLevel={}, status={}, keyword={}, pageNo={}, pageSize={}",
                baseDim, metricLevel, status, keyword, pageNo, pageSize);
        PageResult<PerfMetricDef> entityPage = metricDefService.page(baseDim, metricLevel, status, keyword, pageNo, pageSize);
        // 将 entity 分页结果转换为 DTO 分页结果，屏蔽内部字段
        PageResult<MetricDefRespDTO> dtoPage = PageResult.of(
                entityPage.getPageNo(),
                entityPage.getPageSize(),
                entityPage.getTotal(),
                entityPage.getRecords().stream()
                        .map(MetricAssembler::toRespDTO)
                        .collect(Collectors.toList())
        );
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * Get metric definition by code.
     * 返回 MetricDefRespDTO，不暴露 entity 内部字段。
     */
    @GetMapping("/{metricCode}")
    @Operation(summary = "Get metric definition by code")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<MetricDefRespDTO> getByCode(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.getByCode] metricCode={}", metricCode);
        return ResponseWrapper.success(MetricAssembler.toRespDTO(metricDefService.getByCode(metricCode)));
    }

    /**
     * List referenced metric codes.
     */
    @GetMapping("/{metricCode}/refs")
    @Operation(summary = "List referenced metric codes")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> listRefs(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.listRefs] metricCode={}", metricCode);
        metricDefService.getByCode(metricCode);
        List<String> refs = metricRefService.listRefsOf(metricCode).stream()
                .map(ref -> ref.getRefMetricCode())
                .toList();
        return ResponseWrapper.success(refs);
    }

    /**
     * List metrics that reference the given metric code.
     */
    @GetMapping("/{metricCode}/ref-by")
    @Operation(summary = "List upstream metric codes")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> listRefBy(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.listRefBy] metricCode={}", metricCode);
        metricDefService.getByCode(metricCode);
        List<String> refBy = metricRefService.listWhoRef(metricCode).stream()
                .map(ref -> ref.getMetricCode())
                .toList();
        return ResponseWrapper.success(refBy);
    }

    /**
     * List occupied slots for the given base dimension.
     */
    @GetMapping("/val-slots")
    @Operation(summary = "List occupied value slots")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<Set<Integer>> listSlots(@RequestParam("baseDim") @NotBlank String baseDim) {
        log.debug("[MetricDefController.listSlots] baseDim={}", baseDim);
        return ResponseWrapper.success(metricSlotService.listOccupied(baseDim));
    }

    /**
     * Create metric definition.
     * 返回 MetricDefRespDTO，不暴露 entity 内部字段。
     */
    @PostMapping
    @Operation(summary = "Create metric definition")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    public ResponseWrapper<MetricDefRespDTO> create(@Valid @RequestBody CreateMetricReqDTO req) {
        log.info("[MetricDefController.create] metricCode={}, baseDim={}, metricLevel={}",
                req.getMetricCode(), req.getBaseDim(), req.getMetricLevel());
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode(req.getMetricCode())
                .metricName(req.getMetricName())
                .metricNameEn(req.getMetricNameEn())
                .metricDesc(req.getMetricDesc())
                .baseDim(req.getBaseDim())
                .metricLevel(req.getMetricLevel())
                .calcFreq(req.getCalcFreq())
                .calcMode(req.getCalcMode())
                .calcLogicType(req.getCalcLogicType())
                .sqlText(req.getSqlText())
                .exprText(req.getExprText())
                .summaryRule(req.getSummaryRule())
                .refMetricCodes(req.getRefMetricCodes())
                .preferredSlot(req.getPreferredSlot())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(MetricAssembler.toRespDTO(metricLifecycleFacade.createMetric(cmd)));
    }

    /**
     * Update metric definition.
     * 返回 MetricDefRespDTO，不暴露 entity 内部字段。
     */
    @PutMapping("/{metricCode}")
    @Operation(summary = "Update metric definition")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    public ResponseWrapper<MetricDefRespDTO> update(@PathVariable("metricCode") @NotBlank String metricCode,
                                                    @Valid @RequestBody UpdateMetricReqDTO req) {
        log.info("[MetricDefController.update] metricCode={}", metricCode);
        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode(metricCode)
                .metricName(req.getMetricName())
                .metricNameEn(req.getMetricNameEn())
                .metricDesc(req.getMetricDesc())
                .calcFreq(req.getCalcFreq())
                .calcMode(req.getCalcMode())
                .calcLogicType(req.getCalcLogicType())
                .sqlText(req.getSqlText())
                .exprText(req.getExprText())
                .summaryRule(req.getSummaryRule())
                .refMetricCodes(req.getRefMetricCodes())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(MetricAssembler.toRespDTO(metricLifecycleFacade.updateMetric(cmd)));
    }

    /**
     * Disable metric definition by delete endpoint.
     */
    @DeleteMapping("/{metricCode}")
    @Operation(summary = "Delete metric definition")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "DELETE", resourceType = "METRIC_DEF", reasonRequired = true)
    public ResponseWrapper<Void> delete(@PathVariable("metricCode") @NotBlank String metricCode,
                                        @Valid @RequestBody ReleaseSlotReqDTO req) {
        log.info("[MetricDefController.delete] metricCode={}, reason={}", metricCode, req.getReason());
        metricLifecycleFacade.disableMetric(metricCode, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * Change metric status.
     */
    @PutMapping("/{metricCode}/status")
    @Operation(summary = "Change metric definition status")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "STATUS_CHANGE", resourceType = "METRIC_DEF", reasonRequired = true)
    public ResponseWrapper<Void> changeStatus(@PathVariable("metricCode") @NotBlank String metricCode,
                                              @Valid @RequestBody ChangeStatusReqDTO req) {
        log.info("[MetricDefController.changeStatus] metricCode={}, status={}, reason={}",
                metricCode, req.getStatus(), req.getReason());
        metricLifecycleFacade.disableMetric(metricCode, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * Release slot after metric is disabled.
     */
    @PostMapping("/{metricCode}/slot/release")
    @Operation(summary = "Release metric slot")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.CONFIG)
    @AuditLog(action = "SLOT_RELEASE", resourceType = "METRIC_DEF", reasonRequired = true)
    public ResponseWrapper<Void> releaseSlot(@PathVariable("metricCode") @NotBlank String metricCode,
                                             @Valid @RequestBody ReleaseSlotReqDTO req) {
        log.info("[MetricDefController.releaseSlot] metricCode={}, reason={}", metricCode, req.getReason());
        PerfMetricDef metricDef = metricDefService.getByCode(metricCode);
        metricSlotService.releaseSlot(metricDef.getId(), currentUserApi.getCurrentEmpId(), req.getReason());
        return ResponseWrapper.success();
    }

    /**
     * 指标试运行（03 §A.5）.
     *
     * <p>端点：{@code POST /api/perf/metrics/{metricCode}/trial-run}
     * <p>高危操作：执行指标 SQL / Groovy 但不写宽表、不写 run_task；仅返回样本。
     * <p>审计：{@code @AuditLog(action="METRIC_TRIAL_RUN", resourceType="PERF_METRIC_TRIAL")}.
     *
     * @param metricCode 指标编码（path）
     * @param req        请求体（dataDate / sampleSize / params）
     * @return 样本结果
     */
    @PostMapping("/{metricCode}/trial-run")
    @Operation(summary = "Trial run metric (execute without persistence)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "METRIC_TRIAL_RUN", resourceType = "PERF_METRIC_TRIAL")
    public ResponseWrapper<MetricTrialRespDTO> trialRun(@PathVariable("metricCode") @NotBlank String metricCode,
                                                        @Valid @RequestBody MetricTrialReqDTO req) {
        log.info("[MetricDefController.trialRun] metricCode={}, dataDate={}, sampleSize={}",
                metricCode, req.getDataDate(), req.getSampleSize());
        MetricTrialResult serviceResult = metricTrialService.trial(
                metricCode, req.getDataDate(), req.getSampleSize(), req.getParams());
        MetricTrialRespDTO dto = new MetricTrialRespDTO();
        dto.setMetricCode(metricCode);
        dto.setSampleSize(serviceResult.getSampleSize());
        dto.setTotalRows(serviceResult.getTotalRows());
        dto.setSamples(serviceResult.getSamples());
        dto.setExprResult(serviceResult.getExprResult());
        dto.setExecutionMillis(serviceResult.getExecutionMillis());
        return ResponseWrapper.success(dto);
    }
}
