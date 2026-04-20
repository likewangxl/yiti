package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.controller.dto.AddKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.PublishKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
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

/**
 * KPI 方案 REST 控制器 (9 个端点, 对齐 PT_RESOURCE P_PERF_KPI_*).
 *
 * <p>RED 阶段占位 - 所有方法抛 UnsupportedOperationException.
 * <p>GREEN 阶段通过 {@link KpiSchemeService} / {@link KpiItemService} 实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/kpi-schemes")
@Tag(name = "Performance KPI Scheme", description = "KPI 方案配置")
@Validated
@RequiredArgsConstructor
public class KpiSchemeController {

    private final CurrentUserApi currentUserApi;
    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;

    /** 分页查询 KPI 方案. */
    @GetMapping
    @Operation(summary = "分页查询 KPI 方案")
    public ResponseWrapper<PageResult<KpiSchemeDTO>> list(
            @RequestParam(value = "schemeCode", required = false) String schemeCode,
            @RequestParam(value = "cycleType", required = false) String cycleType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        throw new UnsupportedOperationException("RED");
    }

    /** 获取 KPI 方案详情. */
    @GetMapping("/{id}")
    @Operation(summary = "获取 KPI 方案详情")
    public ResponseWrapper<KpiSchemeDTO> getById(@PathVariable("id") @NotBlank String id) {
        throw new UnsupportedOperationException("RED");
    }

    /** 新增 KPI 方案. */
    @PostMapping
    @Operation(summary = "新增 KPI 方案")
    public ResponseWrapper<KpiSchemeDTO> create(@Valid @RequestBody CreateKpiSchemeReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 编辑 KPI 方案. */
    @PutMapping("/{id}")
    @Operation(summary = "编辑 KPI 方案")
    public ResponseWrapper<KpiSchemeDTO> update(@PathVariable("id") @NotBlank String id,
                                                @Valid @RequestBody UpdateKpiSchemeReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 删除 / 停用 KPI 方案 (高危, reason 必填). */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 KPI 方案 (高危)")
    public ResponseWrapper<Void> delete(@PathVariable("id") @NotBlank String id,
                                        @Valid @RequestBody ReleaseSlotReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 发布 KPI 方案 (高危, reason 必填). */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布 KPI 方案 (高危)")
    public ResponseWrapper<KpiSchemeDTO> publish(@PathVariable("id") @NotBlank String id,
                                                 @Valid @RequestBody PublishKpiSchemeReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 方案内新增指标项. */
    @PostMapping("/{id}/items")
    @Operation(summary = "方案内新增指标项")
    public ResponseWrapper<KpiItemDTO> addItem(@PathVariable("id") @NotBlank String id,
                                               @Valid @RequestBody AddKpiItemReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 编辑指标项. */
    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "编辑指标项")
    public ResponseWrapper<KpiItemDTO> updateItem(@PathVariable("id") @NotBlank String id,
                                                  @PathVariable("itemId") @NotBlank String itemId,
                                                  @Valid @RequestBody UpdateKpiItemReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }

    /** 删除指标项 (高危, reason 必填). */
    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "删除指标项 (高危)")
    public ResponseWrapper<Void> deleteItem(@PathVariable("id") @NotBlank String id,
                                            @PathVariable("itemId") @NotBlank String itemId,
                                            @Valid @RequestBody ReleaseSlotReqDTO req) {
        throw new UnsupportedOperationException("RED");
    }
}
