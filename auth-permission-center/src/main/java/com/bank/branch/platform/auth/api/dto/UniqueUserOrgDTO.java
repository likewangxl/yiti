package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 全局唯一机构归属的员工映射。
 * <p>仅表示在 EXT_USER_ORG 中恰好归属一个机构且该机构命中调用方授权机构集合的员工，
 * 不代表全部员工。</p>
 */
@Data
public class UniqueUserOrgDTO {

    /** 员工ID，对应 EXT_USER_ORG.USER_ID。 */
    private String empId;

    /** 唯一机构编码，对应 EXT_USER_ORG.ORG_CODE。 */
    private String orgCode;
}
