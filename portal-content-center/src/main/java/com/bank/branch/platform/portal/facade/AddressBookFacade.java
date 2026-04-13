package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import com.bank.branch.platform.portal.convert.EmployeeConverter;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.service.AddressBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通讯录 Facade 实现
 *
 * <p>实现 {@link AddressBookApi} 接口，负责将 AddressBookService / AddrbookEmployeeMapper
 * 返回的实体转换为跨模块 DTO。所有方法均为只读查询，不提供写操作。</p>
 *
 * <p>异常处理约定：
 * <ul>
 *   <li>单条查询不存在时返回 {@code Optional.empty()}，不抛异常</li>
 *   <li>批量查询不存在的项不包含在返回列表中，不报错</li>
 *   <li>系统异常直接抛出 RuntimeException，由调用方降级</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookFacade implements AddressBookApi {

    private final AddressBookService addressBookService;
    private final AddrbookEmployeeMapper addrbookEmployeeMapper;

    /**
     * 获取员工通讯录信息。
     * 捕获 BizException（员工不存在）返回 Optional.empty()，符合 API 契约。
     *
     * @param empId 员工工号
     * @return 员工详情 DTO，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<EmployeeDTO> getEmployee(String empId) {
        try {
            AddrbookEmployee entity = addressBookService.getEmployee(empId);
            return Optional.ofNullable(EmployeeConverter.toDTO(entity));
        } catch (BizException e) {
            log.debug("[AddressBookFacade.getEmployee] 员工不存在, empId={}", empId);
            return Optional.empty();
        }
    }

    /**
     * 批量获取员工信息。
     * 使用 Mapper 直接批量查询，避免 N+1。
     *
     * @param empIds 员工工号列表
     * @return 员工DTO列表（不存在的员工不返回）
     */
    @Override
    public List<EmployeeDTO> getEmployees(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<AddrbookEmployee> entities = addrbookEmployeeMapper.listByEmpIds(empIds);
        return entities.stream()
                .map(EmployeeConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 模糊搜索员工。
     * 委托 AddressBookService 进行搜索。
     *
     * @param keyword 关键词（工号/姓名）
     * @param limit   返回数量上限
     * @return 员工DTO列表
     */
    @Override
    public List<EmployeeDTO> searchEmployees(String keyword, int limit) {
        List<AddrbookEmployee> entities = addressBookService.searchEmployees(keyword, limit);
        return entities.stream()
                .map(EmployeeConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按机构查询员工列表。
     * 委托 AddressBookService 按机构代码查询。
     *
     * @param orgCode 机构编码
     * @return 该机构下的全部员工DTO列表
     */
    @Override
    public List<EmployeeDTO> listEmployeesByOrg(String orgCode) {
        List<AddrbookEmployee> entities = addressBookService.listByOrg(orgCode);
        return entities.stream()
                .map(EmployeeConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 校验员工是否为客户经理角色。
     * V1 简化实现：检查 position 字段是否为客户经理相关值。
     *
     * @param empId 员工工号
     * @return true 表示是客户经理
     */
    @Override
    public boolean isCustomerManager(String empId) {
        try {
            AddrbookEmployee entity = addressBookService.getEmployee(empId);
            // V1 简化：position 包含"客户经理"即认为是客户经理
            return entity.getPosition() != null && entity.getPosition().contains("客户经理");
        } catch (BizException e) {
            log.debug("[AddressBookFacade.isCustomerManager] 员工不存在, empId={}", empId);
            return false;
        }
    }
}
