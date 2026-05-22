package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustMineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 业绩调整 - 我的申请 列表端点。
 * <p>createdBy 硬约束 = currentEmpId，前端无法传 createdBy 越权查别人。</p>
 */
@Slf4j
@Tag(name = "业绩调整-我的申请")
@RestController
@RequestMapping("/api/perf/alloc-adjust")
@RequiredArgsConstructor
public class AllocAdjustMineController {

    private final AllocAdjustMineService service;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "我的申请列表（业绩调整）")
    @GetMapping("/my-applies")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<AdjustTodoRespDTO>> listMyApplies(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "allocDim", required = false) String allocDim,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        if (pageSize > 100) pageSize = 100;
        if (pageNo < 1) pageNo = 1;
        log.info("[AllocAdjustMineController.listMyApplies] empId={}, kw={}, dim={}, kind={}, status={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }
}
