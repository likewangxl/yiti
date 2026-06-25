package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.AmasPriceApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasPriceApprovalVO;
import com.bank.branch.platform.report.service.AmasPriceApprovalQueryService;
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
 * 价格审批查询 REST 控制器（只读，归属报表分析中心）.
 *
 * <p>路径映射（PT_RESOURCE 见 2026-06-25-amas-price-approval-resources.sql）：
 * <ul>
 *   <li>GET /api/reports/amas-price-approvals              → R_RPT_PRICE_LIST（列表，申请时间倒序）</li>
 *   <li>GET /api/reports/amas-price-approvals/{priceApprId} → R_RPT_PRICE_DET（详情）</li>
 * </ul>
 *
 * <p>只读端点仅 {@code @BizAuth}，不带 {@code @AuditLog}（与本模块其它只读报表一致）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/amas-price-approvals")
@Tag(name = "报表-价格审批查询", description = "AMAS 价格审批查看（列表 + 详情）")
@Validated
@RequiredArgsConstructor
public class AmasPriceApprovalController {

    private final AmasPriceApprovalQueryService amasPriceApprovalQueryService;

    /**
     * 价格审批列表（申请时间倒序，顶部查询项过滤）.
     */
    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "价格审批列表")
    public ResponseWrapper<AmasPriceApprovalVO> list(
            @Valid @ModelAttribute AmasPriceApprovalQueryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<AmasPriceApprovalVO> result = amasPriceApprovalQueryService.pageList(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * 价格审批详情.
     */
    @GetMapping("/{priceApprId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "价格审批详情")
    public ResponseWrapper<AmasPriceApprovalVO> detail(@PathVariable("priceApprId") String priceApprId) {
        return ResponseWrapper.success(amasPriceApprovalQueryService.detail(priceApprId));
    }
}
