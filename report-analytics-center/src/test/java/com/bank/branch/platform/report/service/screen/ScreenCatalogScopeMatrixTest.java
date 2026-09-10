package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenAccessRole;
import com.bank.branch.platform.report.mapper.RptScreenAccessRoleMapper;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 目录使用真实 ScreenScopeAuthorizationService 的多用户授权矩阵。
 *
 * <p>角色表、当前用户和机构组 API 是底层边界 mock；authorize 本身不做 thenReturn/thenThrow
 * 模拟，确保目录实际走屏白名单、同角色交集和机构组范围门禁。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScreenCatalogScopeMatrixTest {

    private static final String CORP_ROLE = "R_SCREEN_CORP_VIEWER";
    private static final String RETAIL_ROLE = "R_SCREEN_RETAIL_VIEWER";

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper datasourceMapper;
    @Mock private RptScreenMapPointMapper mapPointMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private OrgGroupApi orgGroupApi;
    @Mock private RptScreenAccessRoleMapper accessRoleMapper;
    @Mock private AuditApi auditApi;

    private ScreenConfigServiceImpl configService;
    private AtomicReference<String> empId;
    private AtomicReference<Set<String>> currentRoles;
    private AtomicInteger roleLookupIndex;

    @BeforeEach
    void setUp() {
        empId = new AtomicReference<>("E_CORP");
        currentRoles = new AtomicReference<>(Set.of(CORP_ROLE));
        roleLookupIndex = new AtomicInteger();
        lenient().when(currentUserApi.getCurrentEmpId()).thenAnswer(invocation -> empId.get());
        when(currentUserApi.getCurrentRoleCodes()).thenAnswer(invocation -> currentRoles.get());

        ScreenScopeAuthorizationService scopeService = new ScreenScopeAuthorizationService(
                currentUserApi, orgGroupApi, accessRoleMapper, auditApi);
        configService = new ScreenConfigServiceImpl(screenMapper, blockMapper, datasourceMapper,
                mapPointMapper, currentUserApi);
        ReflectionTestUtils.setField(configService, "scopeAuthorizationService", scopeService);
    }

    @Test
    void catalog_realScopeAuthorizationShowsDifferentMenusForRoleSubsetsAndRejectsSysAdminBypass() {
        RptScreen corp = namedGroupScreen(101L, "SCR_CORP", "公司经营屏", "GROUP_CORP");
        RptScreen retail = namedGroupScreen(102L, "SCR_RETAIL", "零售经营屏", "GROUP_RETAIL");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(corp, retail));
        when(accessRoleMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            // 目录按 screenCode 稳定排序，按每次目录调用重置的查找序号对应 CORP/RETAIL 屏。
            boolean corpLookup = roleLookupIndex.getAndIncrement() % 2 == 0;
            return List.of(role(corpLookup ? CORP_ROLE : RETAIL_ROLE));
        });
        group("GROUP_CORP", Set.of("C1", "C2"));
        group("GROUP_RETAIL", Set.of("R1", "R2"));
        when(orgGroupApi.resolveAuthorizedScope(anyString(), anyString(), anyCollection()))
                .thenAnswer(invocation -> resolvedScope(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        // E_CORP 命中同角色白名单和 GROUP_CORP 的有效范围，只看到 CORP 菜单。
        assertThat(catalogForCurrentUser())
                .extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("SCR_CORP");
        verify(orgGroupApi).resolveAuthorizedScope("E_CORP", "GROUP_CORP", Set.of(CORP_ROLE));

        // 同一目录数据下，E_RETAIL 的菜单子集切换到 RETAIL。
        empId.set("E_RETAIL");
        currentRoles.set(Set.of(RETAIL_ROLE));
        assertThat(catalogForCurrentUser())
                .extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("SCR_RETAIL");
        verify(orgGroupApi).resolveAuthorizedScope("E_RETAIL", "GROUP_RETAIL", Set.of(RETAIL_ROLE));

        // SYS_ADMIN 没有屏级白名单交集，不能绕过屏级角色或机构范围门禁。
        empId.set("E_ADMIN");
        currentRoles.set(Set.of("SYS_ADMIN"));
        assertThat(catalogForCurrentUser()).isEmpty();
        verify(currentUserApi, never()).isSystemAdmin();
    }

    @Test
    void catalog_realScopeAuthorizationRejectsBlankNamedGroupWhitelist() {
        RptScreen screen = namedGroupScreen(201L, "SCR_NAMED_EMPTY", "空白角色屏", "GROUP_EMPTY");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(accessRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        currentRoles.set(Set.of(CORP_ROLE));

        assertThat(configService.listAuthorizedPublishedScreens()).isEmpty();
        verify(orgGroupApi, never()).resolveAuthorizedScope(anyString(), anyString(), anyCollection());
    }

    @Test
    void catalog_realScopeAuthorizationKeepsLegacyContextCompatibleWithBlankWhitelist() {
        RptScreen screen = new RptScreen();
        screen.setId(301L);
        screen.setScreenCode("SCR_LEGACY_EMPTY");
        screen.setScreenName("旧上下文屏");
        screen.setViewLevel("BRANCH");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setStatus("ACTIVE");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson(validPackage());
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(accessRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        currentRoles.set(Set.of());

        assertThat(configService.listAuthorizedPublishedScreens())
                .extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("SCR_LEGACY_EMPTY");
    }

    private void group(String code, Set<String> members) {
        OrgGroupDTO group = new OrgGroupDTO();
        group.setGroupCode(code);
        group.setStatus("ACTIVE");
        group.setMemberOrgCodes(List.copyOf(members));
        when(orgGroupApi.getGroup(code)).thenReturn(group);
        when(orgGroupApi.listActiveMemberCodes(code)).thenReturn(members);
    }

    private List<ScreenEntryRespDTO> catalogForCurrentUser() {
        roleLookupIndex.set(0);
        return configService.listAuthorizedPublishedScreens();
    }

    private OrgGroupScopeDTO resolvedScope(String currentEmpId, String groupCode,
                                           Collection<String> allowedRoles) {
        OrgGroupScopeDTO scope = new OrgGroupScopeDTO();
        boolean corp = "E_CORP".equals(currentEmpId) && "GROUP_CORP".equals(groupCode)
                && allowedRoles.contains(CORP_ROLE);
        boolean retail = "E_RETAIL".equals(currentEmpId) && "GROUP_RETAIL".equals(groupCode)
                && allowedRoles.contains(RETAIL_ROLE);
        if (corp || retail) {
            scope.setAuthorized(true);
            scope.setMemberOrgCodes(corp ? Set.of("C1") : Set.of("R1"));
            scope.setMatchedRoleCodes(corp ? Set.of(CORP_ROLE) : Set.of(RETAIL_ROLE));
        }
        return scope;
    }

    private RptScreenAccessRole role(String roleCode) {
        RptScreenAccessRole role = new RptScreenAccessRole();
        role.setRoleCode(roleCode);
        role.setStatus("ACTIVE");
        return role;
    }

    private RptScreen namedGroupScreen(long id, String code, String name, String groupCode) {
        RptScreen screen = new RptScreen();
        screen.setId(id);
        screen.setScreenCode(code);
        screen.setScreenName(name);
        screen.setViewLevel("BRANCH");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode(groupCode);
        screen.setStatus("ACTIVE");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson(validPackage());
        return screen;
    }

    private String validPackage() {
        return "{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}";
    }
}
