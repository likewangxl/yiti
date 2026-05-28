package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.entity.PtRoleResource;
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
    void replaceMenus_shouldRebindInterfacesPerMenu_includingPublicInterfaces() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);
        // mock 联动查询：M_PERF_METRICS 下 2 个接口 + 1 个公共接口
        when(resourceMapper.selectInterfaceIdsByMenuIds(List.of("M_PERF_METRICS", "M_PERF_IMPORT")))
            .thenReturn(List.of("A_METRIC_LIST", "A_METRIC_GET"));
        when(resourceMapper.selectPublicInterfaceIds())
            .thenReturn(List.of("A_AUTH_LOGIN"));

        roleResourceService.replaceMenus("R_RM", List.of("M_PERF_METRICS", "M_PERF_IMPORT"), "替换菜单");

        // 清菜单 + 清接口（不调用全量 deleteByRoleId）
        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper).deleteInterfaceBindingsByRoleId("R_RM");
        verify(roleResourceMapper, never()).deleteByRoleId(anyString());
        // 插入 2 菜单 + 2 菜单接口 + 1 公共接口 = 5 条
        verify(roleResourceMapper, times(5)).insert(any(PtRoleResource.class));
        // 缓存失效 + 事件发布
        verify(cacheService).evictRoleResourceCache("R_RM");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void replaceMenus_emptyList_shouldClearMenusAndInterfacesAndSkipPublicBinding() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));

        roleResourceService.replaceMenus("R_RM", List.of(), "清空菜单");

        // 菜单 + 接口绑定都清
        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper).deleteInterfaceBindingsByRoleId("R_RM");
        // 无菜单时不查接口、不插任何绑定
        verify(resourceMapper, never()).selectInterfaceIdsByMenuIds(anyList());
        verify(resourceMapper, never()).selectPublicInterfaceIds();
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
    }

    @Test
    void replaceMenus_shouldNotOverrideExistingBizScope() {
        // 防御性回归：已配置的 BizScope 绝不能被自动写回 SELF（历史 bug：经营机构负责人 REPORT=ORG_SUBTREE 被覆盖）
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);
        when(resourceMapper.selectInterfaceIdsByMenuIds(any())).thenReturn(List.of());
        when(resourceMapper.selectPublicInterfaceIds()).thenReturn(List.of());
        // REPORT 已存在；其余 7 个 bizType 不存在
        PtRoleBizScope existing = new PtRoleBizScope();
        existing.setRoleId("R_RM"); existing.setBizType("REPORT"); existing.setDataScope("ORG_SUBTREE");
        when(roleBizScopeMapper.selectByRoleIdAndBizType("R_RM", "REPORT")).thenReturn(existing);
        // 其余 7 个返回 null（mockito 默认就是 null，可省略）

        roleResourceService.replaceMenus("R_RM", List.of("M_PERF_METRICS"), "分配菜单");

        // 已存在的 REPORT 绝不调 saveBizScope
        verify(bizScopeService, never()).saveBizScope(eq("R_RM"), eq("REPORT"), anyString(), anyString());
        // 不存在的 7 个仍会自动配 SELF
        verify(bizScopeService, times(7)).saveBizScope(eq("R_RM"), anyString(), eq("SELF"), anyString());
    }

    @Test
    void getMenuIdsByRoleId_shouldReturnMenuIdsOnly() {
        when(roleResourceMapper.selectMenuIdsByRoleId("R_RM"))
            .thenReturn(List.of("M_PERF_METRICS", "M_REPORT_DYNAMIC"));

        List<String> ids = roleResourceService.getMenuIdsByRoleId("R_RM");

        assertThat(ids).containsExactly("M_PERF_METRICS", "M_REPORT_DYNAMIC");
    }

    private PtRole makeRole(String roleId) {
        PtRole r = new PtRole();
        r.setRoleId(roleId);
        r.setRoleCode("CUST_MANAGER");
        return r;
    }
}
