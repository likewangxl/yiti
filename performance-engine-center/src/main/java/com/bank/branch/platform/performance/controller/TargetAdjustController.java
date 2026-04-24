package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.TargetAdjustCreateReqDTO;
import com.bank.branch.platform.performance.controller.dto.TargetAdjustRespDTO;
import com.bank.branch.platform.performance.service.adjust.TargetAdjustService;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitTargetAdjustCmd;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 目标修正申请 REST 控制器 (V1.2 Q3.2c).
 *
 * <p>路径映射：
 * <ul>
 *   <li>POST /api/perf/target-adjust/create → 提交申请（@AuditLog reason）</li>
 *   <li>GET /api/perf/target-adjust/{id} → 查询详情</li>
 *   <li>GET /api/perf/target-adjust/list → 分页列表（status/planId/subjectType/subjectId 过滤）</li>
 *   <li>POST /api/perf/target-adjust/{id}/withdraw → 撤回（@AuditLog reason）</li>
 * </ul>
 *
 * <p>单档授权：所有端点 {@code @BizAuth(bizType=PERF_CONFIG, action=<具体动作>)}，
 * 细粒度资源通过 PT_RESOURCE 绑定，当前阶段未新增 P_PERF_TARGET_ADJ_* 资源条目，
 * 留待 Q8 种子数据阶段补齐。
 *
 * <p>V1.2 简化：不同步 Flowable cancelProcess（与 Q2 AllocAdjust 一致），
 * withdraw 仅置本地状态为 REJECTED，技术债留待后续迭代.
 *
 * <p>V1.3 R4.1 改造：Controller 不再 import / 使用 entity，所有装配下沉到
 * {@link TargetAdjustService#submitDto} / {@link TargetAdjustService#getByIdDto}
 * / {@link TargetAdjustService#pageDto}.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/target-adjust")
@Tag(name = "Performance Target Adjust", description = "目标修正申请")
@Validated
@RequiredArgsConstructor
public class TargetAdjustController {

    private final CurrentUserApi currentUserApi;
    private final TargetAdjustService targetAdjustService;

    /**
     * 提交目标修正申请.
     *
     * <p>启动 {@code perf_target_adjust_v1} BPMN，返回申请 ID 与初始状态.
     */
    @PostMapping("/create")
    @Operation(summary = "提交目标修正申请")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "TARGET_ADJUST_CREATE", resourceType = "PERF_TARGET_ADJUST", reasonRequired = true)
    public ResponseWrapper<Map<String, String>> create(@Valid @RequestBody TargetAdjustCreateReqDTO req) {
        log.info("[TargetAdjustController.create] planId={}, subject={}:{}, cycleKey={}, adjustmentCount={}",
                req.getPlanId(), req.getSubjectType(), req.getSubjectId(), req.getCycleKey(),
                req.getAdjustments() == null ? 0 : req.getAdjustments().size());

        SubmitTargetAdjustCmd cmd = SubmitTargetAdjustCmd.builder()
                .planId(req.getPlanId())
                .subjectType(req.getSubjectType())
                .subjectId(req.getSubjectId())
                .cycleKey(req.getCycleKey())
                .ownerOrgId(req.getOwnerOrgId())
                .reason(req.getReason())
                .applicant(currentUserApi.getCurrentEmpId())
                .adjustments(toCmdAdjustments(req.getAdjustments()))
                .build();

        return ResponseWrapper.success(targetAdjustService.submitDto(cmd));
    }

    /**
     * 查询目标修正申请详情.
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询目标修正申请详情")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<TargetAdjustRespDTO> getById(@PathVariable("id") @NotBlank String id) {
        log.debug("[TargetAdjustController.getById] id={}", id);
        return ResponseWrapper.success(targetAdjustService.getByIdDto(id));
    }

    /**
     * 分页列表（支持 status / planId / subjectType / subjectId / ownerOrgId / createdBy 过滤）.
     *
     * <p>V1.2 Q3 阶段不叠加 BizScopeApi 的 owner_org_id 数据范围过滤，
     * 留待 Q7 阶段接入（BizScopeApi 注入）.
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询目标修正申请")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<TargetAdjustRespDTO> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "planId", required = false) String planId,
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "subjectId", required = false) String subjectId,
            @RequestParam(value = "ownerOrgId", required = false) String ownerOrgId,
            @RequestParam(value = "createdBy", required = false) String createdBy,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[TargetAdjustController.list] status={}, planId={}, subjectType={}, pageNo={}",
                status, planId, subjectType, pageNo);
        PageResult<TargetAdjustRespDTO> dtoPage = targetAdjustService.pageDto(
                status, planId, subjectType, subjectId, ownerOrgId, createdBy, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * 撤回申请（IN_APPROVAL / DRAFT → REJECTED）.
     */
    @PostMapping("/{id}/withdraw")
    @Operation(summary = "撤回目标修正申请（高危）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "TARGET_ADJUST_WITHDRAW", resourceType = "PERF_TARGET_ADJUST", reasonRequired = true)
    public ResponseWrapper<Void> withdraw(@PathVariable("id") @NotBlank String id,
                                          @RequestBody WithdrawReq req) {
        log.info("[TargetAdjustController.withdraw] id={}, reason={}",
                id, req == null ? null : req.getReason());
        String reason = req == null ? null : req.getReason();
        targetAdjustService.withdraw(id, reason, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /**
     * 请求 DTO → Service Cmd 的 adjustments 列表转换（空安全）.
     */
    private List<SubmitTargetAdjustCmd.TargetAdjustment> toCmdAdjustments(
            List<TargetAdjustCreateReqDTO.Adjustment> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubmitTargetAdjustCmd.TargetAdjustment> cmds = new ArrayList<>(reqItems.size());
        for (TargetAdjustCreateReqDTO.Adjustment it : reqItems) {
            cmds.add(SubmitTargetAdjustCmd.TargetAdjustment.builder()
                    .metricCode(it.getMetricCode())
                    .oldValue(it.getOldValue())
                    .newValue(it.getNewValue())
                    .build());
        }
        return cmds;
    }

    /**
     * 撤回请求简单 body（仅含 reason）.
     */
    @lombok.Data
    public static class WithdrawReq {
        private String reason;
    }
}
