package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.mapper.UserDirectoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

    @Mock UserDirectoryMapper userDirectoryMapper;
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
}
