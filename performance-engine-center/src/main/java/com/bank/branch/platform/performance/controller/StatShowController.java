package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.service.StatShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

/**
 * 财务统计展示表只读查询 REST 控制器.
 *
 * <p>对接外部数仓抽数的两张展示表（员工维度 / 客户维度），仅提供条件分页查询，供报表展示。
 * <ul>
 *   <li>GET /api/perf/stat-show/emp  → XAN_M9B_EMP_STAT_SHOW3（员工维度）</li>
 *   <li>GET /api/perf/stat-show/cust → XAN_M98_CUST_STAT_SHOW3（客户维度）</li>
 * </ul>
 *
 * <p>只读端点仅标 {@code @BizAuth(LIST)}，不带 {@code @AuditLog}（读操作不审计）。
 * 行以 LinkedHashMap 投影返回，保留数仓列顺序。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/stat-show")
@RequiredArgsConstructor
@Tag(name = "绩效-财务统计展示", description = "员工/客户维度财务统计展示表只读查询")
public class StatShowController {

    private final StatShowService statShowService;

    /**
     * 员工维度财务统计展示表分页查询.
     */
    @GetMapping("/emp")
    @Operation(summary = "员工维度财务统计展示表分页查询")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<LinkedHashMap<String, Object>> listEmpStat(
            @RequestParam(required = false) String statisDt,
            @RequestParam(required = false) String branchNo,
            @RequestParam(required = false) String empId,
            @RequestParam(required = false) String indType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.debug("[StatShowController.listEmpStat] statisDt={}, branchNo={}, empId={}, indType={}, "
                + "keyword={}, pageNo={}, pageSize={}", statisDt, branchNo, empId, indType, keyword,
                pageNo, pageSize);
        PageResult<LinkedHashMap<String, Object>> page = statShowService.pageEmpStat(
                statisDt, branchNo, empId, indType, keyword, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }

    /**
     * 客户维度财务统计展示表分页查询.
     */
    @GetMapping("/cust")
    @Operation(summary = "客户维度财务统计展示表分页查询")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<LinkedHashMap<String, Object>> listCustStat(
            @RequestParam(required = false) String statisDt,
            @RequestParam(required = false) String branchNo,
            @RequestParam(required = false) String custId,
            @RequestParam(required = false) String custType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.debug("[StatShowController.listCustStat] statisDt={}, branchNo={}, custId={}, custType={}, "
                + "keyword={}, pageNo={}, pageSize={}", statisDt, branchNo, custId, custType, keyword,
                pageNo, pageSize);
        PageResult<LinkedHashMap<String, Object>> page = statShowService.pageCustStat(
                statisDt, branchNo, custId, custType, keyword, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }
}
