package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.controller.dto.CreateTargetPlanReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateTargetPlanReqDTO;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.facade.assembler.TargetAssembler;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.cmd.CreateTargetPlanCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateTargetPlanCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标方案 REST 控制器 (4 个端点, 对齐 PT_RESOURCE P_PERF_TGT_P_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET  /api/perf/target-plans         → P_PERF_TGT_P_LIST</li>
 *   <li>GET  /api/perf/target-plans/{id}    → P_PERF_TGT_P_GET</li>
 *   <li>POST /api/perf/target-plans         → P_PERF_TGT_P_ADD</li>
 *   <li>PUT  /api/perf/target-plans/{id}    → P_PERF_TGT_P_UPD (update 不强制 reason, plan L1406)</li>
 * </ul>
 *
 * <p>异常策略: Controller 不做 try-catch, PerfException 冒泡至全局异常处理器,
 * 业务错误统一以 200 + 错误码返回 (NOT_FOUND → PERF-40403, CODE_DUP → PERF-40908,
 * KPI_SCHEME_INVALID → PERF-40915).
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/target-plans")
@Tag(name = "Performance Target Plan", description = "目标方案配置")
@Validated
@RequiredArgsConstructor
public class TargetPlanController {

    private final CurrentUserApi currentUserApi;
    private final TargetPlanService targetPlanService;

    /**
     * 分页查询目标方案.
     *
     * <p>过滤条件: kpiSchemeId / status / keyword (plan_code / plan_name 模糊匹配).
     */
    @GetMapping
    @Operation(summary = "分页查询目标方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<TargetPlanDTO> list(
            @RequestParam(value = "kpiSchemeId", required = false) String kpiSchemeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[TargetPlanController.list] kpiSchemeId={}, status={}, keyword={}, pageNo={}, pageSize={}",
                kpiSchemeId, status, keyword, pageNo, pageSize);
        PageResult<PerfTargetPlan> raw = targetPlanService.page(kpiSchemeId, status, keyword, pageNo, pageSize);
        List<TargetPlanDTO> dtos = new ArrayList<>(raw.getRecords().size());
        for (PerfTargetPlan plan : raw.getRecords()) {
            dtos.add(TargetAssembler.toDto(plan));
        }
        return ResponseWrapper.page(PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos));
    }

    /**
     * 获取目标方案详情. 不存在抛 PERF-40403.
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取目标方案详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<TargetPlanDTO> getById(@PathVariable("id") @NotBlank String id) {
        log.debug("[TargetPlanController.getById] id={}", id);
        PerfTargetPlan plan = targetPlanService.getById(id);
        return ResponseWrapper.success(TargetAssembler.toDto(plan));
    }

    /**
     * 新建目标方案.
     *
     * <p>业务校验 (由 Service 层抛出): planCode UK 冲突 → PERF-40908;
     * kpiSchemeId 对应 KPI 方案不存在或非 ACTIVE → PERF-40915;
     * effectiveDate null → PERF-40001 (DTO 层 @NotNull 已预先拦截为 400).
     */
    @PostMapping
    @Operation(summary = "新增目标方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_PLAN")
    public ResponseWrapper<TargetPlanDTO> create(@Valid @RequestBody CreateTargetPlanReqDTO req) {
        log.info("[TargetPlanController.create] planCode={}, kpiSchemeId={}, targetDim={}, targetCycle={}",
                req.getPlanCode(), req.getKpiSchemeId(), req.getTargetDim(), req.getTargetCycle());
        CreateTargetPlanCmd cmd = CreateTargetPlanCmd.builder()
                .planCode(req.getPlanCode())
                .planName(req.getPlanName())
                .kpiSchemeId(req.getKpiSchemeId())
                .targetDim(req.getTargetDim())
                .targetCycle(req.getTargetCycle())
                .effectiveDate(req.getEffectiveDate())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        PerfTargetPlan plan = targetPlanService.create(cmd);
        return ResponseWrapper.success(TargetAssembler.toDto(plan));
    }

    /**
     * 编辑目标方案 (部分更新).
     *
     * <p>Plan L1406 钦定: update 不强制 reason (与 delete/publish 不同),
     * 空 body / 全 null DTO 均合法, 返回未改动的方案视图。
     */
    @PutMapping("/{id}")
    @Operation(summary = "编辑目标方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE", resourceType = "TARGET_PLAN")
    public ResponseWrapper<TargetPlanDTO> update(@PathVariable("id") @NotBlank String id,
                                                 @Valid @RequestBody UpdateTargetPlanReqDTO req) {
        log.info("[TargetPlanController.update] id={}, planName={}, targetDim={}, targetCycle={}",
                id, req.getPlanName(), req.getTargetDim(), req.getTargetCycle());
        UpdateTargetPlanCmd cmd = UpdateTargetPlanCmd.builder()
                .planName(req.getPlanName())
                .targetDim(req.getTargetDim())
                .targetCycle(req.getTargetCycle())
                .effectiveDate(req.getEffectiveDate())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        PerfTargetPlan plan = targetPlanService.updateById(id, cmd);
        return ResponseWrapper.success(TargetAssembler.toDto(plan));
    }
}
