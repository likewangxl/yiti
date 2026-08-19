package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.entity.PtRoleResource;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleResourceServiceTest {

    @Mock RoleResourceMapper roleResourceMapper;
    @Mock RoleMapper roleMapper;
    @Mock ResourceMapper resourceMapper;
    @Mock RoleBizScopeMapper roleBizScopeMapper;
    @Mock PermissionCacheService cacheService;
    @Mock BizScopeService bizScopeService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks RoleResourceService roleResourceService;

    @Test
    void bindResources_shouldThrowWhenRoleNotFound() {
        when(roleMapper.selectByRoleId("NONE")).thenReturn(null);

        assertThatThrownBy(() -> roleResourceService.bindResources("NONE", List.of("RES_01"), "原因"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40401"));
    }

    @Test
    void bindResources_shouldSkipAlreadyBoundResources() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.existsByRoleIdAndResourceId("R_RM", "RES_01")).thenReturn(true);

        roleResourceService.bindResources("R_RM", List.of("RES_01"), "原因");

        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
    }

    @Test
    void bindResources_shouldInsertNewBindings() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.existsByRoleIdAndResourceId("R_RM", "RES_01")).thenReturn(false);
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);

        roleResourceService.bindResources("R_RM", List.of("RES_01"), "原因");

        verify(roleResourceMapper).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void replaceResources_shouldDeleteThenInsert() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);

        roleResourceService.replaceResources("R_RM", List.of("RES_01", "RES_02"), "替换原因");

        verify(roleResourceMapper).deleteByRoleId("R_RM");
        verify(roleResourceMapper, times(2)).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
    }

    @Test
    void replaceResources_emptyList_shouldClearAllBindings() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));

        roleResourceService.replaceResources("R_RM", List.of(), "清空原因");

        verify(roleResourceMapper).deleteByRoleId("R_RM");
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
    }

    @Test
    void getResourceIdsByRoleId_shouldReadFromCache() {
        when(cacheService.getResourceIdsByRoleId("R_RM")).thenReturn(java.util.Set.of("RES_01"));

        java.util.Set<String> ids = roleResourceService.getResourceIdsByRoleId("R_RM");

        assertThat(ids).containsExactly("RES_01");
    }

    // ─── 菜单分配（参考 xanpd role.vue 模式：菜单与接口分开存取） ────────────

    @Test
    void replaceMenus_shouldThrowWhenRoleNotFound() {
        when(roleMapper.selectByRoleId("NONE")).thenReturn(null);

        assertThatThrownBy(() -> roleResourceService.replaceMenus("NONE", List.of("M_PERF_METRICS"), "原因"))
            .isInstanceOf(BizException.class);
    }

    @Test
    void replaceMenus_interfaceResourceId_shouldRejectBeforeDeletingExistingMenus() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(resourceMapper.selectById("P_RE_CKPT_EXEC"))
                .thenReturn(makeResource("P_RE_CKPT_EXEC", 0, 0));

        assertThatThrownBy(() -> roleResourceService.replaceMenus(
                "R_RM", List.of("P_RE_CKPT_EXEC"), "非法接口冒充菜单"))
                .isInstanceOf(BizException.class);

        verify(roleResourceMapper, never()).deleteMenuBindingsByRoleId(anyString());
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verifyNoInteractions(bizScopeService, cacheService, eventPublisher);
    }

    @Test
    void replaceMenus_missingResourceId_shouldRejectBeforeDeletingExistingMenus() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(resourceMapper.selectById("M_MISSING")).thenReturn(null);

        assertThatThrownBy(() -> roleResourceService.replaceMenus(
                "R_RM", List.of("M_MISSING"), "不存在菜单"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40402"));

        verify(roleResourceMapper, never()).deleteMenuBindingsByRoleId(anyString());
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verifyNoInteractions(bizScopeService, cacheService, eventPublisher);
    }

    @Test
    void replaceMenus_disabledResourceId_shouldRejectWholeRequestBeforeDeletingExistingMenus() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(resourceMapper.selectById("M_VALID"))
                .thenReturn(makeResource("M_VALID", 0, 1));
        when(resourceMapper.selectById("M_DISABLED"))
                .thenReturn(makeResource("M_DISABLED", 1, 1));

        assertThatThrownBy(() -> roleResourceService.replaceMenus(
                "R_RM", List.of("M_VALID", "M_DISABLED"), "混合菜单"))
                .isInstanceOf(BizException.class);

        verify(roleResourceMapper, never()).deleteMenuBindingsByRoleId(anyString());
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verifyNoInteractions(bizScopeService, cacheService, eventPublisher);
    }

    @Test
    void replaceMenus_shouldReplaceMenusWithoutTouchingExplicitInterfaceBindings() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(resourceMapper.selectById("M_PERF_METRICS"))
                .thenReturn(makeResource("M_PERF_METRICS", 0, 1));
        when(resourceMapper.selectById("M_PERF_IMPORT"))
                .thenReturn(makeResource("M_PERF_IMPORT", 0, 1));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);

        roleResourceService.replaceMenus("R_RM", List.of("M_PERF_METRICS", "M_PERF_IMPORT"), "替换菜单");

        // 菜单分配只维护 IS_MENU=1；既有显式 API 绑定必须保持原样
        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper, never()).deleteInterfaceBindingsByRoleId(anyString());
        verify(roleResourceMapper, never()).deleteByRoleId(anyString());
        verify(resourceMapper, never()).selectInterfaceIdsByMenuIds(anyList());
        verify(resourceMapper, never()).selectPublicInterfaceIds();
        verify(roleResourceMapper, times(2)).insert(any(PtRoleResource.class));
        // 缓存失效 + 事件发布
        verify(cacheService).evictRoleResourceCache("R_RM");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void replaceMenus_emptyList_shouldClearMenusWithoutTouchingExplicitInterfaceBindings() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));

        roleResourceService.replaceMenus("R_RM", List.of(), "清空菜单");

        // 清空菜单也不能删除角色已显式配置的 API 权限
        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper, never()).deleteInterfaceBindingsByRoleId(anyString());
        verify(resourceMapper, never()).selectInterfaceIdsByMenuIds(anyList());
        verify(resourceMapper, never()).selectPublicInterfaceIds();
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
    }

    @Test
    void replaceMenus_shouldNotOverrideExistingBizScope() {
        // 防御性回归：已配置的 BizScope 绝不能被自动写回 SELF（历史 bug：经营机构负责人 REPORT=ORG_SUBTREE 被覆盖）
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(resourceMapper.selectById("M_PERF_METRICS"))
                .thenReturn(makeResource("M_PERF_METRICS", 0, 1));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);
        // REPORT 已存在；其余 8 个 bizType 不存在
        PtRoleBizScope existing = new PtRoleBizScope();
        existing.setRoleId("R_RM"); existing.setBizType("REPORT"); existing.setDataScope("ORG_SUBTREE");
        when(roleBizScopeMapper.selectByRoleIdAndBizType("R_RM", "REPORT")).thenReturn(existing);
        // 其余 8 个返回 null（mockito 默认就是 null，可省略）

        roleResourceService.replaceMenus("R_RM", List.of("M_PERF_METRICS"), "分配菜单");

        // 已存在的 REPORT 绝不调 saveBizScope
        verify(bizScopeService, never()).saveBizScope(eq("R_RM"), eq("REPORT"), anyString(), anyString());
        // 新增的违规管理必须随菜单分配自动补齐 SELF 数据范围
        verify(bizScopeService).saveBizScope("R_RM", "VIOLATION", "SELF", "菜单分配自动配置");
        // 不存在的 8 个仍会自动配 SELF
        verify(bizScopeService, times(8)).saveBizScope(eq("R_RM"), anyString(), eq("SELF"), anyString());
    }

    @Test
    void getMenuIdsByRoleId_shouldReturnMenuIdsOnly() {
        when(roleResourceMapper.selectMenuIdsByRoleId("R_RM"))
            .thenReturn(List.of("M_PERF_METRICS", "M_REPORT_DYNAMIC"));

        List<String> ids = roleResourceService.getMenuIdsByRoleId("R_RM");

        assertThat(ids).containsExactly("M_PERF_METRICS", "M_REPORT_DYNAMIC");
    }

    // ── 资源维度分配角色 ──────────────────────────────────────────

    @Test
    void getRoleIdsByResource_shouldReturnMapperResult() {
        when(roleResourceMapper.selectRoleIdsByResourceId("M_HIST_PERF_ADJUST"))
            .thenReturn(List.of("1", "229"));

        List<String> ids = roleResourceService.getRoleIdsByResource("M_HIST_PERF_ADJUST");

        assertThat(ids).containsExactly("1", "229");
    }

    @Test
    void assignRolesToResource_shouldThrowWhenResourceNotFound() {
        when(resourceMapper.selectById("NOPE")).thenReturn(null);

        assertThatThrownBy(() -> roleResourceService.assignRolesToResource("NOPE", List.of("1"), "原因"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40402"));
        verify(roleResourceMapper, never()).deleteByResourceId(anyString());
    }

    @Test
    void assignRolesToResource_shouldDeleteThenInsert_andEvictAffectedRoles() {
        com.bank.branch.platform.auth.entity.PtResource res = new com.bank.branch.platform.auth.entity.PtResource();
        res.setResourceId("M_HIST_PERF_ADJUST");
        when(resourceMapper.selectById("M_HIST_PERF_ADJUST")).thenReturn(res);
        // 旧绑定 {1,5}，新绑定 {1,229} → 受影响应含被解绑的 5 与新增的 229
        when(roleResourceMapper.selectRoleIdsByResourceId("M_HIST_PERF_ADJUST"))
            .thenReturn(List.of("1", "5"));

        roleResourceService.assignRolesToResource("M_HIST_PERF_ADJUST", List.of("1", "229"), "分配角色");

        verify(roleResourceMapper).deleteByResourceId("M_HIST_PERF_ADJUST");
        verify(roleResourceMapper, times(2)).insert(any(PtRoleResource.class)); // 1, 229
        verify(cacheService).evictRoleResourceCache("1");
        verify(cacheService).evictRoleResourceCache("5");
        verify(cacheService).evictRoleResourceCache("229");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void assignRolesToResource_emptyRoleIds_clearsBindings() {
        com.bank.branch.platform.auth.entity.PtResource res = new com.bank.branch.platform.auth.entity.PtResource();
        res.setResourceId("M_X");
        when(resourceMapper.selectById("M_X")).thenReturn(res);
        when(roleResourceMapper.selectRoleIdsByResourceId("M_X")).thenReturn(List.of("7"));

        roleResourceService.assignRolesToResource("M_X", List.of(), "清空");

        verify(roleResourceMapper).deleteByResourceId("M_X");
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("7");
    }

    private PtRole makeRole(String roleId) {
        PtRole r = new PtRole();
        r.setRoleId(roleId);
        r.setRoleCode("CUST_MANAGER");
        return r;
    }

    private PtResource makeResource(String resourceId, int status, int isMenu) {
        PtResource resource = new PtResource();
        resource.setResourceId(resourceId);
        resource.setStatus(status);
        resource.setIsMenu(isMenu);
        return resource;
    }
}
