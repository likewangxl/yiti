package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AddrbookQueryService 单元测试
 * <p>TDD RED-GREEN 闭环：先写测试，再实现 Service</p>
 */
@ExtendWith(MockitoExtension.class)
class AddrbookQueryServiceTest {

    @Mock
    AddrbookEmployeeMapper addrbookMapper;

    @InjectMocks
    AddrbookQueryService addrbookQueryService;

    /**
     * listResponsibleEmps 应返回脱敏手机号的 ResponsibleEmpDTO 列表
     */
    @Test
    void listResponsibleEmpsShouldReturnDTOsWithMaskedMobile() {
        AddrbookEmployee emp = new AddrbookEmployee();
        emp.setEmpId("E10001");
        emp.setEmpName("张三");
        emp.setMobile("13812345678");
        emp.setPosition("客户经理");
        emp.setStatus("ACTIVE");

        when(addrbookMapper.listByEmpIds(List.of("E10001"))).thenReturn(List.of(emp));

        List<ResponsibleEmpDTO> result = addrbookQueryService.listResponsibleEmps(List.of("E10001"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmpId()).isEqualTo("E10001");
        assertThat(result.get(0).getEmpName()).isEqualTo("张三");
        assertThat(result.get(0).getMobile()).isEqualTo("138****5678");
        assertThat(result.get(0).getPosition()).isEqualTo("客户经理");
    }

    /**
     * listResponsibleEmps 传入 null 或空列表时应返回空列表
     */
    @Test
    void listResponsibleEmpsShouldReturnEmptyForNullInput() {
        assertThat(addrbookQueryService.listResponsibleEmps(null)).isEmpty();
        assertThat(addrbookQueryService.listResponsibleEmps(Collections.emptyList())).isEmpty();
    }

    /**
     * findInvalidEmpIds 全部存在且 ACTIVE 时应返回空列表
     */
    @Test
    void findInvalidEmpIdsShouldReturnEmptyWhenAllActive() {
        List<String> empIds = List.of("E10001", "E10002");
        when(addrbookMapper.countActiveByEmpIds(empIds)).thenReturn(2);

        List<String> result = addrbookQueryService.findInvalidEmpIds(empIds);

        assertThat(result).isEmpty();
    }

    /**
     * findInvalidEmpIds 部分不存在时应返回缺失的 empId 集合
     */
    @Test
    void findInvalidEmpIdsShouldReturnMissingEmpIds() {
        List<String> empIds = List.of("E10001", "E10002", "E99999");
        when(addrbookMapper.countActiveByEmpIds(empIds)).thenReturn(2);

        AddrbookEmployee e1 = new AddrbookEmployee();
        e1.setEmpId("E10001");
        e1.setStatus("ACTIVE");
        AddrbookEmployee e2 = new AddrbookEmployee();
        e2.setEmpId("E10002");
        e2.setStatus("ACTIVE");
        when(addrbookMapper.listByEmpIds(empIds)).thenReturn(Arrays.asList(e1, e2));

        List<String> result = addrbookQueryService.findInvalidEmpIds(empIds);

        assertThat(result).containsExactly("E99999");
    }
}
