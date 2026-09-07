package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenAccessRole;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenAccessRoleMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 命名机构组四项门禁与同角色防拼接测试。 */
@ExtendWith(MockitoExtension.class)
class ScreenScopeAuthorizationServiceTest {

    @Mock private CurrentUserApi currentUserApi;
    @Mock private OrgGroupApi orgGroupApi;
    @Mock private RptScreenAccessRoleMapper roleMapper;
    @Mock private AuditApi auditApi;

    private ScreenScopeAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new ScreenScopeAuthorizationService(currentUserApi, orgGroupApi, roleMapper, auditApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        lenient().when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_SCREEN_CORP_VIEWER"));
        lenient().when(currentUserApi.isSystemAdmin()).thenReturn(false);
    }

    private RptScreen screen() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("ORG_GRP_CORP_DEPARTMENTS");
        return screen;
    }

    private void groupDefaults() {
        OrgGroupDTO group = new OrgGroupDTO();
        group.setGroupCode("ORG_GRP_CORP_DEPARTMENTS");
        group.setStatus("ACTIVE");
        when(orgGroupApi.getGroup("ORG_GRP_CORP_DEPARTMENTS")).thenReturn(group);
        when(orgGroupApi.listActiveMemberCodes("ORG_GRP_CORP_DEPARTMENTS")).thenReturn(Set.of("D1", "D2"));
    }

    @Test
    void authorize_roleAOnlyScreenAndRoleBOnlyGroup_rejectsPermissionSplicing() {
        groupDefaults();
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode("R_SCREEN_CORP_VIEWER");
        role.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        OrgGroupScopeDTO crossRole = new OrgGroupScopeDTO();
        crossRole.setAuthorized(true);
        crossRole.setMemberOrgCodes(Set.of("D1"));
        crossRole.setMatchedRoleCodes(Set.of("R_OTHER_GROUP_ROLE"));
        when(orgGroupApi.resolveAuthorizedScope("E001", "ORG_GRP_CORP_DEPARTMENTS",
                Set.of("R_SCREEN_CORP_VIEWER"))).thenReturn(crossRole);

        assertThatThrownBy(() -> service.authorize(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
    }

    @Test
    void authorize_sameRoleIntersection_returnsOnlyServerResolvedMembers() {
        groupDefaults();
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode("R_SCREEN_CORP_VIEWER");
        role.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        OrgGroupScopeDTO allowed = new OrgGroupScopeDTO();
        allowed.setAuthorized(true);
        allowed.setMemberOrgCodes(Set.of("D1"));
        allowed.setMatchedRoleCodes(Set.of("R_SCREEN_CORP_VIEWER"));
        when(orgGroupApi.resolveAuthorizedScope("E001", "ORG_GRP_CORP_DEPARTMENTS",
                Set.of("R_SCREEN_CORP_VIEWER"))).thenReturn(allowed);

        assertThat(service.authorize(screen())).containsExactly("D1");
    }

    @Test
    void authorize_authorizedWithoutMatchedRole_failsClosed() {
        groupDefaults();
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode("R_SCREEN_CORP_VIEWER");
        role.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        OrgGroupScopeDTO forged = new OrgGroupScopeDTO();
        forged.setAuthorized(true);
        forged.setMemberOrgCodes(Set.of("D1"));
        // Auth 契约必须回传同一角色交集；缺失时 report 不能把 authorized=true 当作全量放行。
        when(orgGroupApi.resolveAuthorizedScope("E001", "ORG_GRP_CORP_DEPARTMENTS",
                Set.of("R_SCREEN_CORP_VIEWER"))).thenReturn(forged);

        assertThatThrownBy(() -> service.authorize(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
    }

    @Test
    void authorize_authResultOutsideConfiguredGroup_isClampedAndRejected() {
        groupDefaults();
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode("R_SCREEN_CORP_VIEWER");
        role.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        OrgGroupScopeDTO forged = new OrgGroupScopeDTO();
        forged.setAuthorized(true);
        forged.setMemberOrgCodes(Set.of("NOT_IN_GROUP"));
        forged.setMatchedRoleCodes(Set.of("R_SCREEN_CORP_VIEWER"));
        when(orgGroupApi.resolveAuthorizedScope("E001", "ORG_GRP_CORP_DEPARTMENTS",
                Set.of("R_SCREEN_CORP_VIEWER"))).thenReturn(forged);

        assertThatThrownBy(() -> service.authorize(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
    }

    @Test
    void validateForSave_emptyGroup_failsClosed() {
        OrgGroupDTO group = new OrgGroupDTO();
        group.setStatus("ACTIVE");
        when(orgGroupApi.getGroup("ORG_GRP_CORP_DEPARTMENTS")).thenReturn(group);
        when(orgGroupApi.listActiveMemberCodes("ORG_GRP_CORP_DEPARTMENTS")).thenReturn(Set.of());

        assertThatThrownBy(() -> service.validateForSave(screen(), Set.of("R_SCREEN_CORP_VIEWER"), false))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void authorize_roleLookupFailure_failsClosed() {
        when(roleMapper.selectList(any(Wrapper.class)))
                .thenThrow(new IllegalStateException("role store unavailable"));

        assertThatThrownBy(() -> service.authorize(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
    }

    /** 系统管理员仍必须经屏白名单、机构组及同一角色交集校验，不能把平台资源管理员语义扩大为数据范围。 */
    @Test
    void authorize_systemAdminCannotBypassSameRoleScreenAndGroupGate() {
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorize(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
        // report 不再读取 isSystemAdmin；资源管理员身份不能成为命名组数据范围的旁路。
        verify(currentUserApi, never()).isSystemAdmin();
        // 范围适配器不知道调用 URL；结构化运行拒绝审计由 /view 或 /data 的真实入口写入。
        verify(auditApi, never()).log(any());
    }

    /** requireRoles 只控制空白名单；提交了非空但无效/未绑定的角色同样必须在保存时拒绝。 */
    @Test
    void validateForSave_nonEmptyInvalidRoleFailsEvenWhenEmptyRolesAreAllowed() {
        groupDefaults();
        OrgGroupRoleCheckDTO invalid = new OrgGroupRoleCheckDTO();
        invalid.setInvalidRoleCodes(Set.of("R_NOT_FOUND"));
        invalid.setSatisfiable(false);
        when(orgGroupApi.checkRoleBindings("ORG_GRP_CORP_DEPARTMENTS", Set.of("R_NOT_FOUND")))
                .thenReturn(invalid);

        assertThatThrownBy(() -> service.validateForSave(screen(), Set.of("R_NOT_FOUND"), false))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED.getCode());
    }

    private void publishDefaults() {
        groupDefaults();
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode("R_SCREEN_CORP_VIEWER");
        role.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        OrgGroupRoleCheckDTO valid = new OrgGroupRoleCheckDTO();
        valid.setSatisfiable(true);
        valid.setValidRoleCodes(Set.of("R_SCREEN_CORP_VIEWER"));
        when(orgGroupApi.checkRoleBindings(any(), anyCollection())).thenReturn(valid);
    }

    private OrgProfileDTO profile(String code, String status) {
        OrgProfileDTO profile = new OrgProfileDTO();
        profile.setOrgCode(code);
        profile.setStatus(status);
        return profile;
    }

    @Test
    void validatePublishRoles_missingConfiguredMemberProfileFailsClosed() {
        publishDefaults();
        when(orgGroupApi.getActiveProfiles(anyCollection()))
                .thenReturn(Map.of("D1", profile("D1", "ACTIVE")));

        assertThatThrownBy(() -> service.validatePublishRoles(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void validatePublishRoles_inactiveConfiguredMemberProfileFailsClosed() {
        publishDefaults();
        when(orgGroupApi.getActiveProfiles(anyCollection())).thenReturn(Map.of(
                "D1", profile("D1", "ACTIVE"),
                "D2", profile("D2", "DISABLED")));

        assertThatThrownBy(() -> service.validatePublishRoles(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void validatePublishRoles_mismatchedConfiguredMemberProfileFailsClosed() {
        publishDefaults();
        when(orgGroupApi.getActiveProfiles(anyCollection())).thenReturn(Map.of(
                "D1", profile("D1", "ACTIVE"),
                "D2", profile("OTHER", "ACTIVE")));

        assertThatThrownBy(() -> service.validatePublishRoles(screen()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void activeProfiles_nullResponseFailsClosed() {
        when(orgGroupApi.getActiveProfiles(anyCollection())).thenReturn(null);

        assertThatThrownBy(() -> service.activeProfiles(screen(), Set.of("D1")))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }
}
