package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserFacade#getUserByEmpIds / #getUsersByUsernames 反 N+1 单元测试.
 *
 * <p>动态指标查询「员工维度」等场景会对这两个方法传入大批量工号/用户名，旧实现内部逐人
 * 回调 {@code getUserByEmpId}（每人 3 次查询：PT_USER + EXT_USER_ORG + EXT_ORG_INFO），
 * 100 人即 300 次查询。本测试锁定「必须走批量 IN 查询」的行为，防止回归到逐人查询。
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeBatchQueryNPlusOneTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    void getUserByEmpIds_returnsFieldsMatchingSingleUserMapping_singleBatchQuery_noPerUserNPlusOne() {
        // Mock 批量返回顺序与入参顺序相反，验证出参仍按入参 empIds 顺序输出
        when(userMapper.selectByUserIds(anyList())).thenReturn(List.of(
                ptUser("B", "userB", "乙用户", "1", 0),
                ptUser("A", "userA", "甲用户", "1", 1)));
        when(userOrgMapper.selectByUserIds(anyList())).thenReturn(List.of(
                userOrg("A", "ORG_A"),
                userOrg("B", "ORG_B")));
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(
                orgInfo("ORG_A", "甲机构"),
                orgInfo("ORG_B", "乙机构")));

        List<UserDTO> result = userFacade.getUserByEmpIds(List.of("A", "B"));

        assertThat(result).hasSize(2);
        // 按入参 empIds 顺序（A、B），而非批量查询返回顺序（B、A）
        UserDTO a = result.get(0);
        assertThat(a.getEmpId()).isEqualTo("A");
        assertThat(a.getUsername()).isEqualTo("userA");
        assertThat(a.getDisplayName()).isEqualTo("甲用户");
        assertThat(a.getUserType()).isEqualTo("1");
        // ISENABLED 反向语义：1 → 未启用
        assertThat(a.getEnabled()).isFalse();
        assertThat(a.getMainOrgCode()).isEqualTo("ORG_A");
        assertThat(a.getMainOrgName()).isEqualTo("甲机构");

        UserDTO b = result.get(1);
        assertThat(b.getEmpId()).isEqualTo("B");
        assertThat(b.getUsername()).isEqualTo("userB");
        assertThat(b.getDisplayName()).isEqualTo("乙用户");
        // ISENABLED 反向语义：0 → 启用
        assertThat(b.getEnabled()).isTrue();
        assertThat(b.getMainOrgCode()).isEqualTo("ORG_B");
        assertThat(b.getMainOrgName()).isEqualTo("乙机构");

        // 关键防回归断言：单次批量查询，绝不逐人查 PT_USER/EXT_USER_ORG/EXT_ORG_INFO
        verify(userMapper, times(1)).selectByUserIds(anyList());
        verify(userMapper, never()).selectByUserId(anyString());
        verify(userOrgMapper, times(1)).selectByUserIds(anyList());
        verify(userOrgMapper, never()).selectByUserId(anyString());
        verify(orgMapper, times(1)).selectByOrgCodes(any());
        verify(orgMapper, never()).selectByOrgCode(anyString());
    }

    @Test
    void getUserByEmpIds_userWithoutMainOrg_leavesOrgFieldsNull() {
        when(userMapper.selectByUserIds(anyList())).thenReturn(List.of(
                ptUser("A", "userA", "甲用户", "1", 0)));
        when(userOrgMapper.selectByUserIds(anyList())).thenReturn(List.of());

        List<UserDTO> result = userFacade.getUserByEmpIds(List.of("A"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMainOrgCode()).isNull();
        assertThat(result.get(0).getMainOrgName()).isNull();
        // 无人有主机构时，机构编码集合为空，不应发起空 IN 查询
        verify(orgMapper, never()).selectByOrgCodes(any());
    }

    @Test
    void getUserByEmpIds_blankOrEmpty_returnsEmptyNoDb() {
        assertThat(userFacade.getUserByEmpIds(null)).isEmpty();
        assertThat(userFacade.getUserByEmpIds(new ArrayList<>())).isEmpty();
        verify(userMapper, never()).selectByUserIds(anyList());
        verify(userOrgMapper, never()).selectByUserIds(anyList());
    }

    @Test
    void getUsersByUsernames_returnsFieldsMatchingSingleUserMapping_singleBatchQuery_noPerUserNPlusOne() {
        when(userMapper.selectByUsernames(anyList())).thenReturn(List.of(
                ptUser("A", "userA", "甲用户", "1", 0)));
        when(userOrgMapper.selectByUserIds(anyList())).thenReturn(List.of(
                userOrg("A", "ORG_A")));
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(
                orgInfo("ORG_A", "甲机构")));

        List<UserDTO> result = userFacade.getUsersByUsernames(List.of("userA"));

        assertThat(result).hasSize(1);
        UserDTO dto = result.get(0);
        assertThat(dto.getEmpId()).isEqualTo("A");
        assertThat(dto.getUsername()).isEqualTo("userA");
        assertThat(dto.getDisplayName()).isEqualTo("甲用户");
        assertThat(dto.getMainOrgCode()).isEqualTo("ORG_A");
        assertThat(dto.getMainOrgName()).isEqualTo("甲机构");

        // 关键防回归断言：单次批量查询，绝不逐人查 PT_USER/EXT_USER_ORG/EXT_ORG_INFO
        verify(userMapper, times(1)).selectByUsernames(anyList());
        verify(userMapper, never()).selectByUserId(anyString());
        verify(userOrgMapper, times(1)).selectByUserIds(anyList());
        verify(userOrgMapper, never()).selectByUserId(anyString());
        verify(orgMapper, times(1)).selectByOrgCodes(any());
        verify(orgMapper, never()).selectByOrgCode(anyString());
    }

    @Test
    void getUsersByUsernames_blankOrEmpty_returnsEmptyNoDb() {
        assertThat(userFacade.getUsersByUsernames(null)).isEmpty();
        assertThat(userFacade.getUsersByUsernames(new ArrayList<>())).isEmpty();
        verify(userMapper, never()).selectByUsernames(anyList());
        verify(userOrgMapper, never()).selectByUserIds(anyList());
    }

    private static PtUser ptUser(String userId, String username, String chnName, String userType, int isEnabled) {
        PtUser u = new PtUser();
        u.setUserId(userId);
        u.setUsername(username);
        u.setUserchnname(chnName);
        u.setUserType(userType);
        u.setIsEnabled(isEnabled);
        return u;
    }

    private static ExtUserOrg userOrg(String userId, String orgCode) {
        ExtUserOrg uo = new ExtUserOrg();
        uo.setUserId(userId);
        uo.setOrgCode(orgCode);
        return uo;
    }

    private static ExtOrgInfo orgInfo(String orgCode, String orgName) {
        ExtOrgInfo o = new ExtOrgInfo();
        o.setOrgCode(orgCode);
        o.setOrgName(orgName);
        return o;
    }
}
