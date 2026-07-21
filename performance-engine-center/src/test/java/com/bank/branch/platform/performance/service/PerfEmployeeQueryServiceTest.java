package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.PerfEmpOptionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfEmployeeQueryService 单元测试。
 *
 * <p>本服务存在的理由就是「返回工号而非 USER_ID」：目标值 subjectId 存的是
 * {@code PT_USER.USERNAME}（工号），而平台既有的 {@code /api/reports/employees/search}
 * 返回的是 {@code UserDTO.empId}（= {@code PT_USER.USER_ID} 短代理键），两者对不上，
 * 直接复用会让选出来的人提交必失败。故首要用例就是钉死这条口径。
 */
@ExtendWith(MockitoExtension.class)
class PerfEmployeeQueryServiceTest {

    @Mock
    private UserApi userApi;

    @InjectMocks
    private PerfEmployeeQueryService service;

    private UserDTO user(String userId, String username, String displayName, String orgName) {
        UserDTO u = new UserDTO();
        u.setEmpId(userId);          // USER_ID 短代理键，如 E40001
        u.setUsername(username);     // 工号，如 finance_zhou —— 这才是 subjectId 用的值
        u.setDisplayName(displayName);
        u.setMainOrgName(orgName);
        return u;
    }

    @Test
    @DisplayName("返回的 username 是工号(USERNAME)，不是 USER_ID —— 与目标值 subjectId 同口径")
    void returnsUsernameNotUserId() {
        when(userApi.pageUsers(eq("zhou"), eq(1), anyInt()))
                .thenReturn(PageResult.of(1, 20, 1L,
                        List.of(user("E40001", "finance_zhou", "周八(资财)", "资财部"))));

        List<PerfEmpOptionDTO> out = service.searchEmployees("zhou", 20);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getUsername()).isEqualTo("finance_zhou");
        assertThat(out.get(0).getUsername()).isNotEqualTo("E40001");
        assertThat(out.get(0).getDisplayName()).isEqualTo("周八(资财)");
        assertThat(out.get(0).getOrgName()).isEqualTo("资财部");
    }

    @Test
    @DisplayName("limit 收敛到 [1,50]：防止前端传 0/负数/超大值打爆查询")
    void clampsLimit() {
        when(userApi.pageUsers(any(), eq(1), anyInt())).thenReturn(PageResult.of(1, 1, 0L, Collections.emptyList()));

        service.searchEmployees("k", 0);
        verify(userApi).pageUsers("k", 1, 1);

        service.searchEmployees("k", 9999);
        verify(userApi).pageUsers("k", 1, 50);
    }

    @Test
    @DisplayName("用户名为空的记录跳过：username 是给 subjectId 用的，空值选中即无效")
    void skipsRowsWithoutUsername() {
        when(userApi.pageUsers(any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 2L,
                        List.of(user("E1", null, "无工号", "X"),
                                user("E2", "ok_user", "有工号", "Y"))));

        List<PerfEmpOptionDTO> out = service.searchEmployees("k", 20);

        assertThat(out).extracting(PerfEmpOptionDTO::getUsername).containsExactly("ok_user");
    }

    @Test
    @DisplayName("上游返回 null 分页/记录时返回空列表，不抛 NPE")
    void nullSafe() {
        when(userApi.pageUsers(any(), anyInt(), anyInt())).thenReturn(null);
        assertThat(service.searchEmployees("k", 20)).isEmpty();
    }
}
