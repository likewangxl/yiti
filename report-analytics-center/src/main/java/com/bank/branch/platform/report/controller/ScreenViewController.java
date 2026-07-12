package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏运行时整屏配置读取.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/view")
@Tag(name = "大屏-运行时", description = "整屏配置读取（屏+区块+PROVINCE 点位）")
@RequiredArgsConstructor
public class ScreenViewController {

    private final ScreenConfigService configService;

    @GetMapping("/{screenCode}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "按编码读取整屏配置")
    public ResponseWrapper<ScreenViewRespDTO> view(@PathVariable String screenCode) {
        return ResponseWrapper.success(configService.getViewByCode(screenCode));
    }
}
