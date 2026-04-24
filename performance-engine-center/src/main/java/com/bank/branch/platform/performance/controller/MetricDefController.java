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
import com.bank.branch.platform.performance.controller.dto.MetricExecuteReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialReqDTO;
import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateMetricReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.facade.MetricLifecycleFacade;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.CascadeRefresher;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.SysControlService;
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
    private final MetricCalcService metricCalcService;
    private final CascadeRefresher cascadeRefresher;
    private final SysControlService sysControlService;
    private final PerfRunTaskMapper perfRunTaskMapper;

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
        // V1.3 R3.2：装配 03 §A.5 对齐的元数据字段（taskId/status/startedAt/endedAt）。
        // Trial 不落 perf_run_task（P3.1 契约：全程无侧效），taskId 生成一个 UUID 用于消费方追踪。
        java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
        String trialTaskId = java.util.UUID.randomUUID().toString().replace("-", "");
        MetricTrialResult serviceResult;
        try {
            serviceResult = metricTrialService.trial(
                    metricCode, req.getDataDate(), req.getSampleSize(), req.getParams());
        } catch (RuntimeException ex) {
            // 失败时继续走统一异常处理，Service 抛出的 PerfException 由全局 handler 转 ResponseWrapper；
            // 本 catch 只在需要装配 FAILED 状态 DTO 时介入——目前异常直接透传，保持 V1.1 行为不变。
            throw ex;
        }
        java.time.LocalDateTime endedAt = java.time.LocalDateTime.now();
        MetricTrialRespDTO dto = MetricTrialRespDTO.builder()
                .taskId(trialTaskId)
                .metricCode(metricCode)
                .sampleSize(serviceResult.getSampleSize())
                .totalRows(serviceResult.getTotalRows())
                .status("SUCCESS")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .errorMsg(null)
                .exprResult(serviceResult.getExprResult())
                .executionMillis(serviceResult.getExecutionMillis())
                .sampleRows(serviceResult.getSamples())
                .build();
        return ResponseWrapper.success(dto);
    }

    /**
     * 指标立即执行（03 §A.6）.
     *
     * <p>端点：{@code POST /api/perf/metrics/{metricCode}/execute}
     * <p>高危操作：写宽表 + 写 run_task；{@code cascade=true} 时触发下游级联刷新。
     * <p>审计：{@code @AuditLog(action="METRIC_EXECUTE", resourceType="PERF_METRIC_RUN",
     *         reasonRequired=true)}（07 §1.1 要求 reason 必填）.
     *
     * <p>路由逻辑：
     * <ul>
     *   <li>{@code cascade=false} → {@link MetricCalcService#calcMetric} 仅刷新本指标</li>
     *   <li>{@code cascade=true} （默认）→ {@link CascadeRefresher#refreshCascade} 按拓扑序刷新根 + 下游</li>
     * </ul>
     *
     * @param metricCode 指标编码（path）
     * @param req        执行请求（dataDate / cascade / async / reason）
     * @return 根任务 ID + 初始状态（PENDING/RUNNING/SUCCESS）
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

        // 预校验指标存在性：不存在时在调用 Service 前就抛 PERF-40001，避免产生孤立 run_task
        PerfMetricDef def = metricDefService.getByCode(metricCode);

        // 解析版本：从 sys_control 当前生效版本读取（可为空时使用兜底版本）
        String version;
        try {
            SysControl current = sysControlService.getCurrentVersion(def.getBaseDim());
            version = current.getCurrentVersion();
        } catch (Exception ex) {
            // sys_control 未初始化时使用兜底版本（测试场景 / 首次执行）；
            // 生产环境通过 System Control Init 流程保证此不发生。
            log.warn("[MetricDefController.execute] sys_control 读取失败，采用兜底版本: {}", ex.getMessage());
            version = "v_default";
        }

        boolean cascade = req.getCascade() == null ? Boolean.TRUE : req.getCascade();
        String taskId;
        if (cascade) {
            taskId = cascadeRefresher.refreshCascade(metricCode, req.getDataDate(), version);
        } else {
            taskId = metricCalcService.calcMetric(metricCode, req.getDataDate(), version);
        }

        // V1.3 R3.3：回归 03 §A.6 契约，使用 RunTaskInfoDTO 替代临时 Map<String,Object>.
        // 从 perf_run_task 读取真实状态（Service 可能已同步完成为 SUCCESS/FAILED，也可能仍 RUNNING）；
        // 查不到时退化为 RUNNING 占位（极端竞态下 Service 未及时 commit）。
        PerfRunTask task = perfRunTaskMapper.selectById(taskId);
        String status = task != null && task.getStatus() != null ? task.getStatus() : "RUNNING";
        return ResponseWrapper.success(RunTaskInfoDTO.builder()
                .taskId(taskId)
                .status(status)
                .metricCode(metricCode)
                .dataDate(req.getDataDate())
                .version(version)
                .build());
    }
}
