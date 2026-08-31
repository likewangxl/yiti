package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.UserDirectoryApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 负责人查询改用 UserDirectoryApi 的测试。 */
@ExtendWith(MockitoExtension.class)
class AddrbookQueryServiceTest {

    @Mock UserDirectoryApi userDirectoryApi;
    @InjectMocks AddrbookQueryService service;

    @Test
    void listResponsibleEmpsMasksMobile() {
        UserDirectoryDTO employee = employee("U1", "张三");
        employee.setMobile("13812345678");
        when(userDirectoryApi.getEmployeesByIds(List.of("U1"))).thenReturn(List.of(employee));

        List<ResponsibleEmpDTO> result = service.listResponsibleEmps(List.of("U1"));

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getEmpId()).isEqualTo("U1");
            assertThat(dto.getEmpName()).isEqualTo("张三");
            assertThat(dto.getMobile()).isEqualTo("138****5678");
        });
    }

    @Test
    void findInvalidEmpIdsTreatsUnmatchedUsersAsInvalid() {
        when(userDirectoryApi.getEmployeesByIds(List.of("U1", "U2", "U3")))
                .thenReturn(List.of(employee("U1", "张三"), employee("U2", "李四")));

        assertThat(service.findInvalidEmpIds(List.of("U1", "U2", "U3")))
                .containsExactly("U3");
    }

    private UserDirectoryDTO employee(String id, String name) {
        UserDirectoryDTO dto = new UserDirectoryDTO();
        dto.setEmpId(id);
        dto.setEmpName(name);
        dto.setStatus("ACTIVE");
        return dto;
    }
}
