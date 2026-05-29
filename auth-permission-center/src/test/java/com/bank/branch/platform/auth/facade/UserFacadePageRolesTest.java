package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleItemDTO;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFacadePageRolesTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    @DisplayName("pageUsers 返回分页用户，empId/username/displayName 装配正确")
    void pageUsers_returnsMappedDtos() {
        PtUser u = new PtUser();
        u.setUserId("1001");
        u.setUsername("zhangsan");
        u.setUserchnname("张三");
        when(userMapper.selectByKeywordPaged("张", 0, 20)).thenReturn(List.of(u));
        when(userMapper.countByKeyword("张")).thenReturn(1L);

        PageResult<UserDTO> r = userFacade.pageUsers("张", 1, 20);

        assertThat(r.getTotal()).isEqualTo(1L);
        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getRecords().get(0).getEmpId()).isEqualTo("1001");
        assertThat(r.getRecords().get(0).getDisplayName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("pageUsers 入参越界时归一：pageNo<1→1，pageSize>100→100")
    void pageUsers_normalizesPaging() {
        when(userMapper.selectByKeywordPaged(null, 0, 100)).thenReturn(List.of());
        when(userMapper.countByKeyword(null)).thenReturn(0L);

        PageResult<UserDTO> r = userFacade.pageUsers(null, 0, 999);

        assertThat(r.getPageNo()).isEqualTo(1);
        assertThat(r.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getRolesByUserIds 按 userId 分组返回角色列表")
    void getRolesByUserIds_groupsByUserId() {
        UserRoleItemDTO a = new UserRoleItemDTO();
        a.setUserId("1001"); a.setRoleId("r1"); a.setRoleCode("R_ADMIN"); a.setRoleChName("管理员");
        UserRoleItemDTO b = new UserRoleItemDTO();
        b.setUserId("1001"); b.setRoleId("r2"); b.setRoleCode("R_RM"); b.setRoleChName("客户经理");
        when(userRoleMapper.selectRolesByUserIds(List.of("1001"))).thenReturn(List.of(a, b));

        Map<String, List<RoleSimpleDTO>> map = userFacade.getRolesByUserIds(List.of("1001"));

        assertThat(map.get("1001")).hasSize(2);
        assertThat(map.get("1001")).extracting(RoleSimpleDTO::getRoleChName)
                .containsExactlyInAnyOrder("管理员", "客户经理");
    }

    @Test
    @DisplayName("getRolesByUserIds 空入参返回空 Map")
    void getRolesByUserIds_emptyInput_returnsEmptyMap() {
        assertThat(userFacade.getRolesByUserIds(List.of())).isEmpty();
        assertThat(userFacade.getRolesByUserIds(null)).isEmpty();
    }
}
