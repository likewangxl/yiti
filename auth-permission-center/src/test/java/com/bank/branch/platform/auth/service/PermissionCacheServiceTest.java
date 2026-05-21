package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * PermissionCacheService 测试（去 Redis 后改 mock Mapper）。
 * <p>接口签名不变，业务层无感；getXxx 改为直接 mapper 调用；
 * evictXxxCache 改为 NoOp（不抛异常即合规，避免业务层大改）。</p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock ResourceMapper resourceMapper;
    @Mock RoleResourceMapper roleResourceMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock RoleBizScopeMapper roleBizScopeMapper;
    @InjectMocks PermissionCacheService cacheService;

    // ── 直接读 DB（去 Redis）──────────────────────────────────────

    @Test
    void getRoleIdsByEmpId_callsMapperDirectly() {
        when(userRoleMapper.selectRoleIdsByUserId("E001"))
                .thenReturn(List.of("R_RM", "R_SYS"));

        Set<String> roleIds = cacheService.getRoleIdsByEmpId("E001");

        assertThat(roleIds).containsExactlyInAnyOrder("R_RM", "R_SYS");
        verify(userRoleMapper).selectRoleIdsByUserId("E001");
    }

    @Test
    void getRoleIdsByEmpId_returnsEmptySetWhenDbEmpty() {
        when(userRoleMapper.selectRoleIdsByUserId("E999")).thenReturn(List.of());

        Set<String> roleIds = cacheService.getRoleIdsByEmpId("E999");

        assertThat(roleIds).isEmpty();
    }

    @Test
    void getResourceIdsByRoleId_callsMapperDirectly() {
        when(roleResourceMapper.selectResourceIdsByRoleId("R_RM"))
                .thenReturn(List.of("RES_001", "RES_002"));

        Set<String> ids = cacheService.getResourceIdsByRoleId("R_RM");

        assertThat(ids).containsExactlyInAnyOrder("RES_001", "RES_002");
        verify(roleResourceMapper).selectResourceIdsByRoleId("R_RM");
    }

    @Test
    void getAllResources_callsMapperDirectly_statusEnabled() {
        PtResource r = new PtResource();
        r.setResourceId("RES_001");
        when(resourceMapper.selectAll(0, null)).thenReturn(List.of(r));

        List<PtResource> resources = cacheService.getAllResources();

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).getResourceId()).isEqualTo("RES_001");
        verify(resourceMapper).selectAll(0, null);
    }

    @Test
    void getBizScopesByRoleId_callsMapperDirectly() {
        PtRoleBizScope scope = new PtRoleBizScope();
        scope.setRoleId("R_RM");
        when(roleBizScopeMapper.selectByRoleId("R_RM")).thenReturn(List.of(scope));

        List<PtRoleBizScope> scopes = cacheService.getBizScopesByRoleId("R_RM");

        assertThat(scopes).hasSize(1);
        verify(roleBizScopeMapper).selectByRoleId("R_RM");
    }

    // ── 缓存失效 NoOp：不抛异常即合规 ────────────────────────────

    @Test
    void evictUserRolesCache_isNoOp() {
        assertThatCode(() -> cacheService.evictUserRolesCache("E001")).doesNotThrowAnyException();
        verifyNoInteractions(userRoleMapper, roleResourceMapper, resourceMapper, roleBizScopeMapper);
    }

    @Test
    void evictRoleResourceCache_isNoOp() {
        assertThatCode(() -> cacheService.evictRoleResourceCache("R_RM")).doesNotThrowAnyException();
    }

    @Test
    void evictBizScopeCache_isNoOp() {
        assertThatCode(() -> cacheService.evictBizScopeCache("R_RM")).doesNotThrowAnyException();
    }

    @Test
    void evictAllResourceCache_isNoOp() {
        assertThatCode(() -> cacheService.evictAllResourceCache()).doesNotThrowAnyException();
    }

    @Test
    void evictOrgSubtreeCache_isNoOp() {
        assertThatCode(() -> cacheService.evictOrgSubtreeCache("ORG001")).doesNotThrowAnyException();
    }
}
