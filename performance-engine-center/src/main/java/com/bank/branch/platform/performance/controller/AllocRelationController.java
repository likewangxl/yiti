package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.facade.assembler.AllocAssembler;
import com.bank.branch.platform.performance.service.AllocRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 客户业绩分配关系 REST 控制器 (3 个只读端点, 对齐 PT_RESOURCE P_PERF_ALLOC_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET /api/perf/alloc-relations          → P_PERF_ALLOC_CUR</li>
 *   <li>GET /api/perf/alloc-relations/history  → P_PERF_ALLOC_HIS</li>
 *   <li>GET /api/perf/alloc-relations/summary  → P_PERF_ALLOC_SUM</li>
 * </ul>
 *
 * <p>读操作端点仅标注 {@code @BizAuth(READ)}, 不带 {@code @AuditLog}
 * (Plan Task 5.4 L1580 钦定, 读操作不审计)。
 *
 * <p>summary 端点 empId 设计决策 (方案 C): Controller 不接受 empId 参数,
 * 永远以 {@link CurrentUserApi#getCurrentEmpId()} 为查询主体传入
 * {@link AllocRelationService#batchSummaryByEmps}. 该设计:
 * <ol>
 *   <li>天然满足 Plan L1577 "普通用户 summary 只返回自己" 的语义;</li>
 *   <li>规避普通用户越权查他人 summary 的风险, 无需引入额外的数据范围拦截分支;</li>
 *   <li>简化 Controller, 保持与 Service {@code batchSummaryByEmps} 批量签名兼容
 *       (内部把单 empId 包装成 Set.of)。</li>
 * </ol>
 * 管理员如需查他人或全员 summary, 由 V1.1 新增的管理员专用端点承担 (V1.0 本端点不覆盖)。
 *
 * <p>异常策略: Controller 不做 try-catch, 未登录时 {@link CurrentUserApi#getCurrentEmpId()}
 * 抛 {@code AuthException}, 由全局异常处理器映射 HTTP 401.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/alloc-relations")
@Tag(name = "Performance Alloc Relation", description = "客户业绩分配关系 (只读)")
@Validated
@RequiredArgsConstructor
public class AllocRelationController {

    private final CurrentUserApi currentUserApi;
    private final AllocRelationService allocRelationService;

    /**
     * 查询客户当前有效的分配关系.
     *
     * @param custId  客户 ID, 必填
     * @param bizKind 业务种类 (nullable)
     */
    @GetMapping
    @Operation(summary = "当前分配关系")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<CustAllocRelationDTO>> list(
            @RequestParam(value = "custId") @NotBlank String custId,
            @RequestParam(value = "bizKind", required = false) String bizKind) {
        // 记录 empId 便于排查 (未登录此处抛 AuthException → 401)
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[AllocRelationController.list] empId={}, custId={}, bizKind={}",
                empId, custId, bizKind);

        List<CustAllocRelation> entities = allocRelationService.getCurrentAllocations(custId, bizKind);
        return ResponseWrapper.success(AllocAssembler.toDtoList(entities));
    }

    /**
     * 查询客户在指定日期生效的分配关系快照.
     *
     * @param custId   客户 ID, 必填
     * @param asOfDate 快照基准日期, 必填 (ISO yyyy-MM-dd)
     */
    @GetMapping("/history")
    @Operation(summary = "历史分配关系")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<CustAllocRelationDTO>> history(
            @RequestParam(value = "custId") @NotBlank String custId,
            @RequestParam(value = "asOfDate") @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[AllocRelationController.history] empId={}, custId={}, asOfDate={}",
                empId, custId, asOfDate);

        List<CustAllocRelation> entities = allocRelationService.getAllocationHistory(custId, asOfDate);
        return ResponseWrapper.success(AllocAssembler.toDtoList(entities));
    }

    /**
     * 查询当前登录员工名下的分配关系汇总 (单员工 summary).
     *
     * <p>V1.0 简化: 以当前登录 empId 为汇总主体, 不接受外部 empId 参数 (方案 C).
     * 普通用户 → 只返回自己的汇总; 管理员 → 也只返回自己 empId 的汇总条目.
     *
     * @param bizKind  业务种类 (nullable)
     * @param asOfDate 截止日期 (nullable, 默认今天)
     */
    @GetMapping("/summary")
    @Operation(summary = "分配关系汇总")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<AllocSummaryDTO>> summary(
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        // empId 来自 currentUser, 普通用户天然只能看到自己 (方案 C)
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[AllocRelationController.summary] empId={}, bizKind={}, asOfDate={}",
                empId, bizKind, asOfDate);

        List<AllocSummaryDTO> result = allocRelationService.batchSummaryByEmps(
                Set.of(empId), bizKind, asOfDate);
        return ResponseWrapper.success(result);
    }
}
