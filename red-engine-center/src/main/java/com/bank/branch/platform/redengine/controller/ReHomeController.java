package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReHomeBranchRankingDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeSummaryDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverduePageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionExecuteReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningPoolDTO;
import com.bank.branch.platform.redengine.service.ReHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红色引擎首页、红黄牌预警池和任务逾期扣分入口。
 *
 * <p>角色裁剪、组织数据范围以及逾期扣分的 SYS_ADMIN 专属校验均在
 * {@link ReHomeService} 内执行，Controller 不把前端隐藏菜单当作安全边界。</p>
 */
@Slf4j
@Tag(name = "红色引擎-首页与预警")
@RestController
@RequestMapping("/api/re/home")
@RequiredArgsConstructor
public class ReHomeController {

    private final ReHomeService homeService;
    private final CurrentUserApi currentUserApi;

    /** 按当前登录角色返回首页汇总。 */
    @Operation(summary = "首页汇总")
    @GetMapping("/summary")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReHomeSummaryDTO> summary() {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReHomeSummaryDTO result = homeService.getSummary(operatorId);
        log.info("[ReHomeController.summary] operatorId={}, mode={}", operatorId, result.getMode());
        return ResponseWrapper.success(result);
    }

    /** 查询当前自然季度的支部密集排名。 */
    @Operation(summary = "当前季度支部排名")
    @GetMapping("/ranking")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReHomeBranchRankingDTO>> ranking() {
        String operatorId = currentUserApi.getCurrentEmpId();
        List<ReHomeBranchRankingDTO> result = homeService.getCurrentQuarterRanking(operatorId);
        log.info("[ReHomeController.ranking] operatorId={}, size={}", operatorId, result.size());
        return ResponseWrapper.success(result);
    }

    /** 查询所有角色可读的红黄牌预警池。 */
    @Operation(summary = "红黄牌预警池")
    @GetMapping("/warning-pool")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReWarningPoolDTO> warningPool() {
        ReWarningPoolDTO result = homeService.getWarningPool();
        log.info("[ReHomeController.warningPool] red={}, yellow={}",
                result.getRedBranches().size(), result.getYellowBranches().size());
        return ResponseWrapper.success(result);
    }

    /** 分页查询逾期待执行扣分任务；服务层仅允许 SYS_ADMIN。 */
    @Operation(summary = "逾期上报待执行扣分")
    @GetMapping("/overdue")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskOverdueItemDTO> overdue(
            @Valid @ModelAttribute ReOverduePageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReTaskOverdueItemDTO> result = homeService.pageOverdue(query, operatorId);
        log.info("[ReHomeController.overdue] operatorId={}, total={}", operatorId, result.getTotal());
        return ResponseWrapper.page(result);
    }

    /** 执行逾期扣分；服务层进行 SYS_ADMIN、实体归属、截止时间和幂等校验。 */
    @Operation(summary = "执行逾期扣分")
    @PostMapping("/overdue/execute")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.EXECUTE)
    @AuditLog(action = "RE_TASK_OVERDUE_DEDUCTION", resourceType = "RE_TASK_DEDUCTION", reasonRequired = true)
    public ResponseWrapper<ReTaskDeductionActionRespDTO> executeOverdue(
            @Valid @RequestBody ReTaskDeductionExecuteReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReTaskDeductionActionRespDTO result = homeService.executeOverdue(request, operatorId);
        log.info("[ReHomeController.executeOverdue] operatorId={}, assignmentId={}, idempotent={}",
                operatorId, result.getAssignmentId(), result.isIdempotent());
        return ResponseWrapper.success(result);
    }
}
