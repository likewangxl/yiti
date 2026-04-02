package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUserRole;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
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
class UserRoleServiceTest {

    @Mock UserRoleMapper userRoleMapper;
    @Mock RoleMapper roleMapper;
    @Mock UserMapper userMapper;
    @Mock PermissionCacheService cacheService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks UserRoleService userRoleService;

    @Test
    void bindRoles_shouldThrowWhenUserNotFound() {
        when(userMapper.selectByUserId("NONE")).thenReturn(null);
        assertThatThrownBy(() -> userRoleService.bindRoles("NONE", List.of("R_RM"), "原因"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }

    @Test
    void bindRoles_shouldSkipExistingBindings() {
        com.bank.branch.platform.auth.entity.PtUser user = new com.bank.branch.platform.auth.entity.PtUser();
        user.setUserId("E001");
        when(userMapper.selectByUserId("E001")).thenReturn(user);

        PtRole role = new PtRole();
        role.setRoleId("R_RM");
        role.setRoleCode("CUST_MANAGER");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(role);

        // existing user-role records
        when(userRoleMapper.selectRoleIdsByUserId("E001")).thenReturn(List.of("R_RM"));

        userRoleService.bindRoles("E001", List.of("R_RM"), "原因");

        // should NOT insert since binding already exists
        verify(userRoleMapper, never()).insert(any());
    }

    @Test
    void bindRoles_shouldInsertNewBindingsAndEvictCache() {
        com.bank.branch.platform.auth.entity.PtUser user = new com.bank.branch.platform.auth.entity.PtUser();
        user.setUserId("E001");
        when(userMapper.selectByUserId("E001")).thenReturn(user);

        PtRole role = new PtRole();
        role.setRoleId("R_ADMIN");
        role.setRoleCode("SYS_ADMIN");
        when(roleMapper.selectByRoleId("R_ADMIN")).thenReturn(role);
        when(userRoleMapper.selectRoleIdsByUserId("E001")).thenReturn(List.of());
        when(userRoleMapper.insert(any())).thenReturn(1);

        userRoleService.bindRoles("E001", List.of("R_ADMIN"), "原因");

        verify(userRoleMapper).insert(any(PtUserRole.class));
        verify(cacheService).evictUserRolesCache("E001");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void unbindRole_shouldDeleteAndEvictCache() {
        userRoleService.unbindRole("E001", "R_RM", "解绑原因");
        verify(userRoleMapper).deleteByUserIdAndRoleId("E001", "R_RM");
        verify(cacheService).evictUserRolesCache("E001");
    }

    @Test
    void getRolesByUserId_shouldReturnSimpleDtos() {
        PtRole r = new PtRole();
        r.setRoleId("R_RM");
        r.setRoleCode("CUST_MANAGER");
        r.setRoleChName("客户经理");
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of(r));
        List<RoleSimpleDTO> roles = userRoleService.getRolesByUserId("E001");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRoleCode()).isEqualTo("CUST_MANAGER");
    }
}
