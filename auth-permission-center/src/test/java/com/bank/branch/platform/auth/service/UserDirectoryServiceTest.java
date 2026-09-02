package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.mapper.UserDirectoryMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

    @Mock UserDirectoryMapper userDirectoryMapper;
    @Mock UserMapper userMapper;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks UserDirectoryService userDirectoryService;

    private UserDirectoryDTO sample(String empId, String name) {
        UserDirectoryDTO d = new UserDirectoryDTO();
        d.setEmpId(empId);
        d.setEmpName(name);
        d.setOrgCode("BJ_CY");
        d.setOrgName("北京分行朝阳支行");
        d.setStatus("ACTIVE");
        return d;
    }

    private CurrentUserContext currentUser(String empId) {
        return new CurrentUserContext(empId, empId, "测试用户", "BJ_CY", "北京分行朝阳支行", 3,
                Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    void searchEmployees_blankKeyword_returnsEmptyWithoutQuery() {
        assertThat(userDirectoryService.searchEmployees("  ", 20)).isEmpty();
        assertThat(userDirectoryService.searchEmployees(null, 20)).isEmpty();
        verify(userDirectoryMapper, never()).searchByKeyword(org.mockito.ArgumentMatchers.anyString(), anyInt());
    }

    @Test
    void searchEmployees_trimsKeywordAndClampsLimitToMax50() {
        when(userDirectoryMapper.searchByKeyword(eq("张"), eq(50)))
                .thenReturn(List.of(sample("user001", "张三")));

        List<UserDirectoryDTO> r = userDirectoryService.searchEmployees("  张  ", 999);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).getEmpId()).isEqualTo("user001");
        verify(userDirectoryMapper).searchByKeyword("张", 50);
    }

    @Test
    void searchEmployees_clampsNonPositiveLimitToOne() {
        when(userDirectoryMapper.searchByKeyword(eq("李"), eq(1)))
                .thenReturn(List.of(sample("user002", "李四")));

        userDirectoryService.searchEmployees("李", 0);

        verify(userDirectoryMapper).searchByKeyword("李", 1);
    }

    @Test
    void getEmployee_blankEmpId_returnsNullWithoutQuery() {
        assertThat(userDirectoryService.getEmployee(" ")).isNull();
        assertThat(userDirectoryService.getEmployee(null)).isNull();
        verify(userDirectoryMapper, never()).selectByEmpId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getEmployee_returnsMappedDto() {
        when(userDirectoryMapper.selectByEmpId("user001")).thenReturn(sample("user001", "张三"));

        UserDirectoryDTO d = userDirectoryService.getEmployee("user001");

        assertThat(d).isNotNull();
        assertThat(d.getEmpName()).isEqualTo("张三");
        assertThat(d.getOrgName()).isEqualTo("北京分行朝阳支行");
    }

    @Test
    void pageActiveUsers_filtersAndNormalizesPaging() {
        UserDirectoryDTO row = sample("user001", "张三");
        when(userDirectoryMapper.selectActiveUsersByPage("张", "BJ_CY", 0, 100))
                .thenReturn(List.of(row));
        when(userDirectoryMapper.countActiveUsers("张", "BJ_CY")).thenReturn(1L);

        PageResult<UserDirectoryDTO> result = userDirectoryService.pageActiveUsers("  张  ", " BJ_CY ", 0, 999);

        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).containsExactly(row);
        verify(userDirectoryMapper).selectActiveUsersByPage("张", "BJ_CY", 0, 100);
    }

    @Test
    void getEmployeesByIds_preservesRequestedOrder() {
        UserDirectoryDTO first = sample("user001", "张三");
        UserDirectoryDTO second = sample("user002", "李四");
        when(userDirectoryMapper.selectByEmpIds(anyList())).thenReturn(List.of(first, second));

        List<UserDirectoryDTO> result = userDirectoryService.getEmployeesByIds(List.of("user002", "user001", "missing"));

        assertThat(result).extracting(UserDirectoryDTO::getEmpId)
                .containsExactly("user002", "user001");
        verify(userDirectoryMapper).selectByEmpIds(List.of("user002", "user001", "missing"));
    }

    @Test
    void updateCurrentUserContact_usesEmpIdFromCurrentContext() {
        when(currentUserProvider.get()).thenReturn(currentUser("user001"));
        when(userMapper.updateContact("user001", "13900000001", "new@example.com")).thenReturn(1);
        UserDirectoryDTO updated = sample("user001", "张三");
        updated.setMobile("13900000001");
        updated.setEmail("new@example.com");
        when(userDirectoryMapper.selectByEmpId("user001")).thenReturn(updated);

        UserDirectoryDTO result = userDirectoryService.updateCurrentUserContact("13900000001", "new@example.com");

        assertThat(result.getMobile()).isEqualTo("13900000001");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userMapper).updateContact("user001", "13900000001", "new@example.com");
    }

    @Test
    void updateCurrentUserContact_withoutContext_rejectsUpdate() {
        when(currentUserProvider.get()).thenReturn(null);

        assertThatThrownBy(() -> userDirectoryService.updateCurrentUserContact("13900000001", "new@example.com"))
                .isInstanceOf(AuthException.class);
        verify(userMapper, never()).updateContact(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
