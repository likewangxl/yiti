package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.UserDirectoryApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.security.masker.SensitiveDataMasker;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通讯录内部查询服务（不开 REST）。
 *
 * <p>产品负责人查询统一走 auth 的 UserDirectoryApi，不再直接依赖
 * ADDRBOOK_EMPLOYEE 的实体或 Mapper。跨模块/导出使用的手机号在这里统一脱敏。</p>
 */
@Service
@RequiredArgsConstructor
public class AddrbookQueryService {

    private final UserDirectoryApi userDirectoryApi;

    /** 批量按 USER_ID 查询负责人，手机号脱敏。 */
    public List<ResponsibleEmpDTO> listResponsibleEmps(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> employees = userDirectoryApi.getEmployeesByIds(empIds);
        if (employees == null || employees.isEmpty()) {
            return Collections.emptyList();
        }
        return employees.stream().map(this::toResponsibleEmpDTO).collect(Collectors.toList());
    }

    /**
     * 校验 empIds 是否全部为 auth 目录中的在职用户。
     * UserDirectoryApi 只返回在职用户，因此未命中集合和返回之外的 ID 均视为非法。
     */
    public List<String> findInvalidEmpIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> employees = userDirectoryApi.getEmployeesByIds(empIds);
        Set<String> activeIds = employees == null ? Collections.emptySet() : employees.stream()
                .map(UserDirectoryDTO::getEmpId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        return empIds.stream()
                .filter(id -> id == null || !activeIds.contains(id.trim()))
                .distinct()
                .collect(Collectors.toList());
    }

    private ResponsibleEmpDTO toResponsibleEmpDTO(UserDirectoryDTO employee) {
        ResponsibleEmpDTO dto = new ResponsibleEmpDTO();
        dto.setEmpId(employee.getEmpId());
        dto.setEmpName(employee.getEmpName());
        dto.setMobile(employee.getMobile() == null ? null : SensitiveDataMasker.maskPhone(employee.getMobile()));
        dto.setPosition(employee.getPosition());
        return dto;
    }
}
