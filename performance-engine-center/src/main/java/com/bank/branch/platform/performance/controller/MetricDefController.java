package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricCategoryDTO;
import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricExecuteReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateMetricReqDTO;
import com.bank.branch.platform.performance.facade.MetricLifecycleFacade;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，所有装配与编排
 * 下沉到 {@link MetricLifecycleFacade} 与 {@link MetricDefService} 的 {@code xxxDto}
 * 方法。{@code trialRun} / {@code execute} 的 run_task 状态读取与 sys_control 版本解析
 * 也下沉到 Facade.
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
     * V1.10：一次性返回所有指标定义（去分页）.
     *
     * <p>前端指标库页面工作模式：拉全集 → 客户端按 metric_category / metric_level 分组
     * 渲染树。原 V1.3 分页接口（pageNo/pageSize/PageResult）已废弃，前端 listMetrics
     * 不再走 unwrapPage。
     *
     * <p>数据范围：当前未走 PerfScopeHelper，沿用 V1.3 既有行为；如需收敛见
     * {@link MetricDefService#pageWithScope}，后续 controller 可切换.
     */
    @GetMapping
    @Operation(summary = "List all metric definitions (V1.10 no pagination)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<List<MetricDefRespDTO>> list(
            @RequestParam(value = "baseDim", required = false) String baseDim,
            @RequestParam(value = "metricLevel", required = false) Integer metricLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        log.debug("[MetricDefController.list] baseDim={}, metricLevel={}, status={}, keyword={}",
                baseDim, metricLevel, status, keyword);
        return ResponseWrapper.success(
                metricDefService.listAllDto(baseDim, metricLevel, status, keyword));
    }

    /**
     * V1.10：返回所有非空 metric_category 的去重项（{value, label} 对）.
     *
     * <p>对接前端指标库分类下拉 / 树形分组。V1.9 metric_category 直接存中文，
     * value == label；后续接入 sys_dict 翻译时仅扩展 label.
     */
    @GetMapping("/categories")
    @Operation(summary = "List distinct metric categories for filter/group (V1.10)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<List<MetricCategoryDTO>> listCategories() {
        log.debug("[MetricDefController.listCategories]");
        return ResponseWrapper.success(metricDefService.listCategories());
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
        return ResponseWrapper.success(metricDefService.getByCodeDto(metricCode));
    }

    /**
     * List referenced metric codes.
     */
    @GetMapping("/{metricCode}/refs")
    @Operation(summary = "List referenced metric codes")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> listRefs(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.listRefs] metricCode={}", metricCode);
        // 预先存在性校验：不存在抛 PERF-40001
        metricDefService.getByCodeDto(metricCode);
        return ResponseWrapper.success(metricRefService.listRefCodesOf(metricCode));
    }

    /**
     * List metrics that reference the given metric code.
     */
    @GetMapping("/{metricCode}/ref-by")
    @Operation(summary = "List upstream metric codes")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> listRefBy(@PathVariable("metricCode") @NotBlank String metricCode) {
        log.debug("[MetricDefController.listRefBy] metricCode={}", metricCode);
        metricDefService.getByCodeDto(metricCode);
        return ResponseWrapper.success(metricRefService.listCodesWhoRef(metricCode));
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
                .metricCategory(req.getMetricCategory())
                .status(req.getStatus())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        return ResponseWrapper.success(metricLifecycleFacade.createMetricDto(cmd));
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
        return ResponseWrapper.success(metricLifecycleFacade.updateMetricDto(cmd));
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
        // V1.6：放开 ACTIVE/DRAFT/DISABLED 三向切换
        metricLifecycleFacade.changeMetricStatus(metricCode, req.getStatus(), req.getReason(), currentUserApi.getCurrentEmpId());
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
        // V1.3 R4.1：Facade 内部读 entity.getId() 再调 slot release，Controller 不再持有 PerfMetricDef
        metricLifecycleFacade.releaseSlotByMetricCode(metricCode, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * 指标试运行（03 §A.5）.
     *
     * <p>端点：{@code POST /api/perf/metrics/{metricCode}/trial-run}
     * <p>高危操作：执行指标 SQL / Groovy 但不写宽表、不写 run_task；仅返回样本。
     * <p>审计：{@code @AuditLog(action="METRIC_TRIAL_RUN", resourceType="PERF_METRIC_TRIAL")}.
     */
    @PostMapping("/{metricCode}/trial-run")
    @Operation(summary = "Trial run metric (execute without persistence)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "METRIC_TRIAL_RUN", resourceType = "PERF_METRIC_TRIAL")
    public ResponseWrapper<MetricTrialRespDTO> trialRun(@PathVariable("metricCode") @NotBlank String metricCode,
                                                        @Valid @RequestBody MetricTrialReqDTO req) {
        log.info("[MetricDefController.trialRun] metricCode={}, dataDate={}, sampleSize={}",
                metricCode, req.getDataDate(), req.getSampleSize());
        // V1.3 R4.1：装配下沉到 Facade
        return ResponseWrapper.success(metricLifecycleFacade.trialRunDto(
                metricCode, req.getDataDate(), req.getSampleSize(), req.getParams()));
    }

    /**
     * 指标立即执行（03 §A.6）.
     *
     * <p>端点：{@code POST /api/perf/metrics/{metricCode}/execute}
     * <p>高危操作：写宽表 + 写 run_task；{@code cascade=true} 时触发下游级联刷新。
     * <p>审计：{@code @AuditLog(action="METRIC_EXECUTE", resourceType="PERF_METRIC_RUN",
     *         reasonRequired=true)}（07 §1.1 要求 reason 必填）.
     *
     * <p>路由逻辑与 sys_control 解析均下沉到 Facade，Controller 仅关心 DTO 封装.
     */
    @PostMapping("/{metricCode}/execute")
    @Operation(summary = "Execute metric immediately (with optional cascade refresh)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "METRIC_EXECUTE", resourceType = "PERF_METRIC_RUN", reasonRequired = true)
    public ResponseWrapper<RunTaskInfoDTO> execute(
            @PathVariable("metricCode") @NotBlank String metricCode,
            @Valid @RequestBody MetricExecuteReqDTO req) {
        log.info("[MetricDefController.execute] metricCode={}, dataDate={}, cascade={}, reason={}",
                metricCode, req.getDataDate(), req.getCascade(), req.getReason());
        // V1.3 R4.1：Facade.executeMetric 内部完成 sys_control 版本解析 + cascade 路由 +
        // run_task 真实 status 读取；Controller 不再感知 entity.
        return ResponseWrapper.success(metricLifecycleFacade.executeMetric(
                metricCode, req.getDataDate(), req.getCascade()));
    }
}
