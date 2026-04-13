package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.EmployeeDTO;

import java.util.List;
import java.util.Optional;

/**
 * 通讯录对外接口。
 * 被 customer-marketing-center 等业务模块依赖。
 *
 * <p>所有方法均为只读查询，不提供写操作（写操作由 AddressBookController 处理）。
 * 返回的 EmployeeDTO 中 mobile 字段已脱敏。</p>
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface AddressBookApi {

    /**
     * 获取员工通讯录信息。
     *
     * @param empId 员工工号
     * @return 员工详情，不存在时返回 Optional.empty()
     */
    Optional<EmployeeDTO> getEmployee(String empId);

    /**
     * 批量获取员工信息。
     * 用于列表展示，避免 N+1 查询。
     *
     * @param empIds 员工工号列表（上限 200）
     * @return 员工DTO列表（不存在的项不包含在返回列表中）
     */
    List<EmployeeDTO> getEmployees(List<String> empIds);

    /**
     * 模糊搜索员工。
     * 用于前端员工选择器（如转派、指派、@提及等）。
     *
     * @param keyword 关键词（工号/姓名）
     * @param limit   返回数量上限（最大 50）
     * @return 员工列表
     */
    List<EmployeeDTO> searchEmployees(String keyword, int limit);

    /**
     * 按机构查询员工列表。
     *
     * @param orgCode 机构编码
     * @return 该机构下的全部员工列表（仅 status=ACTIVE）
     */
    List<EmployeeDTO> listEmployeesByOrg(String orgCode);

    /**
     * 校验员工是否为客户经理角色。
     * 用于线索转派校验、触达任务分配校验等。
     *
     * @param empId 员工工号
     * @return true 表示是客户经理
     */
    boolean isCustomerManager(String empId);
}
