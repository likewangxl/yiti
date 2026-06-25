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
 * UserFacade#mapUsernamesToEmpId 单元测试.
 *
 * <p>该方法用于大批量导入（如评价任务导入）时的「存在性校验 + 用户名归一为 USER_ID」，
 * 必须是**单次/分片 IN 查询**，仅取 USERNAME/USER_ID 两列，
 * 不得走 {@code getUserByEmpId} 的逐人 N+1（每人 3 查询）路径。
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeMapUsernamesToEmpIdTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    @Test
    void mapUsernamesToEmpId_returnsUsernameToUserId_singleBatchQuery_noPerUserNPlusOne() {
        // DB 存在 emp001(USER_ID=U1) / emp002(USER_ID=U2)，ghost 不存在
        when(userMapper.selectByUsernames(anyList())).thenReturn(List.of(
                ptUser("U1", "emp001"), ptUser("U2", "emp002")));

        Map<String, String> result = userFacade.mapUsernamesToEmpId(
                List.of("emp001", "ghost", "emp002"));

        // 只含存在者，且键=用户名、值=USER_ID（供导入归一存储）
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("emp001", "U1");
        assertThat(result).containsEntry("emp002", "U2");
        assertThat(result).doesNotContainKey("ghost");

        // 关键：单次批量查询，绝不触发逐人 getUserByEmpId（userOrgMapper / orgMapper 一次都不调）
        verify(userMapper, times(1)).selectByUsernames(anyList());
        verify(userOrgMapper, never()).selectByUserId(anyString());
        verify(orgMapper, never()).selectByOrgCode(anyString());
    }

    @Test
    void mapUsernamesToEmpId_blankOrEmpty_returnsEmptyNoDb() {
        assertThat(userFacade.mapUsernamesToEmpId(null)).isEmpty();
        assertThat(userFacade.mapUsernamesToEmpId(new ArrayList<>())).isEmpty();
        verify(userMapper, never()).selectByUsernames(anyList());
    }

    @Test
    void mapUsernamesToEmpId_overChunkSize_queriesInChunks() {
        // 构造 2500 个不同用户名 → 分片 1000 → 应分 3 次 IN 查询
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            names.add("emp" + i);
        }
        // 每次分片均返回空（只验证调用次数，不验证内容）
        when(userMapper.selectByUsernames(anyList())).thenReturn(List.of());

        userFacade.mapUsernamesToEmpId(names);

        verify(userMapper, times(3)).selectByUsernames(anyList());
    }

    private static PtUser ptUser(String userId, String username) {
        PtUser u = new PtUser();
        u.setUserId(userId);
        u.setUsername(username);
        return u;
    }
}
