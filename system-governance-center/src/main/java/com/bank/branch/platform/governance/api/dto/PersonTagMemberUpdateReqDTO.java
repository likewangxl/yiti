package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 业务标签修改成员请求（把关联行改为另一个成员）。
 * <p>按被改成员行的维度取值：EMP 行填 {@link #username}（新工号）、ORG 行填 {@link #orgDeptNo}（新机构编号）。
 * 维度由行本身决定，不可跨维度改（Service 层按行维度校验）。</p>
 */
@Data
public class PersonTagMemberUpdateReqDTO {

    /** 新员工工号（PT_USER.USERNAME 口径）；改 EMP 行时填. */
    private String username;

    /** 新机构业务编号（EXT_ORG_INFO.DEPT_NO 口径）；改 ORG 行时填. */
    private String orgDeptNo;
}
