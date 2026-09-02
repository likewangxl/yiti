package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReCockpitOverviewDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverdueExecuteReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReRankingItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningItemDTO;
import com.bank.branch.platform.redengine.service.ReCockpitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红色引擎-驾驶舱/预警端点。
 * <p>移植自 redengine {@code CockpitController}（{@code business.controller}）。
 * 旧年度归档入口已由任务管理替代，archive 路径不再由本 Controller 注册；历史归档服务和数据
 * 仍保留用于兼容读取与后续迁移。</p>
 */
@Slf4j
@Tag(name = "红色引擎-驾驶舱")
@RestController
@RequestMapping("/api/re/cockpit")
@RequiredArgsConstructor
public class ReCockpitController {

    private final ReCockpitService reCockpitService;

    @Operation(summary = "驾驶舱总览统计")
    @GetMapping("/overview")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReCockpitOverviewDTO> getOverview() {
        ReCockpitOverviewDTO overview = reCockpitService.getOverview();
        log.info("[ReCockpitController.getOverview] totalSubmits={}", overview.getTotalSubmits());
        return ResponseWrapper.success(overview);
    }

    @Operation(summary = "组织排名")
    @GetMapping("/ranking")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReRankingItemDTO>> getRanking() {
        List<ReRankingItemDTO> ranking = reCockpitService.getRanking();
        log.info("[ReCockpitController.getRanking] size={}", ranking.size());
        return ResponseWrapper.success(ranking);
    }

    @Operation(summary = "逾期上报池")
    @GetMapping("/overdue")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReOverdueItemDTO>> getOverdueList() {
        List<ReOverdueItemDTO> overdueList = reCockpitService.getOverdueList();
        log.info("[ReCockpitController.getOverdueList] size={}", overdueList.size());
        return ResponseWrapper.success(overdueList);
    }

    @Operation(summary = "执行逾期扣分(高危)")
    @PostMapping("/overdue/execute")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.EXECUTE)
    @AuditLog(action = "RE_OVERDUE_EXECUTE", resourceType = "RE_OVERDUE_DEDUCTION", reasonRequired = true)
    public ResponseWrapper<Void> executeOverdue(@Valid @RequestBody ReOverdueExecuteReqDTO req) {
        reCockpitService.executeOverdue(req.getSubmitId(), req.getDeductionPoints());
        log.info("[ReCockpitController.executeOverdue] submitId={}, deductionPoints={}, reason={}",
                req.getSubmitId(), req.getDeductionPoints(), req.getReason());
        return ResponseWrapper.success();
    }

    @Operation(summary = "红牌预警池")
    @GetMapping("/warning/red")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReWarningItemDTO>> getRedWarning() {
        List<ReWarningItemDTO> redWarning = reCockpitService.getRedWarning();
        log.info("[ReCockpitController.getRedWarning] size={}", redWarning.size());
        return ResponseWrapper.success(redWarning);
    }

    @Operation(summary = "黄牌预警池")
    @GetMapping("/warning/yellow")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReWarningItemDTO>> getYellowWarning() {
        List<ReWarningItemDTO> yellowWarning = reCockpitService.getYellowWarning();
        log.info("[ReCockpitController.getYellowWarning] size={}", yellowWarning.size());
        return ResponseWrapper.success(yellowWarning);
    }

}
