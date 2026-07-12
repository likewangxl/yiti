package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏布局/区块/地图点位配置管理（管理端）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin")
@Tag(name = "大屏-布局配置", description = "屏/区块整体保存 + 地图点位维护")
@Validated
@RequiredArgsConstructor
public class ScreenConfigAdminController {

    private final ScreenConfigService configService;

    @GetMapping("/screens")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "大屏列表（概要）")
    public ResponseWrapper<List<ScreenDetailRespDTO>> listScreens() {
        return ResponseWrapper.success(configService.listScreens());
    }

    @GetMapping("/screens/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "大屏详情（含区块）")
    public ResponseWrapper<ScreenDetailRespDTO> getScreen(@PathVariable Long id) {
        return ResponseWrapper.success(configService.getScreen(id));
    }

    @PostMapping("/screens")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "大屏整体保存（屏 + 区块，upsert）")
    public ResponseWrapper<Long> saveScreen(@Valid @RequestBody ScreenSaveReqDTO req) {
        return ResponseWrapper.success(configService.saveScreen(req));
    }

    @DeleteMapping("/screens/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    @Operation(summary = "删除大屏")
    public ResponseWrapper<Void> deleteScreen(@PathVariable Long id) {
        configService.deleteScreen(id);
        return ResponseWrapper.success();
    }

    @GetMapping("/map-points")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "地图点位列表")
    public ResponseWrapper<List<MapPointDTO>> listMapPoints() {
        return ResponseWrapper.success(configService.listMapPoints());
    }

    @PutMapping("/map-points")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "地图点位整表覆盖保存")
    public ResponseWrapper<Void> saveMapPoints(@Valid @RequestBody List<MapPointDTO> points) {
        configService.saveMapPoints(points);
        return ResponseWrapper.success();
    }
}
