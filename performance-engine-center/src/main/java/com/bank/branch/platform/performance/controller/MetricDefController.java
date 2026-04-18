package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateMetricReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.facade.MetricLifecycleFacade;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
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

    /**
     * List metric definitions with page result.
     */
    @GetMapping
    @Operation(summary = "List metric definitions")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PerfMetricDef> list(
            @RequestParam(value = "baseDim", required = false) String baseDim,
            @RequestParam(value = "metricLevel", required = false) Integer metricLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[MetricDefController.list] baseDim={}, metricLevel={}, status={}, keyword={}, pageNo={}, pageSize={}",
                baseDim, metricLevel, status, keyword, pageNo, pageSize);
        return ResponseWrapper.page(metricDefService.page(baseDim, metricLevel, status, keyword, pageNo, pageSize));
    }

    /**
     * Get metric definition by code.
     */
    @GetMapping("/{metricCode}")
    @Operation(summary = "Get metric definition by code")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfMetricDef> getByCode(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.getByCode] metricCode={}", metricCode);
        return ResponseWrapper.success(metricDefService.getByCode(metricCode));
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
     */
    @PostMapping
    @Operation(summary = "Create metric definition")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    public ResponseWrapper<PerfMetricDef> create(@Valid @RequestBody CreateMetricReqDTO req) {
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
        return ResponseWrapper.success(metricLifecycleFacade.createMetric(cmd));
    }

    /**
     * Update metric definition.
     */
    @PutMapping("/{metricCode}")
    @Operation(summary = "Update metric definition")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    public ResponseWrapper<PerfMetricDef> update(@PathVariable("metricCode") @NotBlank String metricCode,
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
        return ResponseWrapper.success(metricLifecycleFacade.updateMetric(cmd));
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
}
