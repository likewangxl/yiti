package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.common.security.masker.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通讯录内部查询服务（不开 REST，仅供 portal 模块内部 D.2/D.4/D.5/D.6 使用）
 */
@Service
@RequiredArgsConstructor
public class AddrbookQueryService {

    private final AddrbookEmployeeMapper addrbookMapper;

    /**
     * 批量按 empId 查询并转换为 ResponsibleEmpDTO（含手机号脱敏）
     *
     * @param empIds 员工工号列表
     * @return 负责人 DTO 列表，手机号已脱敏
     */
    public List<ResponsibleEmpDTO> listResponsibleEmps(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        return addrbookMapper.listByEmpIds(empIds).stream()
                .map(this::toResponsibleEmpDTO)
                .collect(Collectors.toList());
    }

    /**
     * 校验 empIds 全部存在且 ACTIVE，返回不存在或非 ACTIVE 的 empId 集合
     *
     * @param empIds 员工工号列表
     * @return 不存在或非 ACTIVE 的 empId 集合，全部合法时返回空列表
     */
    public List<String> findInvalidEmpIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        int activeCount = addrbookMapper.countActiveByEmpIds(empIds);
        if (activeCount == empIds.size()) {
            return Collections.emptyList();
        }
        List<String> foundActive = addrbookMapper.listByEmpIds(empIds).stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .map(AddrbookEmployee::getEmpId)
                .collect(Collectors.toList());
        return empIds.stream()
                .filter(id -> !foundActive.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * 将 AddrbookEmployee 实体转换为 ResponsibleEmpDTO（含手机号脱敏）
     *
     * @param e 通讯录员工实体
     * @return 负责人 DTO
     */
    private ResponsibleEmpDTO toResponsibleEmpDTO(AddrbookEmployee e) {
        ResponsibleEmpDTO dto = new ResponsibleEmpDTO();
        dto.setEmpId(e.getEmpId());
        dto.setEmpName(e.getEmpName());
        dto.setMobile(SensitiveDataMasker.maskPhone(e.getMobile()));
        dto.setPosition(e.getPosition());
        return dto;
    }
}
