package com.bank.branch.platform.portal.controller.dto.addrbook;

import lombok.Data;

/**
 * 通讯录员工搜索结果 DTO（C.4 模糊搜索）
 *
 * <p>用于前端员工选择器，仅返回核心标识字段。</p>
 */
@Data
public class EmployeeSearchDTO {

    /** 员工工号 */
    private String empId;

    /** 员工姓名 */
    private String empName;

    /** 机构编码 */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 岗位 */
    private String position;
}
