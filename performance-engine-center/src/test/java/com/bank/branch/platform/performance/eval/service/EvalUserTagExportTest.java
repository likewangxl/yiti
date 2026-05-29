package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalUserTagExportTest {

    @Mock EvalUserTagMapper evalUserTagMapper;
    @Mock EvalTagMapper evalTagMapper;
    @Mock UserApi userApi;
    @Mock AddressBookApi addressBookApi;
    @Mock EvalUserSettingMapper evalUserSettingMapper;
    @InjectMocks EvalUserTagService service;

    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    @Test
    void listForExport_accumulatesAcrossPages() {
        List<UserDTO> page1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) page1.add(user("E1" + i, "U" + i));
        List<UserDTO> page2 = new ArrayList<>();
        for (int i = 0; i < 50; i++) page2.add(user("E2" + i, "V" + i));
        when(userApi.pageUsers(eq("k"), eq(1), eq(100))).thenReturn(PageResult.of(1, 100, 150, page1));
        when(userApi.pageUsers(eq("k"), eq(2), eq(100))).thenReturn(PageResult.of(2, 100, 150, page2));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of());

        List<EvalUserRoleRowDTO> rows = service.listForExport("k", "all", 10000);

        assertThat(rows).hasSize(150);
        assertThat(rows.get(0).getUserName()).isEqualTo("U0");
        assertThat(rows.get(149).getUserName()).isEqualTo("V49");
    }

    @Test
    void listForExport_stopsAtCap() {
        List<UserDTO> page1 = new ArrayList<>();
        for (int i = 0; i < 100; i++) page1.add(user("E1" + i, "U" + i));
        when(userApi.pageUsers(eq(null), eq(1), eq(100))).thenReturn(PageResult.of(1, 100, 500, page1));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of());

        List<EvalUserRoleRowDTO> rows = service.listForExport(null, "all", 100);

        assertThat(rows).hasSize(100);
    }

    @Test
    void listForExport_emptyWhenNoUsers() {
        when(userApi.pageUsers(any(), eq(1), eq(100)))
                .thenReturn(PageResult.of(1, 100, 0, List.of()));

        List<EvalUserRoleRowDTO> rows = service.listForExport("none", "all", 10000);

        assertThat(rows).isEmpty();
    }

    @Test
    void listForExport_excludedMode_usesNamelistDriven() {
        when(evalUserSettingMapper.selectExcludedUserIds()).thenReturn(List.of("1001", "1002"));
        when(userApi.getUserByEmpIds(List.of("1001", "1002")))
                .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of("1001", "1002"));

        List<EvalUserRoleRowDTO> rows = service.listForExport(null, "0", 10000);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.getEvalEnabled()).isEqualTo(0));
        verify(userApi, never()).pageUsers(any(), anyInt(), anyInt());
    }

    @Test
    void listForExport_participateMode_excludesNamelist() {
        List<UserDTO> page1 = List.of(user("1001", "张三"), user("1002", "李四"));
        when(userApi.pageUsers(eq(null), eq(1), eq(100))).thenReturn(PageResult.of(1, 100, 2, page1));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.<String, List<RoleSimpleDTO>>of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        // 1002 在排除名单（不参与）→ 参与导出剔除
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of("1002"));

        List<EvalUserRoleRowDTO> rows = service.listForExport(null, "1", 10000);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUserId()).isEqualTo("1001");
        assertThat(rows.get(0).getEvalEnabled()).isEqualTo(1);
    }
}
