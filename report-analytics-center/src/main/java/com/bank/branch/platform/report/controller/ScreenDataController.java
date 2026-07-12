package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏统一取数端点（所有组件共用）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/data")
@Tag(name = "大屏-取数", description = "统一取数（dsId + 周期 + 上下文参数 → columns/rows）")
@RequiredArgsConstructor
public class ScreenDataController {

    private final ScreenDatasourceService datasourceService;

    @PostMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "统一取数")
    public ResponseWrapper<ScreenDataRespDTO> data(@Valid @RequestBody ScreenDataReqDTO req) {
        return ResponseWrapper.success(datasourceService.queryData(req));
    }
}
