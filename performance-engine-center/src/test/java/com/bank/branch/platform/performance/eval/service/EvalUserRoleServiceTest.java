package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserRoleServiceTest {

    @Mock private EvalUserTagMapper evalUserTagMapper;
    @Mock private EvalTagMapper evalTagMapper;
    @Mock private UserApi userApi;
    @Mock private AddressBookApi addressBookApi;
    @Mock private EvalUserSettingMapper evalUserSettingMapper;

    @InjectMocks private EvalUserTagService service;

    @Captor private ArgumentCaptor<List<EvalUserTag>> insertCaptor;

    private EvalTag tag(long id) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        return t;
    }

    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    private EvalUserTagRow tagRow(String uid, long tid, String name) {
        EvalUserTagRow r = new EvalUserTagRow();
        r.setUserId(uid); r.setTagId(tid); r.setTagName(name);
        return r;
    }

    @Test
    @DisplayName("saveUserRole 覆盖：先删该用户旧标签，再插入单标签")
    void saveUserRole_overwrite() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        EvalUserTag old2 = new EvalUserTag(); old2.setUserId("1001"); old2.setTagId(8L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1, old2));
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L));

        service.saveUserRole("1001", 1L);

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L, 8L)));
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(1L);
    }

    @Test
    @DisplayName("saveUserRole tagId=null 表示清空：仅删除不插入")
    void saveUserRole_clear() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1));

        service.saveUserRole("1001", null);

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L)));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRole tagId 指向不存在标签 → PERF-40058")
    void saveUserRole_tagNotFound_throws() {
        when(evalTagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveUserRole("1001", 99L))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_NOT_FOUND));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=1(参与) → 写标签 + 移出排除名单")
    void saveUserRoleWithSetting_participate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L));

        service.saveUserRoleWithSetting("1001", 2L, 1);

        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
        verify(evalUserSettingMapper).clearExcluded("1001");
        verify(evalUserSettingMapper, never()).markExcluded(anyString());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=0(不参与) → 写入排除名单")
    void saveUserRoleWithSetting_notParticipate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L));

        service.saveUserRoleWithSetting("1001", 2L, 0);

        verify(evalUserSettingMapper).markExcluded("1001");
        verify(evalUserSettingMapper, never()).clearExcluded(anyString());
    }

    @Test
    @DisplayName("saveUserRoleWithSetting：evalEnabled=null 兜底参与 → 移出排除名单")
    void saveUserRoleWithSetting_nullDefaultsParticipate() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());

        service.saveUserRoleWithSetting("1001", null, null);

        verify(evalUserSettingMapper).clearExcluded("1001");
        verify(evalUserSettingMapper, never()).markExcluded(anyString());
    }

    @Test
    @DisplayName("pageUserRoles 拼装：单标签、部门/岗位/角色到位")
    void pageUserRoles_assembles() {
        when(userApi.pageUsers("张", 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("1001", "张三"))));
        when(addressBookApi.getEmployees(List.of("1001")))
                .thenReturn(List.of(EmployeeDTO.builder().empId("1001").orgName("某支行").position("行长").build()));
        RoleSimpleDTO role = new RoleSimpleDTO();
        role.setRoleChName("管理员");
        when(userApi.getRolesByUserIds(List.of("1001"))).thenReturn(Map.of("1001", List.of(role)));
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("1001")))
                .thenReturn(List.of(tagRow("1001", 1L, "支行行长")));
        when(evalUserSettingMapper.selectExcludedUserIdsIn(List.of("1001"))).thenReturn(List.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles("张", "all", 1, 20);

        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getUserId()).isEqualTo("1001");
        assertThat(row.getUserName()).isEqualTo("张三");
        assertThat(row.getOrgName()).isEqualTo("某支行");
        assertThat(row.getRoleNames()).containsExactly("管理员");
        assertThat(row.getTag().getTagName()).isEqualTo("支行行长");
        assertThat(row.getEvalEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("pageUserRoles 无标签：tag 为 null 不报错")
    void pageUserRoles_noTag() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("U_ABC", "李四"))));
        when(addressBookApi.getEmployees(List.of("U_ABC"))).thenReturn(List.of());
        when(userApi.getRolesByUserIds(List.of("U_ABC"))).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("U_ABC"))).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "all", 1, 20);

        assertThat(r.getRecords().get(0).getTag()).isNull();
    }

    @Test
    @DisplayName("pageUserRoles 默认(参与)：剔除排除名单内的人")
    void pageUserRoles_participateDefault() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 2L, List.of(user("1001", "张三"), user("1002", "李四"))));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(List.of("1001", "1002"))).thenReturn(List.of("1002"));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "1", 1, 20);

        assertThat(r.getRecords()).extracting(EvalUserRoleRowDTO::getUserId).containsExactly("1001");
    }

    @Test
    @DisplayName("pageUserRoles 不参与：名单驱动，total 准确")
    void pageUserRoles_excludedDriven() {
        when(evalUserSettingMapper.selectExcludedUserIds()).thenReturn(List.of("1001", "1002"));
        when(userApi.getUserByEmpIds(List.of("1001", "1002")))
                .thenReturn(List.of(user("1001", "张三"), user("1002", "李四")));
        when(addressBookApi.getEmployees(anyList())).thenReturn(List.of());
        when(userApi.getRolesByUserIds(anyList())).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(anyList())).thenReturn(List.of());
        when(evalUserSettingMapper.selectExcludedUserIdsIn(anyList())).thenReturn(List.of("1001", "1002"));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, "0", 1, 20);

        assertThat(r.getTotal()).isEqualTo(2L);
        assertThat(r.getRecords()).allSatisfy(row -> assertThat(row.getEvalEnabled()).isEqualTo(0));
        verify(userApi, never()).pageUsers(any(), anyInt(), anyInt());
    }
}
