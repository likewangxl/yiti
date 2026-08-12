package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.OrgGroupCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupMembersReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRolesReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileUpdateReqDTO;
import com.bank.branch.platform.auth.service.OrgGroupService;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机构本地画像与命名机构组管理控制器。
 *
 * <p>画像、成员和角色绑定均使用独立 URL，以便 PT_RESOURCE 独立授权和审计。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "机构画像与命名机构组", description = "大屏机构范围的本地画像和角色授权配置")
public class OrgGroupController {

    private final OrgGroupService orgGroupService;

    /** 查询机构列表并附带本地经营画像；城市仅筛选画像城市字段，不混入机构关键词。 */
    @GetMapping("/api/admin/org-profiles")
    @Operation(summary = "查询机构画像")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<OrgProfileDTO>> listProfiles(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "city", required = false) String city) {
        return ResponseWrapper.success(orgGroupService.listProfiles(keyword, city));
    }

    /** 新增或更新机构画像；不写回 EXT_ORG_INFO。 */
    @PutMapping("/api/admin/org-profiles/{orgCode}")
    @Operation(summary = "保存机构画像")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "ORG_PROFILE_CHANGE", resourceType = "PT_ORG_PROFILE", reasonRequired = true,
            serviceManaged = true)
    public ResponseWrapper<OrgProfileDTO> saveProfile(
            @PathVariable String orgCode,
            @Valid @RequestBody OrgProfileUpdateReqDTO req) {
        return ResponseWrapper.success(orgGroupService.saveProfile(orgCode, req));
    }

    /** 查询命名机构组列表。 */
    @GetMapping("/api/admin/org-groups")
    @Operation(summary = "查询命名机构组")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<OrgGroupDTO>> listGroups() {
        return ResponseWrapper.success(orgGroupService.listGroups());
    }

    /** 新建命名机构组。 */
    @PostMapping("/api/admin/org-groups")
    @Operation(summary = "新建命名机构组")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "ORG_GROUP_CREATE", resourceType = "PT_ORG_GROUP", reasonRequired = true,
            serviceManaged = true)
    public ResponseWrapper<OrgGroupDTO> createGroup(
            @Valid @RequestBody OrgGroupCreateReqDTO req) {
        return ResponseWrapper.success(orgGroupService.createGroup(req));
    }

    /** 修改命名机构组基本信息。 */
    @PutMapping("/api/admin/org-groups/{groupCode}")
    @Operation(summary = "修改命名机构组")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "ORG_GROUP_CHANGE", resourceType = "PT_ORG_GROUP", reasonRequired = true,
            serviceManaged = true)
    public ResponseWrapper<OrgGroupDTO> updateGroup(
            @PathVariable String groupCode,
            @Valid @RequestBody OrgGroupUpdateReqDTO req) {
        return ResponseWrapper.success(orgGroupService.updateGroup(groupCode, req));
    }

    /** 覆盖保存机构组直接成员。 */
    @PutMapping("/api/admin/org-groups/{groupCode}/members")
    @Operation(summary = "覆盖保存机构组成员")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "ORG_GROUP_MEMBER_CHANGE", resourceType = "PT_ORG_GROUP_MEMBER", reasonRequired = true,
            serviceManaged = true)
    public ResponseWrapper<OrgGroupDTO> replaceMembers(
            @PathVariable String groupCode,
            @Valid @RequestBody OrgGroupMembersReplaceReqDTO req) {
        return ResponseWrapper.success(orgGroupService.replaceMembers(groupCode, req));
    }

    /** 覆盖保存角色-机构组授权。 */
    @PutMapping("/api/admin/org-groups/{groupCode}/roles")
    @Operation(summary = "覆盖保存机构组角色授权")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @AuditLog(action = "ORG_GROUP_ROLE_CHANGE", resourceType = "PT_ROLE_ORG_GROUP", reasonRequired = true,
            serviceManaged = true)
    public ResponseWrapper<OrgGroupDTO> replaceRoles(
            @PathVariable String groupCode,
            @Valid @RequestBody OrgGroupRolesReplaceReqDTO req) {
        return ResponseWrapper.success(orgGroupService.replaceRoles(groupCode, req));
    }
}
