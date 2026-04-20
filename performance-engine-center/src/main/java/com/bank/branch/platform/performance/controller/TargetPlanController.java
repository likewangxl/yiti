package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.controller.dto.CreateTargetPlanReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateTargetPlanReqDTO;
import com.bank.branch.platform.performance.service.TargetPlanService;
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
 * <p>骨架阶段: 所有方法抛 UnsupportedOperationException.
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
        throw new UnsupportedOperationException("TargetPlanController.list: red phase skeleton, awaiting green");
    }

    /**
     * 获取目标方案详情.
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取目标方案详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<TargetPlanDTO> getById(@PathVariable("id") @NotBlank String id) {
        throw new UnsupportedOperationException("TargetPlanController.getById: red phase skeleton, awaiting green");
    }

    /**
     * 新建目标方案.
     */
    @PostMapping
    @Operation(summary = "新增目标方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_PLAN")
    public ResponseWrapper<TargetPlanDTO> create(@Valid @RequestBody CreateTargetPlanReqDTO req) {
        throw new UnsupportedOperationException("TargetPlanController.create: red phase skeleton, awaiting green");
    }

    /**
     * 编辑目标方案 (部分更新, plan L1406 不强制 reason).
     */
    @PutMapping("/{id}")
    @Operation(summary = "编辑目标方案")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE", resourceType = "TARGET_PLAN")
    public ResponseWrapper<TargetPlanDTO> update(@PathVariable("id") @NotBlank String id,
                                                 @Valid @RequestBody UpdateTargetPlanReqDTO req) {
        throw new UnsupportedOperationException("TargetPlanController.update: red phase skeleton, awaiting green");
    }
}
