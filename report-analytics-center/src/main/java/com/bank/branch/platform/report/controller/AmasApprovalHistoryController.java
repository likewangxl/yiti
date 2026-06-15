package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.AmasApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalDetailVO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalRowVO;
import com.bank.branch.platform.report.service.AmasApprovalQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业绩分配审批历史 REST 控制器（只读，归属报表分析中心）.
 *
 * <p>路径映射（PT_RESOURCE 见 2026-06-15-amas-approval-history-resources.sql）：
 * <ul>
 *   <li>GET /api/reports/amas-approvals               → R_RPT_AMAS_LIST（列表，申请时间倒序）</li>
 *   <li>GET /api/reports/amas-approvals/{perfAdjustNo} → R_RPT_AMAS_DET（详情：分配明细 + 审批流程）</li>
 * </ul>
 *
 * <p>只读端点仅 {@code @BizAuth}，不带 {@code @AuditLog}（与本模块其它只读报表一致）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/amas-approvals")
@Tag(name = "报表-业绩分配审批历史", description = "AMAS 业绩调整审批历史查看（列表 + 详情）")
@Validated
@RequiredArgsConstructor
public class AmasApprovalHistoryController {

    private final AmasApprovalQueryService amasApprovalQueryService;

    /**
     * 业绩分配审批历史列表（申请时间倒序，顶部查询项过滤）.
     */
    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "业绩分配审批历史列表")
    public ResponseWrapper<AmasApprovalRowVO> list(
            @Valid @ModelAttribute AmasApprovalQueryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<AmasApprovalRowVO> result = amasApprovalQueryService.pageList(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * 业绩分配审批历史详情（业绩分配数据 + 审批流程数据）.
     */
    @GetMapping("/{perfAdjustNo}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "业绩分配审批历史详情")
    public ResponseWrapper<AmasApprovalDetailVO> detail(@PathVariable("perfAdjustNo") String perfAdjustNo) {
        return ResponseWrapper.success(amasApprovalQueryService.detail(perfAdjustNo));
    }
}
