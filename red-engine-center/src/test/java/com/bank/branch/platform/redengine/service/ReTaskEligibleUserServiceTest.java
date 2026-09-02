package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserPageQueryDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务可选员工查询服务契约测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskEligibleUserServiceTest {

    @Mock
    private ReUserPartyMapMapper userPartyMapMapper;
    @Mock
    private RePartyOrgMapper partyOrgMapper;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private UserApi userApi;

    @InjectMocks
    private ReTaskEligibleUserServiceImpl service;

    @Test
    void managementRole_canQueryOnlyEnabledMappedEmployeesWithinBranchScope() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(userPartyMapMapper.selectList(any())).thenReturn(List.of(
                mapping("U-1", 101L), mapping("U-2", 102L), mapping("U-3", 101L)));
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(
                org(101L, null, 2, "一支部"), org(102L, null, 2, "二支部")));
        when(userApi.getUserByEmpIds(List.of("U-1", "U-3"))).thenReturn(List.of(
                user("U-1", "zhang", "张三", true),
                user("U-2", "li", "李四", false),
                user("U-3", "wang", "王五", true)));

        ReTaskEligibleUserPageQueryDTO query = new ReTaskEligibleUserPageQueryDTO();
        query.setPageNo(1);
        query.setPageSize(1);
        query.setBranchId(101L);

        var result = service.page(query, "ORG-REVIEWER");

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getEmployeeId()).isEqualTo("U-1");
        assertThat(result.getRecords().get(0).getBranchId()).isEqualTo(101L);
        assertThat(result.getRecords().get(0).getBranchName()).isEqualTo("一支部");
    }

    @Test
    void keyword_matchesEmployeeIdUsernameOrDisplayName_beforePaging() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("SYS_ADMIN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(userPartyMapMapper.selectList(any())).thenReturn(List.of(
                mapping("U-1", 101L), mapping("U-2", 101L)));
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(org(101L, null, 2, "一支部")));
        when(userApi.getUserByEmpIds(List.of("U-1", "U-2"))).thenReturn(List.of(
                user("U-1", "zhang", "张三", true), user("U-2", "li", "李四", true)));

        ReTaskEligibleUserPageQueryDTO query = new ReTaskEligibleUserPageQueryDTO();
        query.setKeyword("张");
        query.setPageNo(1);
        query.setPageSize(20);

        var result = service.page(query, "admin");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).extracting("employeeId").containsExactly("U-1");
    }

    @Test
    void reporterCannotQueryEligibleEmployees_andNoDomainQueryIsAttempted() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("REPORTER");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_REPORT"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.page(new ReTaskEligibleUserPageQueryDTO(), "REPORTER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权查询任务可选员工");

        verify(userPartyMapMapper, never()).selectList(any());
        verify(userApi, never()).getUserByEmpIds(any());
    }

    @Test
    void authUserApiFailure_isFailClosed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(userPartyMapMapper.selectList(any())).thenReturn(List.of(mapping("U-1", 101L)));
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(org(101L, null, 2, "一支部")));
        when(userApi.getUserByEmpIds(List.of("U-1"))).thenThrow(new IllegalStateException("auth unavailable"));

        assertThatThrownBy(() -> service.page(new ReTaskEligibleUserPageQueryDTO(), "ORG-REVIEWER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可选员工查询失败");
    }

    @Test
    void domainMappingFailure_isFailClosed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(userPartyMapMapper.selectList(any())).thenThrow(new IllegalStateException("mapping unavailable"));

        assertThatThrownBy(() -> service.page(new ReTaskEligibleUserPageQueryDTO(), "ORG-REVIEWER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可选员工查询失败");

        verify(partyOrgMapper, never()).selectList(any());
        verify(userApi, never()).getUserByEmpIds(any());
    }

    private static ReUserPartyMap mapping(String userId, Long partyOrgId) {
        ReUserPartyMap mapping = new ReUserPartyMap();
        mapping.setUserId(userId);
        mapping.setPartyOrgId(partyOrgId);
        return mapping;
    }

    private static RePartyOrg org(Long id, Long parentId, int level, String name) {
        RePartyOrg org = new RePartyOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setOrgLevel(level);
        org.setOrgName(name);
        return org;
    }

    private static UserDTO user(String id, String username, String displayName, boolean enabled) {
        UserDTO user = new UserDTO();
        user.setEmpId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEnabled(enabled);
        return user;
    }
}
