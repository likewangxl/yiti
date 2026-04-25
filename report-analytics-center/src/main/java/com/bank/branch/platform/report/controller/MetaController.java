package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.QueryDimensionRespDTO;
import com.bank.branch.platform.report.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表元数据 REST 控制器（A.1 GET /api/reports/query-dimensions，Task M1.1.1）.
 *
 * <p>鉴权：{@code @BizAuth(bizType=REPORT, action=READ)} + PT_RESOURCE ID
 * {@code R_RPT_META_QD}（M1.6 统一注册）。
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-元数据", description = "维度+指标树/分组")
@Validated
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    /**
     * 获取指定维度下的指标树（分组）.
     *
     * @param dim 维度 EMP / ORG / CUST
     */
    @GetMapping("/query-dimensions")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "A.1 获取查询维度+指标树")
    public ResponseWrapper<QueryDimensionRespDTO> getQueryDimensions(
            @RequestParam @NotBlank(message = "dim 不能为空") String dim) {
        return ResponseWrapper.success(metaService.getQueryDimensions(dim));
    }
}
