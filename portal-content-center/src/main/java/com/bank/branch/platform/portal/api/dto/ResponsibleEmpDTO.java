package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

/**
 * 产品负责人信息 DTO（D.2 详情聚合用）
 *
 * <p>聚合通讯录中的负责人基本信息，手机号已脱敏处理。</p>
 */
@Data
public class ResponsibleEmpDTO {

    /** 员工工号 */
    private String empId;

    /** 员工姓名 */
    private String empName;

    /** 手机号（脱敏后） */
    private String mobile;

    /** 职位 */
    private String position;
}
