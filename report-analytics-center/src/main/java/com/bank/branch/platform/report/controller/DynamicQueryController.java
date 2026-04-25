package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;
import com.bank.branch.platform.report.service.DynamicQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态查询 REST 控制器（A.2 POST /api/reports/dynamic-query，Task M1.2.1）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-动态查询", description = "维度+指标即席查询")
@Validated
@RequiredArgsConstructor
public class DynamicQueryController {

    private final DynamicQueryService dynamicQueryService;

    /**
     * 执行动态查询：根据 dim + subjectIds + metricCodes + dataDate 返回行列数据.
     */
    @PostMapping("/dynamic-query")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "A.2 执行动态查询")
    public ResponseWrapper<DynamicQueryRespDTO> dynamicQuery(@Valid @RequestBody DynamicQueryReqDTO req) {
        return ResponseWrapper.success(dynamicQueryService.execute(req));
    }
}
