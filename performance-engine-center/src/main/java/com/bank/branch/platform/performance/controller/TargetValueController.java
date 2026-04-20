package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueBatchReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueReqDTO;
import com.bank.branch.platform.performance.service.TargetValueService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目标值 REST 控制器 (3 个端点, 对齐 PT_RESOURCE P_PERF_TGT_V_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET  /api/perf/target-values         → P_PERF_TGT_V_LIST</li>
 *   <li>POST /api/perf/target-values         → P_PERF_TGT_V_ADD (单条 upsert)</li>
 *   <li>POST /api/perf/target-values/batch   → P_PERF_TGT_V_BAT (批量 upsert, &lt;=500)</li>
 * </ul>
 *
 * <p>骨架阶段: 所有方法抛 UnsupportedOperationException, 仅供 IT "红" 阶段触发失败.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/target-values")
@Tag(name = "Performance Target Value", description = "目标值 upsert/查询")
@Validated
@RequiredArgsConstructor
public class TargetValueController {

    private final CurrentUserApi currentUserApi;
    private final TargetValueService targetValueService;

    /**
     * 按方案分页查询目标值.
     */
    @GetMapping
    @Operation(summary = "分页查询目标值")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<TargetValueDTO> list(
            @RequestParam(value = "planId") @NotBlank String planId,
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "subjectId", required = false) String subjectId,
            @RequestParam(value = "cycleKey", required = false) String cycleKey,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        throw new UnsupportedOperationException("TargetValueController.list: red phase skeleton, awaiting green");
    }

    /**
     * 单值 upsert.
     */
    @PostMapping
    @Operation(summary = "单值目标 upsert")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> create(@Valid @RequestBody UpsertTargetValueReqDTO req) {
        throw new UnsupportedOperationException("TargetValueController.create: red phase skeleton, awaiting green");
    }

    /**
     * 批量 upsert (单批上限 500, 超过则 400).
     */
    @PostMapping("/batch")
    @Operation(summary = "批量目标 upsert (<=500)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> batch(@Valid @RequestBody UpsertTargetValueBatchReqDTO req) {
        throw new UnsupportedOperationException("TargetValueController.batch: red phase skeleton, awaiting green");
    }
}
