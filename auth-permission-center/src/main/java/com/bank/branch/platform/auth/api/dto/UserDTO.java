package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 用户信息 DTO
 */
@Data
public class UserDTO {

    /** 员工ID */
    private String empId;

    /** 登录名 */
    private String username;

    /** 中文姓名 */
    private String displayName;

    /** 主机构编码 */
    private String mainOrgCode;

    /** 主机构名称 */
    private String mainOrgName;
}
