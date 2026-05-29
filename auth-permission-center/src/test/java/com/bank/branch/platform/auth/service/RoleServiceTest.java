package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleMapper roleMapper;
    @Mock PermissionCacheService cacheService;
    @InjectMocks RoleService roleService;

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(roleMapper.selectByRoleId("NONE")).thenReturn(null);
        assertThatThrownBy(() -> roleService.getById("NONE"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40401"));
    }

    @Test
    void getById_shouldReturnRoleWhenFound() {
        PtRole role = makeRole("R_RM", "CUST_MANAGER", "客户经理");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(role);
        RoleRespDTO dto = roleService.getById("R_RM");
        assertThat(dto.getRoleId()).isEqualTo("R_RM");
        assertThat(dto.getRoleCode()).isEqualTo("CUST_MANAGER");
    }

    @Test
    void createRole_shouldThrowWhenCodeDuplicate() {
        when(roleMapper.selectByRoleCode("CUST_MANAGER")).thenReturn(makeRole("R1", "CUST_MANAGER", "客户经理"));
        assertThatThrownBy(() -> roleService.createRole("CUST_MANAGER", "客户经理2", null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40901"));
    }

    @Test
    void createRole_shouldInsertAndReturnDto() {
        when(roleMapper.selectByRoleCode("NEW_ROLE")).thenReturn(null);
        when(roleMapper.insert(any(PtRole.class))).thenReturn(1);
        RoleRespDTO dto = roleService.createRole("NEW_ROLE", "新角色", "备注");
        assertThat(dto.getRoleCode()).isEqualTo("NEW_ROLE");
        assertThat(dto.getRoleChName()).isEqualTo("新角色");
        verify(roleMapper).insert(any(PtRole.class));
    }

    @Test
    void updateRole_shouldThrowWhenNotFound() {
        when(roleMapper.selectByRoleId("NONE")).thenReturn(null);
        assertThatThrownBy(() -> roleService.updateRole("NONE", "新名字", null, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40401"));
    }

    @Test
    void updateRole_shouldUpdateNameAndRemark() {
        PtRole existing = makeRole("R_RM", "CUST_MANAGER", "客户经理");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(existing);
        when(roleMapper.updateById(any(PtRole.class))).thenReturn(1);
        RoleRespDTO dto = roleService.updateRole("R_RM", "客户经理V2", "新备注", null);
        assertThat(dto.getRoleChName()).isEqualTo("客户经理V2");
    }

    @Test
    void deleteRole_shouldSetRecordStatusToOne() {
        PtRole existing = makeRole("R_RM", "CUST_MANAGER", "客户经理");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(existing);
        when(roleMapper.updateById(any(PtRole.class))).thenReturn(1);
        roleService.deleteRole("R_RM", "测试删除");
        verify(roleMapper).updateById(argThat((PtRole r) -> r.getRecordStatus() == 1));
    }

    @Test
    void listByPage_shouldDelegateToMapper() {
        when(roleMapper.selectByPage(null, null, 0, 20)).thenReturn(List.of());
        when(roleMapper.countByPage(null, null)).thenReturn(0L);
        PageResult<RoleRespDTO> result = roleService.listByPage(null, null, 1, 20);
        assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    void listAll_withNullStatus_returnsAllRoles() {
        PtRole r1 = makeRole("R_01", "ADMIN", "管理员");
        PtRole r2 = makeRole("R_02", "VIEWER", "查看员");
        when(roleMapper.selectAllFiltered(null)).thenReturn(List.of(r1, r2));

        List<RoleRespDTO> result = roleService.listAll(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRoleCode()).isEqualTo("ADMIN");
        verify(roleMapper).selectAllFiltered(null);
    }

    @Test
    void listAll_withStatusFilter_returnsFiltered() {
        PtRole r1 = makeRole("R_01", "ADMIN", "管理员");
        r1.setRecordStatus(0);
        when(roleMapper.selectAllFiltered(0)).thenReturn(List.of(r1));

        List<RoleRespDTO> result = roleService.listAll(0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecordStatus()).isEqualTo(0);
        verify(roleMapper).selectAllFiltered(0);
    }

    private PtRole makeRole(String id, String code, String name) {
        PtRole r = new PtRole();
        r.setRoleId(id);
        r.setRoleCode(code);
        r.setRoleChName(name);
        r.setRecordStatus(0);
        r.setSysCode("PLATFORM");
        return r;
    }
}
