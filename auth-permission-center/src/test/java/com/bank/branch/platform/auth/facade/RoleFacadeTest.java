package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleFacadeTest {

    @Mock RoleService roleService;
    @InjectMocks RoleFacade roleFacade;

    @Test
    void listEnabledRoles_queriesEnabledAndSortsByName() {
        RoleRespDTO a = new RoleRespDTO(); a.setRoleCode("R_B"); a.setRoleChName("资财部负责人");
        RoleRespDTO b = new RoleRespDTO(); b.setRoleCode("R_A"); b.setRoleChName("分行行长");
        // 仅查可用角色（recordStatus=0）
        when(roleService.listAll(0)).thenReturn(List.of(a, b));

        List<RoleRespDTO> result = roleFacade.listEnabledRoles();

        verify(roleService).listAll(0);
        // 按中文名称升序：分行行长 < 资财部负责人
        assertThat(result).extracting(RoleRespDTO::getRoleCode).containsExactly("R_A", "R_B");
    }

    @Test
    void listEnabledRoles_nullSafe() {
        when(roleService.listAll(0)).thenReturn(null);
        assertThat(roleFacade.listEnabledRoles()).isEmpty();
    }
}
