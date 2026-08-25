package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工自动补齐建议项 DTO（分配明细员工号输入框联想）.
 *
 * <p>按关键字模糊匹配 PT_USER 的工号 / 登录名 / 中文名，返回登录名 + 中文名供前端展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpSuggestRespDTO {

    /** 员工工号（PT_USER.USER_ID）. */
    private String empId;

    /** 登录名（PT_USER.USERNAME）. */
    private String username;

    /** 中文姓名（PT_USER.USERCHNNAME）. */
    private String empChnName;

    /** 员工主机构编码（EXT_ORG_INFO.ORG_CODE）. */
    private String mainOrgCode;

    /** 员工主机构名称（EXT_ORG_INFO.ORG_NAME）. */
    private String mainOrgName;
}
