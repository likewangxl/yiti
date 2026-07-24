package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 业务标签新增成员请求（一次可同时添加员工与机构两类成员）。
 * <p>两个列表至少一个非空（在 Service 层校验）：{@link #usernames} 员工工号、
 * {@link #orgDeptNos} 机构业务编号（EXT_ORG_INFO.DEPT_NO 口径）。</p>
 */
@Data
public class PersonTagMemberAddReqDTO {

    /** 员工工号列表（PT_USER.USERNAME 口径，可空）. */
    private List<String> usernames;

    /** 机构业务编号列表（EXT_ORG_INFO.DEPT_NO 口径，可空）. */
    private List<String> orgDeptNos;
}
