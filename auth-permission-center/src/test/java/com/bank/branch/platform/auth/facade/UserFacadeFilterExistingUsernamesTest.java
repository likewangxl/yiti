package com.bank.branch.platform.auth.facade;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserFacade#filterExistingUsernames 单元测试.
 *
 * <p>该方法用于大批量「工号是否存在」校验（如导入 5 万行），必须是**单次/分片 IN 查询**，
 * 不得走 {@code getUserByEmpId} 的逐人 N+1（每人 3 查询）路径。
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeFilterExistingUsernamesTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    void filterExistingUsernames_returnsOnlyExisting_singleBatchQuery_noPerUserNPlusOne() {
        // DB 只有 emp001 / emp002 存在，ghost 不存在
        when(userMapper.selectByUsernames(anyList())).thenReturn(List.of(
                ptUser("U1", "emp001"), ptUser("U2", "emp002")));

        List<String> result = userFacade.filterExistingUsernames(
                List.of("emp001", "ghost", "emp002"));

        assertThat(result).containsExactlyInAnyOrder("emp001", "emp002");
        // 关键：单次批量查询，且绝不触发逐人 getUserByEmpId（userOrgMapper / orgMapper 一次都不调）
        verify(userMapper, times(1)).selectByUsernames(anyList());
        verify(userOrgMapper, never()).selectByUserId(anyString());
        verify(orgMapper, never()).selectByOrgCode(anyString());
    }

    @Test
    void filterExistingUsernames_blankOrEmpty_returnsEmptyNoDb() {
        assertThat(userFacade.filterExistingUsernames(null)).isEmpty();
        assertThat(userFacade.filterExistingUsernames(new ArrayList<>())).isEmpty();
        verify(userMapper, never()).selectByUsernames(anyList());
    }

    private static PtUser ptUser(String userId, String username) {
        PtUser u = new PtUser();
        u.setUserId(userId);
        u.setUsername(username);
        return u;
    }
}
