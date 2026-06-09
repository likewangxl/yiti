package com.bank.branch.platform.auth.security.resolver;

import com.bank.branch.platform.auth.service.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacAuthorizerTest {

    @Mock PermissionCacheService cacheService;
    @InjectMocks RbacAuthorizer rbacAuthorizer;

    @Test
    void authorize_roleHasResource_returnsTrue() {
        when(cacheService.getEffectiveRoleIds("E001")).thenReturn(Set.of("R_RM"));
        when(cacheService.getResourceIdsByRoleId("R_RM")).thenReturn(Set.of("RES_001", "RES_002"));

        assertThat(rbacAuthorizer.authorize("E001", "RES_001")).isTrue();
    }

    @Test
    void authorize_noRoleHasResource_returnsFalse() {
        when(cacheService.getEffectiveRoleIds("E001")).thenReturn(Set.of("R_RM"));
        when(cacheService.getResourceIdsByRoleId("R_RM")).thenReturn(Set.of("RES_003"));

        assertThat(rbacAuthorizer.authorize("E001", "RES_001")).isFalse();
    }

    @Test
    void authorize_multipleRolesOneMatches_returnsTrue() {
        // 使用有序集合确保 R_RM（无权限）先迭代，R_ADMIN（有权限）后迭代
        Set<String> orderedRoles = new java.util.LinkedHashSet<>(List.of("R_RM", "R_ADMIN"));
        when(cacheService.getEffectiveRoleIds("E001")).thenReturn(orderedRoles);
        when(cacheService.getResourceIdsByRoleId("R_RM")).thenReturn(Set.of("RES_003"));
        when(cacheService.getResourceIdsByRoleId("R_ADMIN")).thenReturn(Set.of("RES_001"));

        assertThat(rbacAuthorizer.authorize("E001", "RES_001")).isTrue();
    }

    @Test
    void authorize_noRoles_returnsFalse() {
        when(cacheService.getEffectiveRoleIds("E001")).thenReturn(Set.of());

        assertThat(rbacAuthorizer.authorize("E001", "RES_001")).isFalse();
    }
}
