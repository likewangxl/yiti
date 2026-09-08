package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.OrgLocationCapabilitiesDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodeCandidateDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodePreviewReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationUpdateReqDTO;
import com.bank.branch.platform.auth.location.service.OrgLocationService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 机构地址和坐标管理 REST 接口。资源 URL 与动作供主代理登记 PT_RESOURCE。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/org-locations")
@Tag(name = "机构位置", description = "机构详细地址、坐标和地址解析候选管理")
public class OrgLocationController {

    private final OrgLocationService orgLocationService;

    /** 查询位置能力；不返回任何 Key。 */
    @GetMapping("/capabilities")
    @Operation(summary = "查询机构位置能力")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<OrgLocationCapabilitiesDTO> capabilities() {
        return ResponseWrapper.success(orgLocationService.capabilities());
    }

    /** 查询单个机构位置。 */
    @GetMapping("/{orgCode}")
    @Operation(summary = "查询机构位置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<OrgLocationDTO> getLocation(@PathVariable String orgCode) {
        return ResponseWrapper.success(orgLocationService.getLocation(orgCode));
    }

    /** 覆盖保存地址/坐标，版本与原因由服务层强制校验。 */
    @PutMapping("/{orgCode}")
    @Operation(summary = "保存机构位置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<OrgLocationDTO> updateLocation(
            @PathVariable String orgCode, @Valid @RequestBody OrgLocationUpdateReqDTO req) {
        return ResponseWrapper.success(orgLocationService.updateLocation(orgCode, req));
    }

    /** 预览服务端地址解析候选；不会写入位置表。 */
    @PostMapping("/{orgCode}/geocode-preview")
    @Operation(summary = "预览机构地址解析候选")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.EXECUTE)
    public ResponseWrapper<List<OrgLocationGeocodeCandidateDTO>> geocodePreview(
            @PathVariable String orgCode, @Valid @RequestBody OrgLocationGeocodePreviewReqDTO req) {
        return ResponseWrapper.success(orgLocationService.previewGeocode(orgCode, req));
    }
}
