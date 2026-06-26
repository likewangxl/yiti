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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserFacade#mapEmpIdsToUsername 单元测试.
 *
 * <p>该方法用于大批量导出（如 20 万行评价明细）时按 USER_ID 反查登录名(工号 USERNAME)：
 * 必须是**单次/分片 IN 查询**，仅取 USER_ID/USERNAME 两列，
 * 不得走 {@code getUserByEmpId} 的逐人 N+1（每人 3 查询）路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeMapEmpIdsToUsernameTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    void mapEmpIdsToUsername_returnsUserIdToUsername_singleBatchQuery_noPerUserNPlusOne() {
        when(userMapper.selectByUserIds(anyList())).thenReturn(List.of(
                ptUser("U1", "emp001"), ptUser("U2", "emp002")));

        Map<String, String> result = userFacade.mapEmpIdsToUsername(
                List.of("U1", "GHOST", "U2"));

        // 键=USER_ID、值=USERNAME(工号)，不存在者不出现
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("U1", "emp001");
        assertThat(result).containsEntry("U2", "emp002");
        assertThat(result).doesNotContainKey("GHOST");

        // 关键：单次批量查询，绝不触发逐人 getUserByEmpId（userOrgMapper / orgMapper 一次都不调）
        verify(userMapper, times(1)).selectByUserIds(anyList());
        verify(userOrgMapper, never()).selectByUserId(anyString());
        verify(orgMapper, never()).selectByOrgCode(anyString());
    }

    @Test
    void mapEmpIdsToUsername_blankOrEmpty_returnsEmptyNoDb() {
        assertThat(userFacade.mapEmpIdsToUsername(null)).isEmpty();
        assertThat(userFacade.mapEmpIdsToUsername(new ArrayList<>())).isEmpty();
        verify(userMapper, never()).selectByUserIds(anyList());
    }

    @Test
    void mapEmpIdsToUsername_overChunkSize_queriesInChunks() {
        // 2500 个不同 USER_ID → 分片 1000 → 应分 3 次 IN 查询
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            ids.add("U" + i);
        }
        when(userMapper.selectByUserIds(anyList())).thenReturn(List.of());

        userFacade.mapEmpIdsToUsername(ids);

        verify(userMapper, times(3)).selectByUserIds(anyList());
    }

    private static PtUser ptUser(String userId, String username) {
        PtUser u = new PtUser();
        u.setUserId(userId);
        u.setUsername(username);
        return u;
    }
}
