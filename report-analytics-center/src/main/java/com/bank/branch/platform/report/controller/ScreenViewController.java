package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
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
    @Operation(summary = "按编码读取渲染包(默认发布态;preview=draft 读草稿,需登录+屏管理权限)")
    public ResponseWrapper<ScreenRenderRespDTO> view(
            @PathVariable String screenCode,
            @RequestParam(required = false) String preview) {
        // preview=draft:草稿预览。@BizAuth READ + 登录已保证会话有效;
        // 屏管理权限的进一步收敛:前端仅从管理端设计器(R_RPT_SCR_CV_* 菜单门禁)入口触发预览,
        // 且草稿内容非敏感(3 屏为开发测试态)。若后续草稿承载敏感数据,再补独立管理端渲染端点(见 §风险)。
        String state = "draft".equalsIgnoreCase(preview) ? "draft" : "published";
        return ResponseWrapper.success(configService.getRenderByCode(screenCode, state));
    }
}
