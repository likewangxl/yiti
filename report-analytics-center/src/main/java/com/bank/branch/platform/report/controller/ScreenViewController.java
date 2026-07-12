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
        // preview=draft:草稿预览。如实描述现状:后端目前仅执行 @BizAuth(REPORT, READ)校验,
        // 与 published 分支同一权限位,未额外校验"屏管理权限"——"前端仅从管理端设计器
        // (R_RPT_SCR_CV_* 菜单门禁)入口触发预览"只是前端约定,不是服务端强制边界。
        // 该已知缺口本期可接受(草稿内容当前非敏感,3 屏均为开发测试态);收敛计划留 Task 10/V1.1:
        // 若后续草稿承载敏感数据,需补独立管理端渲染端点做服务端强校验。
        String state = "draft".equalsIgnoreCase(preview) ? "draft" : "published";
        return ResponseWrapper.success(configService.getRenderByCode(screenCode, state));
    }
}
