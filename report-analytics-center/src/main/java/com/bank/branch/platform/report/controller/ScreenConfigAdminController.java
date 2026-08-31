package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenAccessRoleSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.MapRegionMetricDTO;
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
 * 大屏元数据、画布入口与地图点位配置管理（管理端）.
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin")
@Tag(name = "大屏-布局配置", description = "屏元数据、独立画布保存与地图点位维护")
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
    @Operation(summary = "新建大屏元数据（空画布）")
    public ResponseWrapper<Long> createScreen(@Valid @RequestBody ScreenCreateReqDTO req) {
        return ResponseWrapper.success(configService.createScreen(req));
    }

    @PutMapping("/screens/{id}/metadata")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "更新大屏元数据与机构范围（不修改画布区块）")
    public ResponseWrapper<Long> updateMetadata(@PathVariable Long id,
                                                 @Valid @RequestBody ScreenMetadataUpdateReqDTO req) {
        return ResponseWrapper.success(configService.updateScreenMetadata(id, req));
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

    @GetMapping("/screens/{id}/map-region-metrics")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "查询地图经营指标（无数据不兜底）")
    public ResponseWrapper<List<MapRegionMetricDTO>> listMapRegionMetrics(@PathVariable Long id) {
        return ResponseWrapper.success(configService.listMapRegionMetrics(id));
    }

    @GetMapping("/screens/{id}/access-roles")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "查询大屏查看角色白名单")
    public ResponseWrapper<List<String>> listAccessRoles(@PathVariable Long id) {
        return ResponseWrapper.success(configService.listAccessRoleCodes(id));
    }

    @PutMapping("/screens/{id}/access-roles")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.PERMISSION_CHANGE)
    @Operation(summary = "覆盖保存大屏查看角色白名单")
    public ResponseWrapper<Void> saveAccessRoles(@PathVariable Long id,
                                                   @Valid @RequestBody ScreenAccessRoleSaveReqDTO req) {
        configService.saveAccessRoleCodes(id, req.getRoleCodes(), req.getReason(), req.getExpectedVersion());
        return ResponseWrapper.success();
    }
}
