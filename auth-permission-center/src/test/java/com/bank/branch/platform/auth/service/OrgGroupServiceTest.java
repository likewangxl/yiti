package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.OrgGroupMembersReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRolesReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileUpdateReqDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtOrgGroup;
import com.bank.branch.platform.auth.entity.PtOrgGroupMember;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleOrgGroup;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.mapper.OrgGroupMemberMapper;
import com.bank.branch.platform.auth.mapper.OrgGroupMapper;
import com.bank.branch.platform.auth.mapper.OrgProfileMapper;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.RoleOrgGroupMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机构画像、命名机构组及角色-机构组交集授权的 Red 阶段测试。
 * 这些用例先固定安全边界，再由实现使其通过。
 */
@ExtendWith(MockitoExtension.class)
class OrgGroupServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadataForLambdaWrappers() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "org-group-test");
        TableInfoHelper.initTableInfo(assistant, PtOrgGroup.class);
        TableInfoHelper.initTableInfo(assistant, PtOrgGroupMember.class);
        TableInfoHelper.initTableInfo(assistant, PtOrgProfile.class);
        TableInfoHelper.initTableInfo(assistant, PtRoleOrgGroup.class);
    }

    @Mock
    private OrgMapper orgMapper;
    @Mock
    private OrgProfileMapper orgProfileMapper;
    @Mock
    private OrgGroupMapper orgGroupMapper;
    @Mock
    private OrgGroupMemberMapper orgGroupMemberMapper;
    @Mock
    private RoleOrgGroupMapper roleOrgGroupMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private OrgGroupAuditService orgGroupAuditService;

    @InjectMocks
    private OrgGroupService service;

    @Test
    void departmentAndSecondaryBranchMayBePrimary() {
        ExtOrgInfo department = org("D001", "HQ", 0);
        when(orgMapper.selectByOrgCode("D001")).thenReturn(department);
        when(orgMapper.selectByOrgCode("B001")).thenReturn(org("B001", "HQ", 0));

        OrgProfileUpdateReqDTO departmentReq = profileReq("DEPARTMENT", "PRIMARY");
        OrgProfileUpdateReqDTO secondaryReq = profileReq("SECONDARY_BRANCH", "PRIMARY");

        service.validateProfile("D001", departmentReq);
        service.validateProfile("B001", secondaryReq);
    }

    @Test
    void subordinateMustBelongToAnAncestorPrimaryOperatingOrg() {
        ExtOrgInfo outlet = org("O001", "B001", 0);
        when(orgMapper.selectByOrgCode("O001")).thenReturn(outlet);
        when(orgProfileMapper.selectById("B001")).thenReturn(null);

        OrgProfileUpdateReqDTO request = profileReq("OUTLET", "SUBORDINATE");
        request.setOwnerOperatingOrgCode("B001");

        assertThatThrownBy(() -> service.validateProfile("O001", request))
                .hasMessageContaining("一级经营机构画像");
    }

    @Test
    void subordinateOwnerMustAlsoBeAnActiveExternalOrganization() {
        ExtOrgInfo outlet = org("O001", "B001", 0);
        ExtOrgInfo disabledOwner = org("B001", "HQ", 1);
        when(orgMapper.selectByOrgCode("O001")).thenReturn(outlet);
        when(orgMapper.selectByOrgCode("B001")).thenReturn(disabledOwner);
        when(orgProfileMapper.selectById("B001")).thenReturn(profile("B001"));

        OrgProfileUpdateReqDTO request = profileReq("OUTLET", "SUBORDINATE");
        request.setOwnerOperatingOrgCode("B001");

        assertThatThrownBy(() -> service.validateProfile("O001", request))
                .hasMessageContaining("有效");
    }

    @Test
    void subordinateWithActivePrimaryAncestorIsAccepted() {
        ExtOrgInfo outlet = org("O001", "B001", 0);
        ExtOrgInfo owner = org("B001", "HQ", 0);
        when(orgMapper.selectByOrgCode("O001")).thenReturn(outlet);
        when(orgMapper.selectByOrgCode("B001")).thenReturn(owner);
        when(orgProfileMapper.selectById("B001")).thenReturn(profile("B001"));

        OrgProfileUpdateReqDTO request = profileReq("OUTLET", "SUBORDINATE");
        request.setOwnerOperatingOrgCode("B001");

        service.validateProfile("O001", request);
    }

    @Test
    void coordinateBoundaryAndPairMustBeValidated() {
        ExtOrgInfo org = org("X001", "HQ", 0);
        when(orgMapper.selectByOrgCode("X001")).thenReturn(org);

        OrgProfileUpdateReqDTO invalid = profileReq("LOCAL_BRANCH", "PRIMARY");
        invalid.setLng(new BigDecimal("181"));
        invalid.setLat(new BigDecimal("34.2"));
        invalid.setCoordSys("GCJ02");
        assertThatThrownBy(() -> service.validateProfile("X001", invalid))
                .hasMessageContaining("经度");

        OrgProfileUpdateReqDTO missingPair = profileReq("LOCAL_BRANCH", "PRIMARY");
        missingPair.setLng(new BigDecimal("108.9"));
        missingPair.setLat(null);
        assertThatThrownBy(() -> service.validateProfile("X001", missingPair))
                .hasMessageContaining("经纬度必须同时填写");
    }

    @Test
    void replacingMembersDeduplicatesButRejectsDisabledOrganization() {
        PtOrgGroup group = group("G_PRIMARY", 0, "ACTIVE");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);
        when(orgGroupMapper.update(any(), any())).thenReturn(1);
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(org("001", "HQ", 0)));
        when(orgGroupMemberMapper.selectList(any())).thenReturn(List.of(member("G_PRIMARY", "001")));

        OrgGroupMembersReplaceReqDTO request = new OrgGroupMembersReplaceReqDTO();
        request.setOrgCodes(List.of("001", "001"));
        request.setVersion(0);
        request.setReason("校准经营机构成员");

        assertThat(service.replaceMembers("G_PRIMARY", request).getMemberOrgCodes())
                .containsExactly("001");

        // 首次成功替换会推进被 mock 组对象的版本；第二次调用重新返回数据库中的旧版本，
        // 才能继续验证停用机构校验，而不是被乐观锁分支提前拦截。
        when(orgGroupMapper.selectOne(any())).thenReturn(group("G_PRIMARY", 0, "ACTIVE"));
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(org("001", "HQ", 1)));
        assertThatThrownBy(() -> service.replaceMembers("G_PRIMARY", request))
                .hasMessageContaining("机构不存在或已停用");
    }

    @Test
    void replacingMembersRequiresVersionAndReason() {
        PtOrgGroup group = group("G_PRIMARY", 0, "ACTIVE");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);

        OrgGroupMembersReplaceReqDTO missingVersion = new OrgGroupMembersReplaceReqDTO();
        missingVersion.setOrgCodes(List.of("001"));
        missingVersion.setReason("校准经营机构成员");
        assertThatThrownBy(() -> service.replaceMembers("G_PRIMARY", missingVersion))
                .hasMessageContaining("版本不能为空");

        OrgGroupMembersReplaceReqDTO missingReason = new OrgGroupMembersReplaceReqDTO();
        missingReason.setOrgCodes(List.of("001"));
        missingReason.setVersion(0);
        assertThatThrownBy(() -> service.replaceMembers("G_PRIMARY", missingReason))
                .hasMessageContaining("原因");

        verify(orgGroupMemberMapper, never()).delete(any());
    }

    @Test
    void profileUpdateRequiresCurrentVersion() {
        ExtOrgInfo external = org("X001", "HQ", 0);
        PtOrgProfile existing = profile("X001");
        existing.setVersion(3);
        when(orgMapper.selectByOrgCode("X001")).thenReturn(external);
        when(orgProfileMapper.selectById("X001")).thenReturn(existing);

        OrgProfileUpdateReqDTO request = profileReq("LOCAL_BRANCH", "PRIMARY");
        request.setVersion(2);
        request.setReason("修订机构画像");

        assertThatThrownBy(() -> service.saveProfile("X001", request))
                .hasMessageContaining("其他管理员修改");
        verify(orgProfileMapper, never()).update(any(), any());
    }

    @Test
    void roleBindingCheckFailsClosedWhenLookupFails() {
        when(orgGroupMapper.selectOne(any())).thenThrow(new IllegalStateException("db unavailable"));

        OrgGroupRoleCheckDTO result = service.checkRoleBindings("G_PRIMARY", Set.of("CORP_VIEWER"));

        assertThat(result.isSatisfiable()).isFalse();
        assertThat(result.getValidRoleCodes()).isEmpty();
        assertThat(result.getUnboundRoleCodes()).isEmpty();
    }

    @Test
    void runtimeScopeFailsClosedWhenGroupLookupFails() {
        when(orgGroupMapper.selectOne(any())).thenThrow(new IllegalStateException("db unavailable"));

        OrgGroupScopeDTO result = service.resolveAuthorizedScope(
                "E001", "G_PRIMARY", Set.of("CORP_VIEWER"));

        assertThat(result.isAuthorized()).isFalse();
        assertThat(result.getMemberOrgCodes()).isEmpty();
        assertThat(result.getMatchedRoleCodes()).isEmpty();
    }

    @Test
    void runtimeScopeFailsClosedForDisabledOrEmptyGroup() {
        PtOrgGroup disabled = group("G_DISABLED", 0, "DISABLED");
        PtOrgGroup empty = group("G_EMPTY", 0, "ACTIVE");
        when(orgGroupMapper.selectOne(any())).thenReturn(disabled, empty);
        when(orgGroupMemberMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.resolveAuthorizedScope("E001", "G_DISABLED", Set.of("CORP_VIEWER")))
                .extracting(OrgGroupScopeDTO::isAuthorized)
                .isEqualTo(false);
        assertThat(service.resolveAuthorizedScope("E001", "G_EMPTY", Set.of("CORP_VIEWER")))
                .extracting(OrgGroupScopeDTO::isAuthorized)
                .isEqualTo(false);
    }

    @Test
    void readApisFailClosedWhenProfileOrGroupLookupFails() {
        when(orgGroupMapper.selectOne(any())).thenThrow(new IllegalStateException("db unavailable"));
        assertThat(service.getGroup("G_PRIMARY")).isNull();

        when(orgMapper.selectByOrgCodes(any())).thenThrow(new IllegalStateException("db unavailable"));
        Map<String, com.bank.branch.platform.auth.api.dto.OrgProfileDTO> profiles =
                service.getActiveProfiles(Set.of("001"));
        assertThat(profiles).isEmpty();
    }

    @Test
    void adminListApisFailClosedWhenLookupFails() {
        when(orgMapper.selectAll()).thenThrow(new IllegalStateException("db unavailable"));
        assertThat(service.listProfiles(null)).isEmpty();

        when(orgGroupMapper.selectList(any())).thenThrow(new IllegalStateException("db unavailable"));
        assertThat(service.listGroups()).isEmpty();
    }

    @Test
    void profileListFiltersCityIndependentlyFromCodeOrNameKeyword() {
        ExtOrgInfo xian = org("ORG_XIAN", "HQ", 0);
        xian.setOrgName("西安本地机构");
        ExtOrgInfo baoji = org("ORG_BAOJI", "HQ", 0);
        baoji.setOrgName("宝鸡本地机构");
        PtOrgProfile xianProfile = profile("ORG_XIAN");
        xianProfile.setCityCode("610100");
        xianProfile.setCityName("西安市");
        PtOrgProfile baojiProfile = profile("ORG_BAOJI");
        baojiProfile.setCityCode("610300");
        baojiProfile.setCityName("宝鸡市");
        when(orgMapper.selectAll()).thenReturn(List.of(xian, baoji));
        when(orgProfileMapper.selectList(any())).thenReturn(List.of(xianProfile, baojiProfile));

        assertThat(service.listProfiles(null, "610300"))
                .extracting(OrgProfileDTO::getOrgCode)
                .containsExactly("ORG_BAOJI");
        assertThat(service.listProfiles("ORG_XIAN", "宝鸡")).isEmpty();
    }

    @Test
    void profileSaveNeverWritesExternalOrgInfo() {
        when(orgMapper.selectByOrgCode("X001")).thenReturn(org("X001", "HQ", 0));
        OrgProfileUpdateReqDTO request = profileReq("LOCAL_BRANCH", "PRIMARY");
        request.setReason("补充机构本地经营画像");

        service.saveProfile("X001", request);

        verify(orgMapper, never()).insert(any(ExtOrgInfo.class));
        verify(orgMapper, never()).update(any(ExtOrgInfo.class), any());
    }

    @Test
    void authorizationRequiresSameRoleInEmployeeAllowedAndGroupBindings() {
        PtOrgGroup group = group("G_CORP", 0, "ACTIVE");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);
        when(orgGroupMemberMapper.selectList(any())).thenReturn(List.of(member("G_CORP", "D001")));
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(org("D001", "HQ", 0)));
        when(orgProfileMapper.selectList(any())).thenReturn(List.of(profile("D001")));
        when(userRoleMapper.selectRolesByUserId("E001"))
                .thenReturn(List.of(role("R1", "CORP_VIEWER"), role("R2", "RETAIL_VIEWER")));
        when(roleMapper.selectAllFiltered(0))
                .thenReturn(List.of(role("R1", "CORP_VIEWER"), role("R2", "RETAIL_VIEWER")));
        when(roleOrgGroupMapper.selectList(any()))
                .thenReturn(List.of(roleGroup("R2", "G_CORP")));

        OrgGroupScopeDTO denied = service.resolveAuthorizedScope(
                "E001", "G_CORP", Set.of("CORP_VIEWER"));

        assertThat(denied.isAuthorized()).isFalse();
        assertThat(denied.getMemberOrgCodes()).isEmpty();
    }

    @Test
    void roleBindingUsesRoleIdButReturnsNormalizedRoleCodeAfterSave() {
        PtOrgGroup group = group("G_CORP", 0, "ACTIVE");
        PtRole corpViewer = role("847", "corp_viewer");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);
        when(orgGroupMapper.update(any(), any())).thenReturn(1);
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(corpViewer));
        when(roleOrgGroupMapper.selectList(any())).thenReturn(List.of(roleGroup("847", "G_CORP")));

        OrgGroupRolesReplaceReqDTO request = new OrgGroupRolesReplaceReqDTO();
        request.setRoleCodes(List.of(" corp_viewer "));
        request.setVersion(0);
        request.setReason("绑定对公大屏查看角色");

        assertThat(service.replaceRoles("G_CORP", request).getRoleCodes())
                .containsExactly("CORP_VIEWER");

        org.mockito.ArgumentCaptor<PtRoleOrgGroup> bindingCaptor =
                org.mockito.ArgumentCaptor.forClass(PtRoleOrgGroup.class);
        verify(roleOrgGroupMapper).insert(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getRoleId()).isEqualTo("847");
    }

    @Test
    void groupDetailsReadbackResolvesRoleBindingByRoleIdInsteadOfRoleCode() {
        PtRole corpViewer = role("847", "corp_viewer");
        when(orgGroupMapper.selectOne(any())).thenReturn(group("G_CORP", 0, "ACTIVE"));
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(corpViewer));
        when(roleOrgGroupMapper.selectList(any())).thenReturn(List.of(roleGroup("847", "G_CORP")));

        assertThat(service.getGroup("G_CORP").getRoleCodes())
                .containsExactly("CORP_VIEWER");
    }

    @Test
    void roleBindingCheckResolvesRoleIdAgainstNormalizedRoleCode() {
        PtRole corpViewer = role("847", "corp_viewer");
        prepareActiveGroupWithEffectiveMember("G_CORP", "D001");
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(corpViewer));
        when(roleOrgGroupMapper.selectList(any())).thenReturn(List.of(roleGroup("847", "G_CORP")));

        OrgGroupRoleCheckDTO check = service.checkRoleBindings("G_CORP", Set.of("corp_viewer"));

        assertThat(check.getValidRoleCodes()).containsExactly("CORP_VIEWER");
        assertThat(check.getUnboundRoleCodes()).isEmpty();
        assertThat(check.getInvalidRoleCodes()).isEmpty();
        assertThat(check.isSatisfiable()).isTrue();
    }

    @Test
    void runtimeScopeAuthorizesOnlyWhenTheSameNormalizedRoleIsBoundByRoleId() {
        PtRole corpViewer = role("847", "corp_viewer");
        prepareActiveGroupWithEffectiveMember("G_CORP", "D001");
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of(corpViewer));
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(corpViewer));
        when(roleOrgGroupMapper.selectList(any())).thenReturn(List.of(roleGroup("847", "G_CORP")));

        OrgGroupScopeDTO scope = service.resolveAuthorizedScope(
                "E001", "G_CORP", Set.of("corp_viewer"));

        assertThat(scope.isAuthorized()).isTrue();
        assertThat(scope.getMemberOrgCodes()).containsExactly("D001");
        assertThat(scope.getMatchedRoleCodes()).containsExactly("CORP_VIEWER");
    }

    @Test
    void replacingMembersWritesBeforeAfterAndDeltaThroughStructuredAuditService() {
        PtOrgGroup group = group("G_PRIMARY", 0, "ACTIVE");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);
        when(orgGroupMapper.update(any(), any())).thenReturn(1);
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(org("NEW", "HQ", 0)));
        when(orgGroupMemberMapper.selectList(any())).thenReturn(List.of(member("G_PRIMARY", "OLD")));

        OrgGroupMembersReplaceReqDTO request = new OrgGroupMembersReplaceReqDTO();
        request.setOrgCodes(List.of("NEW"));
        request.setVersion(0);
        request.setReason("剔除失效成员并纳入新经营机构");

        service.replaceMembers("G_PRIMARY", request);

        verify(orgGroupAuditService).membersReplaced(
                "G_PRIMARY", Set.of("OLD"), Set.of("NEW"), request.getReason());
    }

    @Test
    void replacingRolesWritesBeforeAfterAndDeltaThroughStructuredAuditService() {
        PtOrgGroup group = group("G_CORP", 0, "ACTIVE");
        PtRole previous = role("101", "legacy_viewer");
        PtRole next = role("847", "corp_viewer");
        when(orgGroupMapper.selectOne(any())).thenReturn(group);
        when(orgGroupMapper.update(any(), any())).thenReturn(1);
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(previous, next));
        when(roleOrgGroupMapper.selectList(any())).thenReturn(List.of(roleGroup("101", "G_CORP")));

        OrgGroupRolesReplaceReqDTO request = new OrgGroupRolesReplaceReqDTO();
        request.setRoleCodes(List.of("corp_viewer"));
        request.setVersion(0);
        request.setReason("调整大屏查看角色");

        service.replaceRoles("G_CORP", request);

        verify(orgGroupAuditService).rolesReplaced(
                "G_CORP", Set.of("LEGACY_VIEWER"), Set.of("CORP_VIEWER"), request.getReason());
    }

    private void prepareActiveGroupWithEffectiveMember(String groupCode, String orgCode) {
        when(orgGroupMapper.selectOne(any())).thenReturn(group(groupCode, 0, "ACTIVE"));
        when(orgGroupMemberMapper.selectList(any())).thenReturn(List.of(member(groupCode, orgCode)));
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(org(orgCode, "HQ", 0)));
        when(orgProfileMapper.selectList(any())).thenReturn(List.of(profile(orgCode)));
    }

    private static OrgProfileUpdateReqDTO profileReq(String nature, String level) {
        OrgProfileUpdateReqDTO req = new OrgProfileUpdateReqDTO();
        req.setOrgNature(nature);
        req.setOperatingLevel(level);
        req.setStatus("ACTIVE");
        return req;
    }

    private static ExtOrgInfo org(String code, String parent, int state) {
        ExtOrgInfo org = new ExtOrgInfo();
        org.setOrgCode(code);
        org.setPId(parent);
        org.setOrganState(state);
        return org;
    }

    private static PtOrgGroup group(String code, int version, String status) {
        PtOrgGroup group = new PtOrgGroup();
        group.setGroupCode(code);
        group.setGroupPurpose("REPORT_SCREEN");
        group.setVersion(version);
        group.setStatus(status);
        return group;
    }

    private static PtRole role(String id, String code) {
        PtRole role = new PtRole();
        role.setRoleId(id);
        role.setRoleCode(code);
        role.setRecordStatus(0);
        return role;
    }

    private static PtOrgProfile profile(String orgCode) {
        PtOrgProfile profile = new PtOrgProfile();
        profile.setOrgCode(orgCode);
        profile.setStatus("ACTIVE");
        profile.setOrgNature("LOCAL_BRANCH");
        profile.setOperatingLevel("PRIMARY");
        return profile;
    }

    private static com.bank.branch.platform.auth.entity.PtOrgGroupMember member(
            String groupCode, String orgCode) {
        com.bank.branch.platform.auth.entity.PtOrgGroupMember member =
                new com.bank.branch.platform.auth.entity.PtOrgGroupMember();
        member.setGroupCode(groupCode);
        member.setOrgCode(orgCode);
        member.setStatus("ACTIVE");
        return member;
    }

    private static com.bank.branch.platform.auth.entity.PtRoleOrgGroup roleGroup(
            String roleId, String groupCode) {
        com.bank.branch.platform.auth.entity.PtRoleOrgGroup binding =
                new com.bank.branch.platform.auth.entity.PtRoleOrgGroup();
        binding.setRoleId(roleId);
        binding.setGroupCode(groupCode);
        binding.setStatus("ACTIVE");
        return binding;
    }
}
