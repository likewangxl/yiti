package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.AllocAdjustApplyQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyDetailVO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyRowVO;
import com.bank.branch.platform.report.service.AllocAdjustQueryService;
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
 * 业绩调整（PERF_ALLOC_ADJUST_APPLY）REST 控制器（只读，归属报表分析中心）.
 *
 * <p>路径映射（PT_RESOURCE 见 2026-06-15-alloc-adjust-history-resources.sql）：
 * <ul>
 *   <li>GET /api/reports/alloc-adjust-applies        → R_RPT_ALC_LIST（列表，申请时间倒序）</li>
 *   <li>GET /api/reports/alloc-adjust-applies/{id}    → R_RPT_ALC_DET（详情：分配明细）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/alloc-adjust-applies")
@Tag(name = "报表-业绩调整", description = "平台业绩调整申请查看（列表 + 详情）")
@Validated
@RequiredArgsConstructor
public class AllocAdjustHistoryController {

    private final AllocAdjustQueryService allocAdjustQueryService;

    /**
     * 业绩调整申请列表（申请时间倒序，顶部查询项过滤）.
     */
    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "业绩调整申请列表")
    public ResponseWrapper<AllocAdjustApplyRowVO> list(
            @Valid @ModelAttribute AllocAdjustApplyQueryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<AllocAdjustApplyRowVO> result = allocAdjustQueryService.pageList(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * 业绩调整申请详情（申请信息 + 业绩分配数据）.
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "业绩调整申请详情")
    public ResponseWrapper<AllocAdjustApplyDetailVO> detail(@PathVariable("id") String id) {
        return ResponseWrapper.success(allocAdjustQueryService.detail(id));
    }
}
