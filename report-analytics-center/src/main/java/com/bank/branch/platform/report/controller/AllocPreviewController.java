package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO;
import com.bank.branch.platform.report.service.AllocPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业绩调整分配预览接口
 *
 * <p>鉴权（2026-07-19 修复）：{@code @BizAuth(bizType = REPORT, action = READ)}，
 * 对应 {@code PT_RESOURCE} 早已登记的 {@code RES_ALLOC_PREVIEW}
 * （{@code GET /api/report/alloc-preview}，见 {@code docs/superpowers/sql/yiti_deploy_20260605.sql}）。
 * 修复前该 Controller 完全没有标注 {@code @BizAuth}，鉴权 AOP 因此不拦截本端点——
 * 详见模块 CLAUDE.md「关键实现要点与踩坑」历史记录。类级
 * {@code @RequestMapping} 与方法级路径拆分后总 URL 与修复前完全一致
 * （仍是历史遗留的单数 {@code /api/report/}，未随本次修复改为 {@code /api/reports/}，
 * 避免连带影响前端与 PT_RESOURCE 登记 URL）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/report")
@Tag(name = "分配预览", description = "业绩调整申请时的余额和分配关系预览")
public class AllocPreviewController {

    private final AllocPreviewService allocPreviewService;

    @GetMapping("/alloc-preview")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "原业绩分配预览（取分配调整申请审批通过的最后一条）")
    public ResponseWrapper<AllocPreviewRespDTO> preview(
            @RequestParam(required = false) String custType,
            @RequestParam String custNo,
            @RequestParam(required = false) String allocDim,
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String statisDt) {
        // 按客户编号取审批通过的最后一条分配；allocDim=ACCOUNT 只查按账号分配，RULE/空查两者。
        // custType/accountNo/statisDt 为兼容旧前端查询串保留入参但不再使用。
        return ResponseWrapper.success(allocPreviewService.preview(custNo, allocDim));
    }
}
