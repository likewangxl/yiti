package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 人员标签修改成员请求（把关联行改为另一个工号）。 */
@Data
public class PersonTagMemberUpdateReqDTO {

    /** 新员工工号（PT_USER.USERNAME 口径）. */
    @NotBlank(message = "工号不能为空")
    private String username;
}
