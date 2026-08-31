package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.security.masker.SensitiveDataMasker;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import com.bank.branch.platform.portal.service.AddressBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通讯录跨模块 Facade。
 *
 * <p>兼容原 {@link AddressBookApi} 契约，但数据统一来自 auth 的 UserDirectoryApi；
 * 手机号仅在跨模块 DTO 这里脱敏，REST Controller 仍返回本人编辑所需的原始值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookFacade implements AddressBookApi {

    private final AddressBookService addressBookService;
    private final UserApi userApi;

    /** 获取单个员工，不存在时返回 Optional.empty。 */
    @Override
    public Optional<EmployeeDTO> getEmployee(String empId) {
        try {
            UserDirectoryDTO employee = addressBookService.getEmployee(empId);
            List<String> productIds = addressBookService.listProductIdsByUserId(employee.getEmpId());
            return Optional.of(toDTO(employee, productIds));
        } catch (BizException e) {
            log.debug("[AddressBookFacade.getEmployee] 员工不存在, empId={}", empId);
            return Optional.empty();
        }
    }

    /** 批量获取员工，并一次性回填负责产品关系。 */
    @Override
    public List<EmployeeDTO> getEmployees(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> employees = addressBookService.getEmployees(empIds);
        return toDTOs(employees);
    }

    /** 模糊搜索员工，并一次性回填负责产品关系。 */
    @Override
    public List<EmployeeDTO> searchEmployees(String keyword, int limit) {
        return toDTOs(addressBookService.searchEmployees(keyword, limit));
    }

    /** 按机构查询在职员工，并一次性回填负责产品关系。 */
    @Override
    public List<EmployeeDTO> listEmployeesByOrg(String orgCode) {
        return toDTOs(addressBookService.listByOrg(orgCode));
    }

    /**
     * 通过 auth 角色编码兼容客户经理校验。
     *
     * <p>新通讯录三表联查没有岗位字段，不能再通过 position 文本猜测角色；角色由
     * UserApi 的公开角色查询契约提供。</p>
     */
    @Override
    public boolean isCustomerManager(String empId) {
        if (empId == null || empId.isBlank()) {
            return false;
        }
        Set<String> roles = userApi.getUserRoleCodes(empId.trim());
        return roles != null && (roles.contains("CUST_MARKETING_MANAGER") || roles.contains("R_RM"));
    }

    private List<EmployeeDTO> toDTOs(List<UserDirectoryDTO> employees) {
        if (employees == null || employees.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = employees.stream().map(UserDirectoryDTO::getEmpId)
                .filter(id -> id != null && !id.isBlank()).collect(Collectors.toList());
        Map<String, List<String>> productIdsByUser = addressBookService.mapProductIdsByUserIds(ids);
        return employees.stream()
                .map(employee -> toDTO(employee, productIdsByUser.get(employee.getEmpId())))
                .collect(Collectors.toList());
    }

    private EmployeeDTO toDTO(UserDirectoryDTO employee, List<String> productIds) {
        String mobile = employee.getMobile() == null ? null : SensitiveDataMasker.maskPhone(employee.getMobile());
        return EmployeeDTO.builder()
                .empId(employee.getEmpId())
                .empName(employee.getEmpName())
                .mobile(mobile)
                .email(employee.getEmail())
                .orgCode(employee.getOrgCode())
                .orgName(employee.getOrgName())
                .position(employee.getPosition())
                .positionDesc(null)
                .selfDesc(null)
                .responsibleProductIds(productIds == null ? Collections.emptyList() : productIds)
                .status(employee.getStatus())
                .updatedTime(employee.getUpdatedTime())
                .build();
    }
}
