package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserFacade#getEmpIdsByOrg 单元测试（审批人「机构角色」不选角色场景）。
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeEmpIdsByOrgTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    void getEmpIdsByOrg_returnsAllUsersInOrg() {
        when(userOrgMapper.selectEmpIdsByOrgCode("02901000")).thenReturn(List.of("E1", "E2", "E3"));
        assertThat(userFacade.getEmpIdsByOrg("02901000")).containsExactly("E1", "E2", "E3");
    }

    @Test
    void getEmpIdsByOrg_blankOrg_returnsEmptyNoDb() {
        assertThat(userFacade.getEmpIdsByOrg(null)).isEmpty();
        assertThat(userFacade.getEmpIdsByOrg("")).isEmpty();
        verify(userOrgMapper, never()).selectEmpIdsByOrgCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getEmpIdsByOrg_nullResult_returnsEmpty() {
        when(userOrgMapper.selectEmpIdsByOrgCode("X")).thenReturn(null);
        assertThat(userFacade.getEmpIdsByOrg("X")).isEmpty();
    }
}
