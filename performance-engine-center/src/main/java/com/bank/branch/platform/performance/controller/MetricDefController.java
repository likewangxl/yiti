package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        validatePageArgs(pageNo, pageSize);
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
    public ResponseWrapper<Set<Integer>> listSlots(@RequestParam("baseDim") String baseDim) {
        validateBaseDim(baseDim);
        log.debug("[MetricDefController.listSlots] baseDim={}", baseDim);
        return ResponseWrapper.success(metricSlotService.listOccupied(baseDim));
    }

    private void validatePageArgs(int pageNo, int pageSize) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo 必须大于等于 1");
        }
        if (pageSize > 100) {
            throw new IllegalArgumentException("pageSize 必须小于等于 100");
        }
    }

    private void validateBaseDim(String baseDim) {
        if (baseDim == null || baseDim.isBlank()) {
            throw new IllegalArgumentException("baseDim 不能为空");
        }
    }
}
