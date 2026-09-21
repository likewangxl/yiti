package com.bank.branch.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.auth.api.dto.OrgGroupCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupMembersReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRolesReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileUpdateReqDTO;
import com.bank.branch.platform.auth.location.persistence.PtOrgLocation;
import com.bank.branch.platform.auth.location.service.OrgLocationService;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtOrgGroup;
import com.bank.branch.platform.auth.entity.PtOrgGroupMember;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleOrgGroup;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.OrgGroupMapper;
import com.bank.branch.platform.auth.mapper.OrgGroupMemberMapper;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.OrgProfileMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.RoleOrgGroupMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 机构画像、命名机构组及角色-机构组授权服务。
 *
 * <p>本服务只维护 auth 自有配置表。EXT_ORG_INFO 是外部同步只读表，所有写操作
 * 都在进入画像或组配置前重新校验外部机构有效性，避免把同步表当成本地配置表修改。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgGroupService {

    private static final String GROUP_PURPOSE_REPORT_SCREEN = "REPORT_SCREEN";
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private static final String COORD_SYS_GCJ02 = "GCJ02";
    private static final Set<String> ORG_NATURES = Set.of(
            "DEPARTMENT", "LOCAL_BRANCH", "SECONDARY_BRANCH", "OUTLET", "OTHER");
    private static final Set<String> OPERATING_LEVELS = Set.of("PRIMARY", "SUBORDINATE", "NONE");

    private final OrgMapper orgMapper;
    private final OrgProfileMapper orgProfileMapper;
    private final OrgGroupMapper orgGroupMapper;
    private final OrgGroupMemberMapper orgGroupMemberMapper;
    private final RoleOrgGroupMapper roleOrgGroupMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgGroupAuditService orgGroupAuditService;
    /** 可选的位置台账补充；位置能力关闭时服务返回空集且旧画像链路保持可用。 */
    private final OrgLocationService orgLocationService;

    /**
     * 查询全部机构并附带本地画像。外部机构名称/状态来自 EXT_ORG_INFO，画像字段来自本地表。
     *
     * @param keyword 机构编码或名称关键字，可空
     * @return 机构画像列表
     */
    public List<OrgProfileDTO> listProfiles(String keyword) {
        return listProfiles(keyword, null);
    }

    /**
     * 查询全部机构并附带本地画像，机构关键词与本地城市画像采用独立的 AND 筛选语义。
     *
     * @param keyword 机构编码或名称关键字，可空
     * @param city    本地画像城市编码或名称关键字，可空；不会与机构关键词混用
     * @return 机构画像列表
     */
    public List<OrgProfileDTO> listProfiles(String keyword, String city) {
        try {
            List<ExtOrgInfo> orgs = safeList(orgMapper.selectAll());
            Map<String, PtOrgProfile> profiles = safeList(orgProfileMapper.selectList(
                            new LambdaQueryWrapper<PtOrgProfile>()))
                    .stream()
                    .collect(Collectors.toMap(PtOrgProfile::getOrgCode, Function.identity(), (left, right) -> left));
            String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
            String normalizedCity = city == null ? null : city.trim().toLowerCase(Locale.ROOT);
            List<OrgProfileDTO> result = new ArrayList<>();
            Map<String, OrgProfileDTO> activeProfiles = new LinkedHashMap<>();
            for (ExtOrgInfo org : orgs) {
                if (normalizedKeyword != null && !normalizedKeyword.isEmpty()
                        && !containsIgnoreCase(org.getOrgCode(), normalizedKeyword)
                        && !containsIgnoreCase(org.getOrgName(), normalizedKeyword)) {
                    continue;
                }
                PtOrgProfile profile = profiles.get(org.getOrgCode());
                if (normalizedCity != null && !normalizedCity.isEmpty()
                        && !containsIgnoreCase(profile == null ? null : profile.getCityCode(), normalizedCity)
                        && !containsIgnoreCase(profile == null ? null : profile.getCityName(), normalizedCity)) {
                    continue;
                }
                OrgProfileDTO dto = toProfileDto(profile, org);
                result.add(dto);
                if (Integer.valueOf(0).equals(org.getOrganState())
                        && ACTIVE.equalsIgnoreCase(dto.getStatus())) {
                    activeProfiles.put(dto.getOrgCode(), dto);
                }
            }
            supplementRuntimeLocations(activeProfiles);
            return result;
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.listProfiles] fail close keyword={}, city={}", keyword, city, ex);
            throw new BizException(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode(),
                    AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage(), ex);
        }
    }

    /**
     * 新增或更新单个机构画像。
     *
     * @param orgCode 外部机构编码
     * @param req     画像配置
     * @return 保存后的画像
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgProfileDTO saveProfile(String orgCode, OrgProfileUpdateReqDTO req) {
        String code = normalizeRequired(orgCode, "机构编码不能为空");
        requireReason(req == null ? null : req.getReason());
        validateProfile(code, req);
        ExtOrgInfo externalOrg = requireActiveExternalOrg(code);
        PtOrgProfile existing = orgProfileMapper.selectById(code);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        if (existing == null) {
            PtOrgProfile entity = toProfileEntity(code, req);
            entity.setVersion(0);
            entity.setCreatedBy(operator);
            entity.setCreatedTime(now);
            entity.setUpdatedBy(operator);
            entity.setUpdatedTime(now);
            orgProfileMapper.insert(entity);
            OrgProfileDTO result = toProfileDto(entity, externalOrg);
            orgGroupAuditService.profileChanged(null, entity, req.getReason());
            return result;
        }

        checkVersion(req.getVersion(), existing.getVersion(),
                AuthErrorCode.ORG_CONFIG_VERSION_REQUIRED, AuthErrorCode.ORG_PROFILE_VERSION_CONFLICT);
        PtOrgProfile entity = toProfileEntity(code, req);
        entity.setVersion(nextVersion(existing.getVersion()));
        entity.setUpdatedBy(operator);
        entity.setUpdatedTime(now);
        LambdaUpdateWrapper<PtOrgProfile> wrapper = new LambdaUpdateWrapper<PtOrgProfile>()
                // 使用显式 SET，允许管理员清空旧的归属、城市或坐标值；MP 默认 NOT_NULL
                // 策略会跳过 null，导致“清空坐标”被错误地保留。
                .set(PtOrgProfile::getOrgNature, entity.getOrgNature())
                .set(PtOrgProfile::getOperatingLevel, entity.getOperatingLevel())
                .set(PtOrgProfile::getOwnerOperatingOrgCode, entity.getOwnerOperatingOrgCode())
                .set(PtOrgProfile::getCityCode, entity.getCityCode())
                .set(PtOrgProfile::getCityName, entity.getCityName())
                .set(PtOrgProfile::getLng, entity.getLng())
                .set(PtOrgProfile::getLat, entity.getLat())
                .set(PtOrgProfile::getCoordSys, entity.getCoordSys())
                .set(PtOrgProfile::getStatus, entity.getStatus())
                .set(PtOrgProfile::getVersion, entity.getVersion())
                .set(PtOrgProfile::getUpdatedBy, entity.getUpdatedBy())
                .set(PtOrgProfile::getUpdatedTime, entity.getUpdatedTime())
                .set(PtOrgProfile::getRemark, entity.getRemark())
                .eq(PtOrgProfile::getOrgCode, code)
                .eq(PtOrgProfile::getVersion, existing.getVersion());
        int updated = orgProfileMapper.update(new PtOrgProfile(), wrapper);
        if (updated != 1) {
            throw new BizException(AuthErrorCode.ORG_PROFILE_VERSION_CONFLICT.getCode(),
                    AuthErrorCode.ORG_PROFILE_VERSION_CONFLICT.getMessage());
        }
        entity.setCreatedBy(existing.getCreatedBy());
        entity.setCreatedTime(existing.getCreatedTime());
        OrgProfileDTO result = toProfileDto(entity, externalOrg);
        orgGroupAuditService.profileChanged(existing, entity, req.getReason());
        return result;
    }

    /**
     * 包级校验入口，供 Red-Green 单元测试固定画像规则；业务写入口同样调用此方法。
     */
    void validateProfile(String orgCode, OrgProfileUpdateReqDTO req) {
        if (req == null) {
            throw invalidProfile("机构画像不能为空");
        }
        String nature = upper(req.getOrgNature());
        String operatingLevel = upper(req.getOperatingLevel());
        if (!ORG_NATURES.contains(nature)) {
            throw invalidProfile("机构性质不合法");
        }
        if (!OPERATING_LEVELS.contains(operatingLevel)) {
            throw invalidProfile("经营管理等级不合法");
        }
        String code = normalizeRequired(orgCode, "机构编码不能为空");
        ExtOrgInfo current = requireActiveExternalOrg(code);
        if (ACTIVE.equalsIgnoreCase(nullToDefault(req.getStatus(), ACTIVE))
                || DISABLED.equalsIgnoreCase(req.getStatus())) {
            // status 的值在下方统一校验；这里保留分支让校验顺序先关注机构有效性。
        } else {
            throw invalidProfile("画像状态只能是 ACTIVE 或 DISABLED");
        }

        BigDecimal lng = req.getLng();
        BigDecimal lat = req.getLat();
        if ((lng == null) != (lat == null)) {
            throw invalidProfile("经纬度必须同时填写");
        }
        if (lng != null) {
            if (lng.compareTo(BigDecimal.valueOf(-180)) < 0
                    || lng.compareTo(BigDecimal.valueOf(180)) > 0) {
                throw invalidProfile("经度超出 [-180,180] 范围");
            }
            if (lat.compareTo(BigDecimal.valueOf(-90)) < 0
                    || lat.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw invalidProfile("纬度超出 [-90,90] 范围");
            }
            if (!COORD_SYS_GCJ02.equalsIgnoreCase(req.getCoordSys())) {
                throw invalidProfile("经纬度存在时坐标系必须为 GCJ02");
            }
        } else if (req.getCoordSys() != null && !req.getCoordSys().isBlank()
                && !COORD_SYS_GCJ02.equalsIgnoreCase(req.getCoordSys())) {
            throw invalidProfile("坐标系只能使用 GCJ02");
        }

        if ("SUBORDINATE".equals(operatingLevel)) {
            String ownerCode = normalizeOptional(req.getOwnerOperatingOrgCode());
            if (ownerCode == null) {
                throw invalidProfile("下属机构必须填写归属一级经营机构");
            }
            if (ownerCode.equals(code)) {
                throw invalidProfile("归属一级经营机构不能是自身");
            }
            PtOrgProfile ownerProfile = orgProfileMapper.selectById(ownerCode);
            if (ownerProfile == null
                    || !ACTIVE.equalsIgnoreCase(ownerProfile.getStatus())
                    || !"PRIMARY".equalsIgnoreCase(ownerProfile.getOperatingLevel())) {
                throw invalidProfile("归属一级经营机构画像不存在、未启用或不是 PRIMARY");
            }
            ExtOrgInfo ownerExternal = orgMapper.selectByOrgCode(ownerCode);
            if (ownerExternal == null || !Integer.valueOf(0).equals(ownerExternal.getOrganState())) {
                throw invalidProfile("归属一级经营机构不是有效外部机构");
            }
            if (!isAncestor(ownerCode, current)) {
                throw invalidProfile("归属一级经营机构必须位于当前机构祖先链");
            }
        }
    }

    /** 查询全部命名机构组。 */
    public List<OrgGroupDTO> listGroups() {
        try {
            return safeList(orgGroupMapper.selectList(new LambdaQueryWrapper<PtOrgGroup>()))
                    .stream().map(this::toGroupDto).toList();
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.listGroups] fail close", ex);
            return List.of();
        }
    }

    /** 查询机构组详情；不存在返回 null。 */
    public OrgGroupDTO getGroup(String groupCode) {
        try {
            PtOrgGroup group = findGroup(groupCode);
            return group == null ? null : toGroupDto(group);
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.getGroup] fail close groupCode={}", groupCode, ex);
            return null;
        }
    }

    /** 创建命名机构组。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgGroupDTO createGroup(OrgGroupCreateReqDTO req) {
        if (req == null) {
            throw invalidGroup("机构组请求不能为空");
        }
        requireReason(req.getReason());
        if (req.getGroupName() == null || req.getGroupName().isBlank()) {
            throw invalidGroup("机构组名称不能为空");
        }
        String code = normalizeRequired(req.getGroupCode(), "机构组编码不能为空");
        validateGroupCode(code);
        if (findGroup(code) != null) {
            throw invalidGroup("机构组编码已存在");
        }
        validatePurpose(req.getGroupPurpose());
        String status = normalizeStatus(req.getStatus());
        PtOrgGroup group = new PtOrgGroup();
        group.setGroupCode(code);
        group.setGroupName(req.getGroupName().trim());
        group.setGroupPurpose(GROUP_PURPOSE_REPORT_SCREEN);
        group.setStatus(status);
        group.setVersion(0);
        group.setCreatedBy(currentOperator());
        group.setCreatedTime(LocalDateTime.now());
        group.setUpdatedBy(group.getCreatedBy());
        group.setUpdatedTime(group.getCreatedTime());
        group.setRemark(req.getRemark());
        orgGroupMapper.insert(group);
        OrgGroupDTO result = toGroupDto(group);
        orgGroupAuditService.groupCreated(group, req.getReason());
        return result;
    }

    /** 更新机构组基本信息；机构组编码不可修改。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgGroupDTO updateGroup(String groupCode, OrgGroupUpdateReqDTO req) {
        PtOrgGroup existing = requireGroup(groupCode);
        PtOrgGroup before = copyGroup(existing);
        if (req == null || req.getGroupName() == null || req.getGroupName().isBlank()) {
            throw invalidGroup("机构组名称不能为空");
        }
        requireReason(req.getReason());
        validateStatus(req.getStatus());
        checkVersion(req.getVersion(), existing.getVersion(),
                AuthErrorCode.ORG_CONFIG_VERSION_REQUIRED, AuthErrorCode.ORG_GROUP_VERSION_CONFLICT);
        int nextVersion = nextVersion(existing.getVersion());
        PtOrgGroup entity = new PtOrgGroup();
        entity.setGroupName(req.getGroupName().trim());
        entity.setStatus(upper(req.getStatus()));
        entity.setVersion(nextVersion);
        entity.setUpdatedBy(currentOperator());
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setRemark(req.getRemark());
        LambdaUpdateWrapper<PtOrgGroup> wrapper = new LambdaUpdateWrapper<PtOrgGroup>()
                .set(PtOrgGroup::getGroupName, entity.getGroupName())
                .set(PtOrgGroup::getStatus, entity.getStatus())
                .set(PtOrgGroup::getVersion, entity.getVersion())
                .set(PtOrgGroup::getUpdatedBy, entity.getUpdatedBy())
                .set(PtOrgGroup::getUpdatedTime, entity.getUpdatedTime())
                .set(PtOrgGroup::getRemark, entity.getRemark())
                .eq(PtOrgGroup::getGroupCode, existing.getGroupCode())
                .eq(PtOrgGroup::getVersion, existing.getVersion());
        int updated = orgGroupMapper.update(new PtOrgGroup(), wrapper);
        if (updated != 1) {
            throw versionConflict();
        }
        existing.setGroupName(entity.getGroupName());
        existing.setStatus(entity.getStatus());
        existing.setVersion(nextVersion);
        existing.setUpdatedBy(entity.getUpdatedBy());
        existing.setUpdatedTime(entity.getUpdatedTime());
        existing.setRemark(entity.getRemark());
        OrgGroupDTO result = toGroupDto(existing);
        orgGroupAuditService.groupChanged(before, existing, req.getReason());
        return result;
    }

    /** 覆盖保存机构组直接成员；不自动展开组织子树。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgGroupDTO replaceMembers(String groupCode, OrgGroupMembersReplaceReqDTO req) {
        if (req == null || req.getOrgCodes() == null) {
            throw invalidMember("机构组成员请求不能为空");
        }
        requireReason(req.getReason());
        PtOrgGroup group = requireGroup(groupCode);
        checkVersion(req.getVersion(), group.getVersion(),
                AuthErrorCode.ORG_CONFIG_VERSION_REQUIRED, AuthErrorCode.ORG_GROUP_VERSION_CONFLICT);
        LinkedHashSet<String> codes = normalizeSet(req.getOrgCodes());
        Map<String, ExtOrgInfo> externalOrgs = new LinkedHashMap<>();
        if (!codes.isEmpty()) {
            for (ExtOrgInfo org : safeList(orgMapper.selectByOrgCodes(codes))) {
                externalOrgs.put(org.getOrgCode(), org);
            }
        }
        for (String code : codes) {
            ExtOrgInfo org = externalOrgs.get(code);
            if (org == null || !Integer.valueOf(0).equals(org.getOrganState())) {
                throw invalidMember("机构不存在或已停用: " + code);
            }
        }
        Set<String> before = directActiveMemberCodes(group.getGroupCode());
        bumpGroupVersion(group);
        orgGroupMemberMapper.delete(new LambdaQueryWrapper<PtOrgGroupMember>()
                .eq(PtOrgGroupMember::getGroupCode, group.getGroupCode()));
        LocalDateTime now = LocalDateTime.now();
        for (String code : codes) {
            PtOrgGroupMember member = new PtOrgGroupMember();
            member.setGroupCode(group.getGroupCode());
            member.setOrgCode(code);
            member.setStatus(ACTIVE);
            member.setCreatedBy(currentOperator());
            member.setCreatedTime(now);
            member.setUpdatedBy(member.getCreatedBy());
            member.setUpdatedTime(now);
            orgGroupMemberMapper.insert(member);
        }
        OrgGroupDTO result = toGroupDto(group);
        orgGroupAuditService.membersReplaced(group.getGroupCode(), before, codes, req.getReason());
        return result;
    }

    /** 覆盖保存角色-机构组绑定；请求传 roleCode，落库转成 roleId。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgGroupDTO replaceRoles(String groupCode, OrgGroupRolesReplaceReqDTO req) {
        if (req == null || req.getRoleCodes() == null) {
            throw invalidRole("机构组角色请求不能为空");
        }
        requireReason(req.getReason());
        PtOrgGroup group = requireGroup(groupCode);
        checkVersion(req.getVersion(), group.getVersion(),
                AuthErrorCode.ORG_CONFIG_VERSION_REQUIRED, AuthErrorCode.ORG_GROUP_VERSION_CONFLICT);
        LinkedHashSet<String> roleCodes = normalizeSet(req.getRoleCodes());
        RoleIndex enabledRoles = enabledRoleIndex();
        for (String roleCode : roleCodes) {
            if (!enabledRoles.byRoleCode().containsKey(roleCode)) {
                throw invalidRole("角色不存在或已停用: " + roleCode);
            }
        }
        Set<String> before = directActiveRoleCodes(group.getGroupCode(), enabledRoles);
        bumpGroupVersion(group);
        roleOrgGroupMapper.delete(new LambdaQueryWrapper<PtRoleOrgGroup>()
                .eq(PtRoleOrgGroup::getGroupCode, group.getGroupCode()));
        LocalDateTime now = LocalDateTime.now();
        for (String roleCode : roleCodes) {
            PtRoleOrgGroup binding = new PtRoleOrgGroup();
            binding.setRoleId(enabledRoles.byRoleCode().get(roleCode).getRoleId());
            binding.setGroupCode(group.getGroupCode());
            binding.setStatus(ACTIVE);
            binding.setCreatedBy(currentOperator());
            binding.setCreatedTime(now);
            binding.setUpdatedBy(binding.getCreatedBy());
            binding.setUpdatedTime(now);
            roleOrgGroupMapper.insert(binding);
        }
        OrgGroupDTO result = toGroupDto(group);
        orgGroupAuditService.rolesReplaced(group.getGroupCode(), before, roleCodes, req.getReason());
        return result;
    }

    /** 查询有效的直接成员机构编码；组不存在、停用或空组均返回空集（Fail Close）。 */
    public Set<String> listActiveMemberCodes(String groupCode) {
        try {
            PtOrgGroup group = findGroup(groupCode);
            if (group == null || !ACTIVE.equalsIgnoreCase(group.getStatus())) {
                return Set.of();
            }
            return effectiveMemberCodes(group.getGroupCode());
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.listActiveMemberCodes] fail close groupCode={}", groupCode, ex);
            return Set.of();
        }
    }

    /**
     * 解析“当前有效角色 ∩ 屏级角色白名单 ∩ 机构组绑定角色”三方交集。
     * 任一配置缺失、机构失效或查询异常均拒绝并返回空成员集。
     */
    public OrgGroupScopeDTO resolveAuthorizedScope(String empId, String groupCode,
                                                    Collection<String> allowedRoleCodes) {
        OrgGroupScopeDTO denied = scope(groupCode, false, Set.of(), "AUTH-ORG-GROUP-FAIL_CLOSE", Set.of());
        try {
            if (empId == null || empId.isBlank() || groupCode == null || groupCode.isBlank()) {
                denied.setDeniedReasonCode("AUTH-ORG-GROUP-INVALID_INPUT");
                return denied;
            }
            PtOrgGroup group = findGroup(groupCode);
            if (group == null) {
                denied.setDeniedReasonCode(AuthErrorCode.ORG_GROUP_NOT_FOUND.getCode());
                return denied;
            }
            if (!ACTIVE.equalsIgnoreCase(group.getStatus())) {
                denied.setDeniedReasonCode("AUTH-ORG-GROUP-DISABLED");
                return denied;
            }
            LinkedHashSet<String> members = new LinkedHashSet<>(effectiveMemberCodes(group.getGroupCode()));
            if (members.isEmpty()) {
                denied.setDeniedReasonCode("AUTH-ORG-GROUP-EMPTY");
                return denied;
            }
            Set<String> allowed = normalizeSet(allowedRoleCodes);
            if (allowed.isEmpty()) {
                denied.setDeniedReasonCode("AUTH-ORG-GROUP-ROLE_WHITELIST_EMPTY");
                return denied;
            }
            Set<String> employeeRoles = safeList(userRoleMapper.selectRolesByUserId(empId)).stream()
                    .filter(role -> role != null && Integer.valueOf(0).equals(role.getRecordStatus()))
                    .map(PtRole::getRoleCode)
                    .filter(Objects::nonNull)
                    .map(OrgGroupService::upper)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (employeeRoles.isEmpty()) {
                denied.setDeniedReasonCode("AUTH-ORG-GROUP-EMPLOYEE_NO_ROLE");
                return denied;
            }
            RoleIndex enabledRoles = enabledRoleIndex();
            Set<String> boundRoles = safeList(roleOrgGroupMapper.selectList(
                            new LambdaQueryWrapper<PtRoleOrgGroup>()
                                    .eq(PtRoleOrgGroup::getGroupCode, group.getGroupCode())
                                    .eq(PtRoleOrgGroup::getStatus, ACTIVE)))
                    .stream()
                    .map(PtRoleOrgGroup::getRoleId)
                    .map(enabledRoles.byRoleId()::get)
                    .filter(Objects::nonNull)
                    .map(PtRole::getRoleCode)
                    .map(OrgGroupService::upper)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> matched = new LinkedHashSet<>(employeeRoles);
            matched.retainAll(allowed);
            matched.retainAll(boundRoles);
            if (matched.isEmpty()) {
                denied.setDeniedReasonCode(AuthErrorCode.ORG_GROUP_UNAUTHORIZED.getCode());
                return denied;
            }
            return scope(group.getGroupCode(), true, members, null, matched);
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.resolveAuthorizedScope] fail close empId={}, groupCode={}",
                    empId, groupCode, ex);
            return denied;
        }
    }

    /** 校验屏级角色白名单与目标机构组是否存在同一有效绑定角色。 */
    public OrgGroupRoleCheckDTO checkRoleBindings(String groupCode, Collection<String> roleCodes) {
        OrgGroupRoleCheckDTO result = new OrgGroupRoleCheckDTO();
        result.setGroupCode(groupCode);
        LinkedHashSet<String> requested = normalizeSet(roleCodes);
        try {
            PtOrgGroup group = findGroup(groupCode);
            if (group == null || !ACTIVE.equalsIgnoreCase(group.getStatus())) {
                result.setInvalidRoleCodes(requested);
                return result;
            }
            RoleIndex enabledRoles = enabledRoleIndex();
            LinkedHashSet<String> invalid = requested.stream()
                    .filter(code -> !enabledRoles.byRoleCode().containsKey(code))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> bound = safeList(roleOrgGroupMapper.selectList(
                            new LambdaQueryWrapper<PtRoleOrgGroup>()
                                    .eq(PtRoleOrgGroup::getGroupCode, groupCode)
                                    .eq(PtRoleOrgGroup::getStatus, ACTIVE)))
                    .stream()
                    .map(PtRoleOrgGroup::getRoleId)
                    .map(enabledRoles.byRoleId()::get)
                    .filter(Objects::nonNull)
                    .map(PtRole::getRoleCode)
                    .map(OrgGroupService::upper)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            LinkedHashSet<String> valid = requested.stream()
                    .filter(enabledRoles.byRoleCode()::containsKey)
                    .filter(bound::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            LinkedHashSet<String> unbound = requested.stream()
                    .filter(enabledRoles.byRoleCode()::containsKey)
                    .filter(code -> !bound.contains(code))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.setValidRoleCodes(valid);
            result.setUnboundRoleCodes(unbound);
            result.setInvalidRoleCodes(invalid);
            result.setSatisfiable(!valid.isEmpty() && !effectiveMemberCodes(groupCode).isEmpty());
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.checkRoleBindings] fail close groupCode={}", groupCode, ex);
            result.setValidRoleCodes(Set.of());
            result.setUnboundRoleCodes(Set.of());
            result.setInvalidRoleCodes(requested);
            result.setSatisfiable(false);
        }
        return result;
    }

    /** 批量返回有效外部机构且画像 ACTIVE 的机构画像。 */
    public Map<String, OrgProfileDTO> getActiveProfiles(Collection<String> orgCodes) {
        try {
            LinkedHashSet<String> codes = normalizeSet(orgCodes);
            if (codes.isEmpty()) {
                return Map.of();
            }
            Map<String, ExtOrgInfo> external = safeList(orgMapper.selectByOrgCodes(codes)).stream()
                    .filter(org -> Integer.valueOf(0).equals(org.getOrganState()))
                    .collect(Collectors.toMap(ExtOrgInfo::getOrgCode, Function.identity(), (left, right) -> left,
                            LinkedHashMap::new));
            if (external.isEmpty()) {
                return Map.of();
            }
            Map<String, OrgProfileDTO> result = safeList(orgProfileMapper.selectList(new LambdaQueryWrapper<PtOrgProfile>()
                            .in(PtOrgProfile::getOrgCode, external.keySet())
                            .eq(PtOrgProfile::getStatus, ACTIVE)))
                    .stream()
                    .collect(Collectors.toMap(PtOrgProfile::getOrgCode,
                            profile -> toProfileDto(profile, external.get(profile.getOrgCode())),
                            (left, right) -> left, LinkedHashMap::new));
            supplementRuntimeLocations(result);
            return result;
        } catch (RuntimeException ex) {
            log.warn("[OrgGroupService.getActiveProfiles] fail close orgCodes={}", orgCodes, ex);
            return Map.of();
        }
    }

    /**
     * 仅为当前调用方已经筛出的有效画像补充位置台账坐标；详细地址永不进入跨模块画像 DTO。
     * 以已核验地址台账的定位为优先；仅无有效台账定位时使用非演示画像坐标。
     */
    private void supplementRuntimeLocations(Map<String, OrgProfileDTO> profiles) {
        if (orgLocationService == null || profiles == null || profiles.isEmpty()) {
            return;
        }
        Map<String, PtOrgLocation> locations = Map.of();
        try {
            Map<String, PtOrgLocation> loaded = orgLocationService.findRuntimeLocations(
                    profiles.keySet(), profiles);
            if (loaded != null) {
                locations = loaded;
            }
        } catch (RuntimeException ex) {
            // 位置补充是可选增强，失败不能吞掉原本已经取得的合法画像。
            log.warn("[OrgGroupService] runtime location supplement skipped size={}", profiles.size(), ex);
        }
        for (Map.Entry<String, OrgProfileDTO> entry : profiles.entrySet()) {
            OrgProfileDTO profile = entry.getValue();
            if (profile == null) {
                continue;
            }
            PtOrgLocation location = locations.get(entry.getKey());
            if (location != null && validGcj02Coordinate(location.getLng(), location.getLat(),
                    location.getCoordSys())) {
                profile.setLng(location.getLng());
                profile.setLat(location.getLat());
                profile.setCoordSys(OrgLocationService.COORD_SYS_GCJ02);
                profile.setLocationSource(location.getLocationSource());
                continue;
            }
            boolean demoCoordinates = isScreenMapDemo(profile);
            if (demoCoordinates) {
                // 演示脚本中的坐标不是生产位置；只有已确认位置台账可以覆盖它。
                profile.setLng(null);
                profile.setLat(null);
                profile.setCoordSys(null);
                profile.setLocationSource(null);
            } else if (validGcj02Coordinate(profile.getLng(), profile.getLat(), profile.getCoordSys())) {
                if (profile.getLocationSource() == null) {
                    profile.setLocationSource(OrgLocationService.SOURCE_PROFILE);
                }
                continue;
            }
        }
    }

    private static boolean isScreenMapDemo(OrgProfileDTO profile) {
        return profile.getRemark() != null
                && profile.getRemark().toUpperCase(Locale.ROOT).contains("SCREEN_MAP_DEMO");
    }

    private static boolean validGcj02Coordinate(BigDecimal lng, BigDecimal lat, String coordSys) {
        return lng != null && lat != null
                && lng.compareTo(BigDecimal.valueOf(-180)) >= 0
                && lng.compareTo(BigDecimal.valueOf(180)) <= 0
                && lat.compareTo(BigDecimal.valueOf(-90)) >= 0
                && lat.compareTo(BigDecimal.valueOf(90)) <= 0
                && OrgLocationService.COORD_SYS_GCJ02.equalsIgnoreCase(coordSys);
    }

    // ----------------------- 校验与转换 -----------------------

    private PtOrgGroup findGroup(String groupCode) {
        String code = normalizeOptional(groupCode);
        if (code == null) {
            return null;
        }
        return orgGroupMapper.selectOne(new LambdaQueryWrapper<PtOrgGroup>()
                .eq(PtOrgGroup::getGroupCode, code));
    }

    private PtOrgGroup requireGroup(String groupCode) {
        PtOrgGroup group = findGroup(groupCode);
        if (group == null) {
            throw new BizException(AuthErrorCode.ORG_GROUP_NOT_FOUND.getCode(),
                    AuthErrorCode.ORG_GROUP_NOT_FOUND.getMessage());
        }
        if (group.getVersion() == null) {
            group.setVersion(0);
        }
        return group;
    }

    /** 返回组表中启用的直接成员，管理 DTO 使用该集合，便于管理员修复画像缺口。 */
    private Set<String> directActiveMemberCodes(String groupCode) {
        return safeList(orgGroupMemberMapper.selectList(new LambdaQueryWrapper<PtOrgGroupMember>()
                        .eq(PtOrgGroupMember::getGroupCode, groupCode)
                        .eq(PtOrgGroupMember::getStatus, ACTIVE)))
                .stream()
                .map(PtOrgGroupMember::getOrgCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 将持久化的角色 ID 解析为管理端使用的规范化角色编码。 */
    private Set<String> directActiveRoleCodes(String groupCode, RoleIndex roles) {
        return safeList(roleOrgGroupMapper.selectList(new LambdaQueryWrapper<PtRoleOrgGroup>()
                        .eq(PtRoleOrgGroup::getGroupCode, groupCode)
                        .eq(PtRoleOrgGroup::getStatus, ACTIVE)))
                .stream()
                .map(PtRoleOrgGroup::getRoleId)
                .map(roles.byRoleId()::get)
                .filter(Objects::nonNull)
                .map(PtRole::getRoleCode)
                .map(OrgGroupService::upper)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 运行时有效机构集合：直接成员 ∩ 外部机构有效 ∩ 本地画像 ACTIVE。
     * 任何一层查不到都不返回该机构，避免机构停用或画像缺失时误放行。
     */
    private Set<String> effectiveMemberCodes(String groupCode) {
        LinkedHashSet<String> direct = new LinkedHashSet<>(directActiveMemberCodes(groupCode));
        if (direct.isEmpty()) {
            return Set.of();
        }
        Set<String> activeExternal = safeList(orgMapper.selectByOrgCodes(direct)).stream()
                .filter(org -> Integer.valueOf(0).equals(org.getOrganState()))
                .map(ExtOrgInfo::getOrgCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (activeExternal.isEmpty()) {
            return Set.of();
        }
        Set<String> activeProfiles = safeList(orgProfileMapper.selectList(new LambdaQueryWrapper<PtOrgProfile>()
                        .in(PtOrgProfile::getOrgCode, activeExternal)
                        .eq(PtOrgProfile::getStatus, ACTIVE)))
                .stream()
                .map(PtOrgProfile::getOrgCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        direct.retainAll(activeExternal);
        direct.retainAll(activeProfiles);
        return direct;
    }

    /**
     * 构建有效角色的双向身份索引。
     *
     * <p>{@code PT_ROLE_ORG_GROUP} 只存 {@code ROLE_ID}，而管理端和跨模块契约
     * 使用规范化 {@code ROLE_CODE}。两个身份不能混作同一个 Map 的 key；否则当
     * ROLE_ID 为数字、ROLE_CODE 为业务编码时，已保存绑定会被错误地识别为不存在。</p>
     */
    private RoleIndex enabledRoleIndex() {
        Map<String, PtRole> byRoleCode = new LinkedHashMap<>();
        Map<String, PtRole> byRoleId = new LinkedHashMap<>();
        for (PtRole role : safeList(roleMapper.selectAllFiltered(0))) {
            if (role == null || role.getRoleCode() == null || role.getRoleId() == null) {
                continue;
            }
            String normalizedCode = upper(role.getRoleCode());
            String roleId = normalizeOptional(role.getRoleId());
            if (normalizedCode == null || roleId == null) {
                continue;
            }
            PtRole sameCode = byRoleCode.putIfAbsent(normalizedCode, role);
            PtRole sameId = byRoleId.putIfAbsent(roleId, role);
            if (sameCode != null || sameId != null) {
                // 角色身份不唯一时不能猜测绑定指向，所有调用方都应按各自 Fail Close 语义处理。
                throw new IllegalStateException("有效角色存在重复 ROLE_ID 或 ROLE_CODE");
            }
        }
        return new RoleIndex(byRoleId, byRoleCode);
    }

    private void bumpGroupVersion(PtOrgGroup group) {
        int current = group.getVersion() == null ? 0 : group.getVersion();
        String operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        int updated = orgGroupMapper.update(null, new LambdaUpdateWrapper<PtOrgGroup>()
                .set(PtOrgGroup::getVersion, current + 1)
                .set(PtOrgGroup::getUpdatedBy, operator)
                .set(PtOrgGroup::getUpdatedTime, now)
                .eq(PtOrgGroup::getGroupCode, group.getGroupCode())
                .eq(PtOrgGroup::getVersion, current));
        if (updated != 1) {
            throw versionConflict();
        }
        group.setVersion(current + 1);
        group.setUpdatedBy(operator);
        group.setUpdatedTime(now);
    }

    private boolean isAncestor(String ownerCode, ExtOrgInfo current) {
        String cursor = current.getPId();
        Set<String> visited = new LinkedHashSet<>();
        while (cursor != null && !cursor.isBlank() && visited.add(cursor)) {
            if (ownerCode.equals(cursor)) {
                return true;
            }
            ExtOrgInfo parent = orgMapper.selectByOrgCode(cursor);
            if (parent == null || !Integer.valueOf(0).equals(parent.getOrganState())) {
                return false;
            }
            cursor = parent.getPId();
        }
        return false;
    }

    private ExtOrgInfo requireActiveExternalOrg(String orgCode) {
        ExtOrgInfo org = orgMapper.selectByOrgCode(orgCode);
        if (org == null || !Integer.valueOf(0).equals(org.getOrganState())) {
            throw new BizException(AuthErrorCode.ORG_NOT_FOUND.getCode(),
                    "机构不存在或已停用: " + orgCode);
        }
        return org;
    }

    private PtOrgProfile toProfileEntity(String orgCode, OrgProfileUpdateReqDTO req) {
        PtOrgProfile entity = new PtOrgProfile();
        entity.setOrgCode(orgCode);
        entity.setOrgNature(upper(req.getOrgNature()));
        entity.setOperatingLevel(upper(req.getOperatingLevel()));
        entity.setOwnerOperatingOrgCode(normalizeOptional(req.getOwnerOperatingOrgCode()));
        entity.setCityCode(normalizeOptional(req.getCityCode()));
        entity.setCityName(normalizeOptional(req.getCityName()));
        entity.setLng(req.getLng());
        entity.setLat(req.getLat());
        entity.setCoordSys(req.getLng() == null ? null : COORD_SYS_GCJ02);
        entity.setStatus(normalizeStatus(req.getStatus()));
        entity.setRemark(req.getRemark());
        return entity;
    }

    private OrgProfileDTO toProfileDto(PtOrgProfile profile, ExtOrgInfo externalOrg) {
        OrgProfileDTO dto = new OrgProfileDTO();
        if (externalOrg != null) {
            dto.setOrgCode(externalOrg.getOrgCode());
            dto.setOrgName(externalOrg.getOrgName());
        }
        if (profile == null) {
            return dto;
        }
        dto.setOrgCode(profile.getOrgCode());
        dto.setOrgNature(profile.getOrgNature());
        dto.setOperatingLevel(profile.getOperatingLevel());
        dto.setOwnerOperatingOrgCode(profile.getOwnerOperatingOrgCode());
        dto.setCityCode(profile.getCityCode());
        dto.setCityName(profile.getCityName());
        dto.setLng(profile.getLng());
        dto.setLat(profile.getLat());
        dto.setCoordSys(profile.getCoordSys());
        dto.setStatus(profile.getStatus());
        dto.setVersion(profile.getVersion());
        dto.setCreatedBy(profile.getCreatedBy());
        dto.setCreatedTime(profile.getCreatedTime());
        dto.setUpdatedBy(profile.getUpdatedBy());
        dto.setUpdatedTime(profile.getUpdatedTime());
        dto.setRemark(profile.getRemark());
        return dto;
    }

    private OrgGroupDTO toGroupDto(PtOrgGroup group) {
        OrgGroupDTO dto = new OrgGroupDTO();
        dto.setId(group.getId());
        dto.setGroupCode(group.getGroupCode());
        dto.setGroupName(group.getGroupName());
        dto.setGroupPurpose(group.getGroupPurpose());
        dto.setStatus(group.getStatus());
        dto.setVersion(group.getVersion());
        dto.setCreatedBy(group.getCreatedBy());
        dto.setCreatedTime(group.getCreatedTime());
        dto.setUpdatedBy(group.getUpdatedBy());
        dto.setUpdatedTime(group.getUpdatedTime());
        dto.setRemark(group.getRemark());
        dto.setMemberOrgCodes(new ArrayList<>(directActiveMemberCodes(group.getGroupCode())));
        RoleIndex roles = enabledRoleIndex();
        dto.setRoleCodes(safeList(roleOrgGroupMapper.selectList(new LambdaQueryWrapper<PtRoleOrgGroup>()
                        .eq(PtRoleOrgGroup::getGroupCode, group.getGroupCode())
                        .eq(PtRoleOrgGroup::getStatus, ACTIVE)))
                .stream()
                .map(PtRoleOrgGroup::getRoleId)
                .map(roles.byRoleId()::get)
                .filter(Objects::nonNull)
                .map(PtRole::getRoleCode)
                .map(OrgGroupService::upper)
                .toList());
        return dto;
    }

    /** 复制机构组，确保审计 before 快照不会被后续实体原地更新覆盖。 */
    private PtOrgGroup copyGroup(PtOrgGroup source) {
        PtOrgGroup copy = new PtOrgGroup();
        copy.setId(source.getId());
        copy.setGroupCode(source.getGroupCode());
        copy.setGroupName(source.getGroupName());
        copy.setGroupPurpose(source.getGroupPurpose());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedTime(source.getCreatedTime());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedTime(source.getUpdatedTime());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private OrgGroupScopeDTO scope(String groupCode, boolean authorized, Set<String> members,
                                   String reason, Set<String> matchedRoleCodes) {
        OrgGroupScopeDTO dto = new OrgGroupScopeDTO();
        dto.setGroupCode(groupCode);
        dto.setAuthorized(authorized);
        dto.setMemberOrgCodes(members == null ? Set.of() : members);
        dto.setDeniedReasonCode(reason);
        dto.setMatchedRoleCodes(matchedRoleCodes == null ? Set.of() : matchedRoleCodes);
        return dto;
    }

    private void validateGroupCode(String code) {
        if (!code.matches("[A-Za-z0-9_\\-]{1,64}")) {
            throw invalidGroup("机构组编码只能包含字母、数字、下划线或连字符");
        }
    }

    private void validatePurpose(String purpose) {
        if (purpose == null || !GROUP_PURPOSE_REPORT_SCREEN.equalsIgnoreCase(purpose.trim())) {
            throw invalidGroup("机构组用途只能是 REPORT_SCREEN");
        }
    }

    private void validateStatus(String status) {
        if (!ACTIVE.equalsIgnoreCase(status) && !DISABLED.equalsIgnoreCase(status)) {
            throw invalidGroup("状态只能是 ACTIVE 或 DISABLED");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? ACTIVE : upper(status);
        validateStatus(normalized);
        return normalized;
    }

    private void checkVersion(Integer requested, Integer current,
                               AuthErrorCode missingVersionCode, AuthErrorCode conflictCode) {
        if (requested == null) {
            throw new BizException(missingVersionCode.getCode(), "版本不能为空");
        }
        if (!Objects.equals(requested, current == null ? 0 : current)) {
            throw new BizException(conflictCode.getCode(), conflictCode.getMessage());
        }
    }

    /** 服务层再次校验高危配置原因，避免绕过 Controller 直接调用时留下无原因变更。 */
    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BizException(AuthErrorCode.HIGH_RISK_ACTION_MISSING_REASON.getCode(),
                    "操作原因不能为空且长度不能超过500字");
        }
    }

    private BizException versionConflict() {
        return new BizException(AuthErrorCode.ORG_GROUP_VERSION_CONFLICT.getCode(),
                AuthErrorCode.ORG_GROUP_VERSION_CONFLICT.getMessage());
    }

    private String currentOperator() {
        DataScopeContext context = DataScopeContext.current();
        return context != null && context.getEmpId() != null && !context.getEmpId().isBlank()
                ? context.getEmpId() : "system";
    }

    private static int nextVersion(Integer version) {
        return (version == null ? 0 : version) + 1;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BizException(AuthErrorCode.ORG_GROUP_INVALID.getCode(), message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LinkedHashSet<String> normalizeSet(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                result.add(upper(normalized));
            }
        }
        return result;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : upper(value);
    }

    private static boolean containsIgnoreCase(String value, String expectedLowerCase) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expectedLowerCase);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /** 有效角色的双向身份索引：持久化按 ID，外部契约按规范化编码。 */
    private record RoleIndex(Map<String, PtRole> byRoleId, Map<String, PtRole> byRoleCode) {
    }

    private static BizException invalidProfile(String message) {
        return new BizException(AuthErrorCode.ORG_PROFILE_INVALID.getCode(), message);
    }

    private static BizException invalidGroup(String message) {
        return new BizException(AuthErrorCode.ORG_GROUP_INVALID.getCode(), message);
    }

    private static BizException invalidMember(String message) {
        return new BizException(AuthErrorCode.ORG_GROUP_MEMBER_INVALID.getCode(), message);
    }

    private static BizException invalidRole(String message) {
        return new BizException(AuthErrorCode.ORG_GROUP_ROLE_INVALID.getCode(), message);
    }
}
