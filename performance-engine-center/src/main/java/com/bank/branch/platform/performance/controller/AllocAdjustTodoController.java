package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
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
 * 业绩调整 - 我的待审批 列表端点。
 * <p>权限：登录用户查自己的待办，empId 从 CurrentUserApi 取，不接受前端参数避免越权。
 * 鉴权沿用 PERF_CONFIG/LIST（与 AllocAdjustController 一致）。</p>
 */
@Slf4j
@Tag(name = "业绩调整-我的待审批")
@RestController
@RequestMapping("/api/perf/alloc-adjust")
@RequiredArgsConstructor
public class AllocAdjustTodoController {

    private final AllocAdjustTodoService service;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "我的待审批列表（业绩调整）")
    @GetMapping("/my-todos")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<AdjustTodoRespDTO>> listMyTodos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "allocDim", required = false) String allocDim,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        if (pageSize > 100) pageSize = 100;
        if (pageNo < 1) pageNo = 1;
        log.info("[AllocAdjustTodoController.listMyTodos] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }
}
