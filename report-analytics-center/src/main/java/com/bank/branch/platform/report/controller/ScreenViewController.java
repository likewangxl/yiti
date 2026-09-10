package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏运行时整屏配置读取.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/view")
@Tag(name = "大屏-运行时", description = "渲染包读取（发布态/草稿态 + PROVINCE 点位；preview=draft 读草稿）")
@RequiredArgsConstructor
public class ScreenViewController {

    private final ScreenConfigService configService;

    @GetMapping("/{screenCode}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "按编码读取渲染包(默认发布态;preview=draft 读草稿,需登录+屏管理权限)")
    public ResponseWrapper<ScreenRenderRespDTO> view(
            @PathVariable String screenCode,
            @RequestParam(required = false) String preview) {
        // preview=draft 除整屏读取资源外，服务层还会校验 R_RPT_SCR_CV_GET
        // 管理端读取资源；普通查看者不能借用 published 端点读取草稿。
        String state = "draft".equalsIgnoreCase(preview) ? "draft" : "published";
        return ResponseWrapper.success(configService.getRenderByCode(screenCode, state));
    }

    @GetMapping("/catalog")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "读取当前用户可见的大屏运行时目录")
    public ResponseWrapper<List<ScreenEntryRespDTO>> catalog() {
        return ResponseWrapper.success(configService.listAuthorizedPublishedScreens());
    }
}
