package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleResource;
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
    @Mock PermissionCacheService cacheService;
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
    void replaceMenus_shouldOnlyTouchMenuBindings_notInterfaces() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));
        when(roleResourceMapper.insert(any(PtRoleResource.class))).thenReturn(1);

        roleResourceService.replaceMenus("R_RM", List.of("M_PERF_METRICS", "M_PERF_IMPORT"), "替换菜单");

        // 只删该角色 IS_MENU=1 的绑定，不动 IS_MENU=0 的接口绑定
        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper, never()).deleteByRoleId(anyString());
        // 插入 2 条新菜单绑定
        verify(roleResourceMapper, times(2)).insert(any(PtRoleResource.class));
        // 缓存失效 + 事件发布
        verify(cacheService).evictRoleResourceCache("R_RM");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void replaceMenus_emptyList_shouldClearAllMenuBindings() {
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(makeRole("R_RM"));

        roleResourceService.replaceMenus("R_RM", List.of(), "清空菜单");

        verify(roleResourceMapper).deleteMenuBindingsByRoleId("R_RM");
        verify(roleResourceMapper, never()).insert(any(PtRoleResource.class));
        verify(cacheService).evictRoleResourceCache("R_RM");
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
