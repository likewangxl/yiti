package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;
    @Mock ResourceMapper resourceMapper;
    @Mock RoleResourceMapper roleResourceMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock RoleBizScopeMapper roleBizScopeMapper;
    @InjectMocks PermissionCacheService cacheService;

    @Test
    void evictUserRolesCache_shouldDeleteKey() {
        cacheService.evictUserRolesCache("E001");
        verify(redisTemplate).delete("auth:user-roles:E001");
    }

    @Test
    void evictRoleResourceCache_shouldDeleteKey() {
        cacheService.evictRoleResourceCache("R_RM");
        verify(redisTemplate).delete("auth:role-resource:R_RM");
    }

    @Test
    void evictBizScopeCache_shouldDeleteKey() {
        cacheService.evictBizScopeCache("R_RM");
        verify(redisTemplate).delete("auth:biz-scope:R_RM");
    }

    @Test
    void evictAllResourceCache_shouldDeleteKey() {
        cacheService.evictAllResourceCache();
        verify(redisTemplate).delete("auth:resource:all");
    }

    @Test
    void evictOrgSubtreeCache_shouldDeleteKey() {
        cacheService.evictOrgSubtreeCache("ORG001");
        verify(redisTemplate).delete("auth:org-subtree:ORG001");
    }

    @Test
    void getRoleIdsByEmpId_shouldReturnFromCacheWhenHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user-roles:E001")).thenReturn(Set.of("R_RM"));

        Set<String> roleIds = cacheService.getRoleIdsByEmpId("E001");

        assertThat(roleIds).containsExactly("R_RM");
        verify(userRoleMapper, never()).selectRoleIdsByUserId(any());
    }

    @Test
    void getRoleIdsByEmpId_shouldLoadFromDbOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user-roles:E001")).thenReturn(null);
        when(userRoleMapper.selectRoleIdsByUserId("E001")).thenReturn(List.of("R_RM", "R_SYS"));

        Set<String> roleIds = cacheService.getRoleIdsByEmpId("E001");

        assertThat(roleIds).containsExactlyInAnyOrder("R_RM", "R_SYS");
        verify(valueOperations).set(eq("auth:user-roles:E001"), any(), any(Duration.class));
    }

    @Test
    void getResourceIdsByRoleId_shouldReturnFromCacheWhenHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:role-resource:R_RM")).thenReturn(Set.of("RES_001"));

        Set<String> ids = cacheService.getResourceIdsByRoleId("R_RM");

        assertThat(ids).containsExactly("RES_001");
        verify(roleResourceMapper, never()).selectResourceIdsByRoleId(anyString());
    }

    @Test
    void getAllResources_shouldReturnFromCacheWhenHit() {
        PtResource r = new PtResource();
        r.setResourceId("RES_001");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:resource:all")).thenReturn(List.of(r));

        List<PtResource> resources = cacheService.getAllResources();

        assertThat(resources).hasSize(1);
        verify(resourceMapper, never()).selectAll(any(), any());
    }
}
